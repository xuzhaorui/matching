package com.match;

import io.netty.channel.ChannelHandlerContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 对百万并发连接做水平分片的 ChannelRegistry：
 * 将一大份 Map 切成 N 份，每份都是 ConcurrentHashMap，
 * key=channelId, value=ChannelHandlerContext。通过 channelId.hashCode() 快速定位到对应分片。
 */
@Component
public class ShardedChannelRegistry {
    // 分片数，最好是 2 的幂：64、128、256，根据你机器内存/CPU 调节
    private static final int SHARD_COUNT = 128;
    private final ConcurrentHashMap<String, ChannelHandlerContext>[] shards;

    @SuppressWarnings("unchecked")
    public ShardedChannelRegistry() {
        shards = new ConcurrentHashMap[SHARD_COUNT];
        for (int i = 0; i < SHARD_COUNT; i++) {
            // 初始化每个分片的小表
            shards[i] = new ConcurrentHashMap<>(16, 0.75f, 4);
        }

    }

    private int shardIndex(String channelId) {
        // hash & (SHARD_COUNT - 1)，SHARD_COUNT 必须为 2 的幂
        return (channelId.hashCode() & 0x7FFF_FFFF) & (SHARD_COUNT - 1);
    }

    public void register(ChannelHandlerContext ch) {
        String id = ch.channel().id().asLongText();
        shards[shardIndex(id)].put(id, ch);
    }

    public void unregister(ChannelHandlerContext ch) {
        String id = ch.channel().id().asLongText();
        shards[shardIndex(id)].remove(id);
    }

    public Mono<ChannelHandlerContext> get(String channelId) {
        ChannelHandlerContext channelHandlerContext = shards[shardIndex(channelId)].get(channelId);
        if (channelHandlerContext == null) {
            return  Mono.empty();
        }else {
            return  Mono.just(channelHandlerContext);
        }
    }
}
