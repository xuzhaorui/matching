package com.match;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.netty.channel.ChannelFuture;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件容器，封装 MatchPair 并提供复用场景
 */
@Setter
@Getter
class MatchPairEvent {
    private MatchPair pair;

    public void clear() { this.pair = null; }
}

/**
 * 高性能通知服务，基于 LMAX Disruptor
 */
@Slf4j
public class DisruptorNotificationService {
    private final Disruptor<MatchPairEvent> disruptor;
    private final RingBuffer<MatchPairEvent> ringBuffer;
    private final ExecutorService executor;

    @Resource
    private  ShardedChannelRegistry registry;

    @Getter
    private final AtomicInteger atomicInteger = new AtomicInteger();


    @Getter
    private final AtomicLong atomicLong = new AtomicLong(0);
    /**
     * 构造并启动 Disruptor
     * @param bufferSize RingBuffer 大小，必须为 2 的次幂
     * @param numConsumers 并行消费线程数
     */
    public DisruptorNotificationService(
                                       int bufferSize,
                                       int numConsumers) {
        // 创建线程池
        this.executor = Executors.newFixedThreadPool(
                numConsumers,
                r -> new Thread(r, "notif-disruptor-worker")
        );
        // 构建 Disruptor
        this.disruptor = new Disruptor<>(
                MatchPairEvent::new,
                bufferSize,
                executor,
                ProducerType.MULTI,
                new com.lmax.disruptor.BusySpinWaitStrategy()
        );

        // 注册事件处理器
        this.disruptor.handleEventsWith(new MatchPairEventHandler());

        // 全局异常处理
        this.disruptor.setDefaultExceptionHandler(new ExceptionHandler<MatchPairEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, MatchPairEvent event) {
                log.error("Error processing event seq {} pair {}", sequence, event.getPair(), ex);
            }
            @Override public void handleOnStartException(Throwable ex) {
                log.error("Disruptor start exception", ex);
            }
            @Override public void handleOnShutdownException(Throwable ex) {
                log.error("Disruptor shutdown exception", ex);
            }
        });

        // 启动
        this.ringBuffer = disruptor.start();

        Executors.newScheduledThreadPool(1).
                scheduleAtFixedRate(() -> {
                            System.out.printf(" 处理消息数 %d | 总消息数%d%n ",
                                    atomicLong.get(), atomicInteger.get()
                            );
                        },
                        100, 800, TimeUnit.MILLISECONDS);
    }

    /**
     * 发布 MatchPair 到 Disruptor
     */
    public void submit(MatchPair pair) {

        long seq = ringBuffer.next();
        try {
            MatchPairEvent evt = ringBuffer.get(seq);
            evt.setPair(pair);
        } finally {
            ringBuffer.publish(seq);
        }
    }

    /**
     * 优雅关闭 Disruptor 与线程池
     */
    public void shutdown() {
        try {
            disruptor.shutdown(5, TimeUnit.SECONDS);
        } catch (com.lmax.disruptor.TimeoutException e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();
    }

    /**
     * 事件处理器：从 MatchPairEvent 获取 MatchPair，推送通知
     */
    private class MatchPairEventHandler implements EventHandler<MatchPairEvent> {
        @Override
        public void onEvent(MatchPairEvent event, long sequence, boolean endOfBatch) {
            MatchPair p = event.getPair();
            try {
                if (p.tryNotify()) {
                    Mono<Void> sendA = registry.get(p.getChannelA())
                            .flatMap(ctxA -> {
                                if (ctxA == null) {
                                    log.warn("ChannelA not found for ID: {}", p.getChannelA());
                                    // 返回空Mono表示终止，或执行重定向逻辑
                                    return Mono.empty(); // 或 return redirectToRetryQueue(pair);
                                }

                                // 执行写操作并返回结果
                                return Mono.fromCallable(() -> {
                                    ChannelFuture fA = ctxA.writeAndFlush(p.getChannelA().getBytes(StandardCharsets.UTF_8));
                                    fA.syncUninterruptibly(); // The blocking operation is wrapped in fromCallable
                                    return fA;
                                    // Blocking operations are transferred to the elastic thread pool
                                }).subscribeOn(Schedulers.boundedElastic());
                            })
                            .doOnError(e -> log.error("Failed to process ChannelA", e))
                            .then(); // 忽略结果，仅关注完成信号

                    Mono<Void> sendB = registry.get(p.getChannelB())
                            .flatMap(ctxB -> {
                                if (ctxB == null) {
                                    log.warn("ChannelB not found for ID: {}", p.getChannelB());
                                    // Returning an empty Mono indicates termination, or execution of redirect logic
                                    return Mono.empty(); // 或 return redirectToRetryQueue(pair);
                                }

                                // 执行写操作并返回结果
                                return Mono.fromCallable(() -> {
                                    ChannelFuture fB = ctxB.writeAndFlush(p.getChannelB().getBytes(StandardCharsets.UTF_8));
                                    fB.syncUninterruptibly();
                                    return fB;
                                }).subscribeOn(Schedulers.boundedElastic());
                            })
                            .doOnError(e -> log.error("Failed to process ChannelB", e))
                            // Ignore the results and focus only on the completion signal
                            .then();

                    Mono.when(sendA, sendB)
                            .subscribe(null, err -> log.error("Notification error", err));

                    atomicLong.addAndGet(2);
                }
            } catch (Exception ex) {
                log.error("Notification error for pair {}", p, ex);
                p.markFailure();
            } finally {
                // Clean up the references, help GC
                event.clear();
            }
        }
    }
}
