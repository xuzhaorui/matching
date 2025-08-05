package com.match;

import lombok.Data;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

/**
 * 经过重构的 MatchEvent
 */
@Data
public class MatchEvent {
    // 合并状态到 32-bit int: 低16位为状态码，高16位为匹配标志
    private volatile int state;
    private long pad1, pad2, pad3, pad4, pad5, pad6, pad7;
    private int pad8;


    private static final VarHandle STATE_HANDLE;
    static {
        try {
            STATE_HANDLE = MethodHandles.lookup()
                    .in(MatchEvent.class)
                    .findVarHandle(MatchEvent.class, "state", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    Supplier<Boolean> consumer = this::tryAcquire;
    // 状态定义
    public static final int STATUS_MASK    = 0x0000_FFFF;
    public static final int INITIAL        = 0;
    public static final int PROCESSING     = 1;
    public static final int MATCHED        = 2;
    public static final int FINALIZED      = 3;
    public static final int MATCHED_FLAG   = 0x0001_0000;

    private int score;
    private int matchRange;
    private String username;
    private String channelId;




    public MatchEvent() {}

    public void init(String username, int score, int matchRange, String channelId) {
        this.username     = username;
        this.score        = score;
        this.matchRange   = matchRange;
        this.channelId    = channelId;
    }

    public boolean tryAcquire() {
        int prev;
        do {
            prev = (int) STATE_HANDLE.getAcquire(this); // acquire 语义读
            if ((prev & STATUS_MASK) != INITIAL) return false;
        } while (!STATE_HANDLE.compareAndSet(this, prev, PROCESSING)); // CAS 操作
        return true;
    }

    public boolean markMatched() {
        int prev, next;
        do {
            prev = (int) STATE_HANDLE.getAcquire(this);
            if ((prev & STATUS_MASK) != PROCESSING) return false;
            next = MATCHED | MATCHED_FLAG; // 合并状态和标志位
        } while (!STATE_HANDLE.compareAndSet(this, prev, next));
        return true;
    }


    // 原子化reset操作
    public MatchEvent reset() {
        STATE_HANDLE.set(this, INITIAL);
        this.state = INITIAL;
        this.score = 0;
        this.matchRange = 0;
        this.username = null;
        this.channelId = null;
        return this;
    }

    /** 取低16位状态码 */
    public int getStateCode() {
        return ((int) STATE_HANDLE.getAcquire(this) & STATUS_MASK);
    }

    // ========== 状态检查方法 ==========
    public boolean isMatched() {
        int s = (int) STATE_HANDLE.getAcquire(this);
        return (s & STATUS_MASK) == MATCHED && (s & MATCHED_FLAG) != 0;
    }

    public boolean isProcessing() {
        return ((int) STATE_HANDLE.getAcquire(this) & STATUS_MASK) == PROCESSING;
    }
}
