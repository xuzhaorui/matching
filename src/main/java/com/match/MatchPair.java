package com.match;


import lombok.Data;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * 匹配成功对子对象，存储一对 MatchEvent 的关键信息，并维护通知状态机
 */
@Data
public class MatchPair {
    // 状态机字段：0=未通知，1=通知中，2=已成功，3=失败
    private volatile long state;
    private long padA, padB, padC, padD, padE, padF, padG;
    private volatile long createdAt;
    private long pad1, pad2, pad3, pad4, pad5, pad6, pad7;



    private static final VarHandle STATE_HANDLE;
    static {
        try {
            STATE_HANDLE = MethodHandles.lookup()
                    .in(MatchPair.class)
                    .findVarHandle(MatchPair.class, "state", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // 状态码掩码
    public static final int STATE_MASK       = 0x0000_FFFF;
    public static final int UNNOTIFIED       = 0;
    public static final int NOTIFYING        = 1;
    public static final int SUCCESS          = 2;
    public static final int FAILURE          = 3;

    // 成员一信息
    private volatile String userA;
    private volatile String channelA;
    private volatile int    scoreA;
    private volatile int    rangeA;

    // 成员二信息
    private volatile String userB;
    private volatile String channelB;
    private volatile int    scoreB;
    private volatile int    rangeB;


    // 手动填充避免伪共享（假设缓存行64字节）


    public MatchPair reset() {
        STATE_HANDLE.set(this, UNNOTIFIED); // 使用 VarHandle 写入
        this.state = UNNOTIFIED;
        this.userA = null;
        this.userB = null;
        this.channelA = null;
        this.channelB = null;
        this.scoreA = 0;
        this.scoreB = 0;
        this.rangeA = 0;
        this.rangeB = 0;
        this.createdAt = 0;
        return this;
    }

    public MatchPair(){
        this.state = UNNOTIFIED;

    }

    public void init(MatchEvent ma, MatchEvent mb) {
        this.userA = ma.getUsername();
        this.userB = mb.getUsername();
        this.channelA = ma.getChannelId();
        this.channelB = mb.getChannelId();
        this.scoreA = ma.getScore();
        this.scoreB = mb.getScore();
        this.rangeA = ma.getMatchRange();
        this.rangeB = mb.getMatchRange();
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 尝试将状态从 UNNOTIFIED 转到 NOTIFYING
     * @return true 如果成功，否则 false
     */
    public boolean tryNotify() {
        long prev;
        do {
            prev = (long) STATE_HANDLE.getAcquire(this); // 读操作用 acquire 语义
            if ((prev & STATE_MASK) != UNNOTIFIED) return false;
        } while (!STATE_HANDLE.compareAndSet(this, prev, NOTIFYING)); // CAS 操作
        return true;
    }

    /**
     * 将状态从 NOTIFYING 标记为 SUCCESS
     * @return true 如果成功，否则 false
     */
    public boolean markSuccess() {
        long prev;
        do {
            prev = (long) STATE_HANDLE.getAcquire(this);
            if ((prev & STATE_MASK) != NOTIFYING) return false;
        } while (!STATE_HANDLE.compareAndSet(this, prev, SUCCESS));
        return true;
    }

    /**
     * 将状态从 NOTIFYING 标记为 FAILURE
     * @return true 如果成功，否则 false
     */
    public boolean markFailure() {
        long prev;
        do {
            prev = (long) STATE_HANDLE.getAcquire(this);
            if ((prev & STATE_MASK) != NOTIFYING) return false;
        } while (!STATE_HANDLE.compareAndSet(this, prev, FAILURE));
        return true;
    }

    // ========== 状态检查方法 ==========


    /**
     * 当前是否已通知中
     */
    public boolean isNotifying() {
        return ((long) STATE_HANDLE.getAcquire(this) & STATE_MASK) == NOTIFYING;
//        return (state & STATE_MASK) == NOTIFYING;
    }

    /**
     * 是否已成功
     */
    public boolean isSuccess() {
//        return (state & STATE_MASK) == SUCCESS;
        return ((long) STATE_HANDLE.getAcquire(this) & STATE_MASK) == SUCCESS;
    }

    /**
     * 是否已失败
     */
    public boolean isFailure() {
//        return (state & STATE_MASK) == FAILURE;
        return ((long) STATE_HANDLE.getAcquire(this) & FAILURE) == SUCCESS;
    }

    /**
     * 是否终态（SUCCESS 或 FAILURE）
     */
    public boolean isFinalized() {
//        long s = state & STATE_MASK;
        long l = (long) STATE_HANDLE.getAcquire(this) & FAILURE;
        return l == SUCCESS || l == FAILURE;
    }


    public boolean isTimeout() {
        return System.currentTimeMillis() - createdAt > 500;
    }

}
