package io.github.xuzhaorui.matching.matchshard;

import io.github.xuzhaorui.matching.messaging.MessagingClient;
import io.github.xuzhaorui.matching.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MessagingSample {
    private static final Logger log = LoggerFactory.getLogger(MessagingSample.class);

    public MessagingSample(MessagingClient client, @Value("${topics.sample}") String topic) {
        client.subscribe(topic, this::handle);
    }

    private void handle(Message msg) {
        log.info("match-shard received {}", new String(msg.payload()));
    }
}
