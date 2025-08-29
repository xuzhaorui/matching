package com.match;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisruptorNotificationServiceTest {

    static class TestRegistry extends ShardedChannelRegistry {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Mono<ChannelHandlerContext> get(String channelId) {
            calls.incrementAndGet();
            return Mono.empty();
        }
    }

    @Test
    void registryGetCalledForBothChannels() throws Exception {
        DisruptorNotificationService service = new DisruptorNotificationService(16, 1);
        TestRegistry registry = new TestRegistry();
        Field field = DisruptorNotificationService.class.getDeclaredField("registry");
        field.setAccessible(true);
        field.set(service, registry);

        MatchEvent ma = new MatchEvent();
        ma.init("A", 1, 1, "channelA");
        MatchEvent mb = new MatchEvent();
        mb.init("B", 1, 1, "channelB");
        MatchPair pair = new MatchPair();
        pair.init(ma, mb);

        service.submit(pair);

        Thread.sleep(200); // wait for async processing

        assertEquals(2, registry.calls.get());
        service.shutdown();
    }
}
