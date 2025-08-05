package com.match;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance matching engine：
 * 1. use Agrona ManyToOneConcurrentArrayQueue
 * 2. Parallelization Reactor Flux.parallel() Dispose of each barrel
 * 3. queue & Old Gen Threshold detection throttling
 * 4. Scan in place (drainTo) Reduce temporary objects
 * 5. scheduleWithFixedDelay dispatch
 */
public class EnhancedMatchEngine {
    private static final Logger log = LoggerFactory.getLogger(EnhancedMatchEngine.class);
    // Buckets and queues
    private final int bucketSize;
    private final int numBuckets;

    private final ManyToOneConcurrentArrayQueue<MatchEvent>[] buckets;

    // Pools with notifications
    private final MatchEvent[][] drainBuffers;
    private final DisruptorNotificationService disruptorNotificationService;

    // bucketStates
    private final int[] bucketStates;
    private static final VarHandle STATE_HANDLE;

    static {
        try {
            STATE_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Low concurrency processing
    private final AtomicInteger matchCounter = new AtomicInteger(0); // 窗口匹配计数器
    private static final  int LOW_CONCURRENCY_THRESHOLD = 10; // 低并发阈值

    private volatile int globalMatchState = 0;
    private static final VarHandle GLOBAL_HANDLE;
    static {
        try {
            GLOBAL_HANDLE = MethodHandles.lookup().findVarHandle(EnhancedMatchEngine.class, "globalMatchState", int.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 后压 & 内存监控
    private final AtomicLong pendingEvents = new AtomicLong(0);
    private final long maxPendingEvents;
    private final double heapUsageThreshold;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // 调度
    private final ScheduledExecutorService scheduler;

    @SuppressWarnings("unchecked")
    public EnhancedMatchEngine(MatchProperties props,
                               DisruptorNotificationService disruptorNotificationService,
                               ThreadPoolTaskExecutor matchThreadPool
    ) {
        this.bucketSize = props.getBucketSize();
        this.numBuckets = (props.getMaxScore() + bucketSize - 1) / bucketSize;
        this.bucketStates = new int[numBuckets]; // 初始全为 0，表示未占用

//        this.maxPendingEvents = props.getMaxPendingEvents();
        this.maxPendingEvents = 20000;
//        this.heapUsageThreshold = props.getHeapUsageThreshold(); // e.g. 0.8 for 80%
        this.heapUsageThreshold = 0.8; // e.g. 0.8 for 80%
        this.disruptorNotificationService = disruptorNotificationService;

        // 初始化 Agrona 队列与预分配 DrainBuffer
        this.buckets = new ManyToOneConcurrentArrayQueue[numBuckets];
        this.drainBuffers = new MatchEvent[numBuckets][];
        for (int i = 0; i < numBuckets; i++) {
            buckets[i] = new ManyToOneConcurrentArrayQueue<>(props.getExpectedLoadPerBucket());
            drainBuffers[i] = new MatchEvent[props.getExpectedLoadPerBucket()];
        }

        this.scheduler = Executors.newScheduledThreadPool(1);
        // scheduleWithFixedDelay
        scheduler.scheduleWithFixedDelay(this::runCycle,
//                props.getInitialDelayMs(), props.getIntervalMs(),
                500,200,
                TimeUnit.MILLISECONDS);

        // 调度器每 1s 重置一次：
        scheduler.scheduleAtFixedRate(() -> {
            matchCounter.set(0);
        }, 1, 1, TimeUnit.SECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            if (matchCounter.get() < LOW_CONCURRENCY_THRESHOLD &&
                    (int) GLOBAL_HANDLE.getVolatile(this) == 0 &&
                    GLOBAL_HANDLE.compareAndSet(this, 0, 1)) {

                try {
                    doGlobalMatch(); // 全桶匹配
                } finally {
                    GLOBAL_HANDLE.setRelease(this, 0);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);


    }

    /**
     * 提交事件，若超出阈值则拒绝
     */
    public boolean submitEvent(String username, int score, int matchRange, String channelId) {
        MatchEvent e = new MatchEvent();
        e.init(username, score, matchRange, channelId);
        e.tryAcquire();
        int bucket = Math.min(numBuckets - 1, (e.getScore() - 1) / bucketSize);
        boolean ok = buckets[bucket].offer(e);
        if (ok) {
            disruptorNotificationService.getAtomicInteger().incrementAndGet();
            pendingEvents.incrementAndGet();
            matchCounter.incrementAndGet();
        }
        return ok;
    }

    public void doGlobalMatch() {
        List<MatchEvent> allEvents = new ArrayList<>();

        // 锁定每个桶
        for (int i = 0; i < numBuckets; i++) {
            if ((int) STATE_HANDLE.getVolatile(bucketStates, i) == 0 &&
                    STATE_HANDLE.compareAndSet(bucketStates, i, 0, 1)) {
                try {
                    ManyToOneConcurrentArrayQueue<MatchEvent> q = buckets[i];
                    MatchEvent e;
                    while ((e = q.poll()) != null) {
                        allEvents.add(e);
                    }
                } finally {
                    STATE_HANDLE.setRelease(bucketStates, i, 0);
                }
            }
        }

        if (!allEvents.isEmpty()) {
            MatchEvent[] batch = allEvents.toArray(new MatchEvent[0]);
            List<MatchPair> pairs = new ArrayList<>();
            VectorizedMatchPipeline.processBatch(batch,  batch.length, pairs);
            emitPairs(pairs);

            for (MatchEvent e : batch) {
                if (e.getStateCode() == MatchEvent.PROCESSING) {
                    // 重新放入原桶
                    int bucketId = Math.min(numBuckets - 1, (e.getScore() - 1) / bucketSize);
                    buckets[bucketId].offer(e);
                }
            }
        }
    }


    /**
     * 扫描各桶并行处理一轮
     */
    private void runCycle() {

        Flux.range(0, numBuckets)
                .parallel()
                .runOn(Schedulers.parallel())
                .filter(this::bucketNonEmpty)
                .doOnNext(bucketId -> {
                    // CAS 原子获取占用权
                    if ((int) STATE_HANDLE.getVolatile(bucketStates, bucketId) == 0 &&
                            STATE_HANDLE.compareAndSet(bucketStates, bucketId, 0, 1)) {

                        try {
                            processBucket(bucketId);
                        } finally {
                            // 匹配结束，释放占用
                            STATE_HANDLE.setRelease(bucketStates, bucketId, 0);
                        }
                    }
                }).sequential().subscribe();
    }

    /**
     * 判断桶是否有待处理事件
     */
    private boolean bucketNonEmpty(int bucketId) {
        return !buckets[bucketId].isEmpty();
    }

    /**
     * 对单个桶执行批量匹配、跨桶逻辑
     */
    private void processBucket(int bucketId) {
        // 原地扫描批量拉取
        MatchEvent[] buf = drainBuffers[bucketId];
        int count = drainTo(buckets[bucketId], buf, buf.length);
        if (count <= 0) return;
        pendingEvents.addAndGet(-count);

        // 同桶匹配
        List<MatchPair> pairs = new ArrayList<>();
        VectorizedMatchPipeline.processBatch(buf, count, pairs);
        emitPairs(pairs);



        // 未匹配者返还本桶
        for (int i = 0; i < count; i++) {
            MatchEvent me = buf[i];
            if (me.getStateCode() == MatchEvent.PROCESSING) {
                buckets[bucketId].offer(me);
                pendingEvents.incrementAndGet();
            }
        }
    }

    /**
     * 推送 MatchPair
     */
    private void emitPairs(List<MatchPair> pairs) {
        for (MatchPair p : pairs) {
            disruptorNotificationService.submit(p);
        }
        pairs.clear();
    }

    /**
     * 检测 Heap 使用率
     */
    private boolean isHeapOverThreshold() {
        MemoryUsage usage = memoryBean.getHeapMemoryUsage();
        return usage.getUsed() >= (long)(usage.getMax() * heapUsageThreshold);
    }

    public void stop() {
        scheduler.shutdown();
    }

    // 扩展 Agrona 队列：添加 drainTo 方法
    // ManyToOneConcurrentArrayQueue 自身不含 drainTo，需自行实现：
    public static <T> int drainTo(ManyToOneConcurrentArrayQueue<T> q, T[] buf, int max) {
        int cnt = 0;
        T e;
        while (cnt < max && (e = q.poll()) != null) {
            buf[cnt++] = e;
        }
        return cnt;
    }

}


