package io.github.xuzhaorui.matching.messaging;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "messaging.type", havingValue = "rocketmq")
public class RocketMqMessagingClient implements MessagingClient {

    private static final Logger log = LoggerFactory.getLogger(RocketMqMessagingClient.class);
    private final RocketMQTemplate template;

    public RocketMqMessagingClient(RocketMQTemplate template) {
        this.template = template;
    }

    @Override
    public void send(String topic, byte[] payload) {
        template.convertAndSend(topic, payload);
    }

    @Override
    public void subscribe(String topic, Consumer<Message> consumer) {
        DefaultMQPushConsumer c = new DefaultMQPushConsumer("demo-" + UUID.randomUUID());
        c.setNamesrvAddr(template.getProducer().getNamesrvAddr());
        try {
            c.subscribe(topic, "*");
            c.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
                msgs.forEach(m -> {
                    Map<String, String> headers = new HashMap<>(m.getProperties());
                    consumer.accept(new Message(m.getBody(), m.getKeys(), headers));
                });
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            c.start();
            log.info("RocketMQ consumer started for topic {}", topic);
        } catch (MQClientException e) {
            throw new RuntimeException(e);
        }
    }
}
