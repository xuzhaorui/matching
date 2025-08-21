package io.github.xuzhaorui.matching.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "messaging.type", havingValue = "kafka")
public class KafkaMessagingClient implements MessagingClient {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagingClient.class);
    private final KafkaTemplate<String, byte[]> template;
    private final ConsumerFactory<String, byte[]> consumerFactory;

    public KafkaMessagingClient(KafkaTemplate<String, byte[]> template) {
        this.template = template;
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, template.getProducerFactory().getConfigurationProperties()
                .get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        this.consumerFactory = new DefaultKafkaConsumerFactory<>(props);
    }

    @Override
    public void send(String topic, byte[] payload) {
        template.send(topic, payload);
    }

    @Override
    public void subscribe(String topic, Consumer<Message> consumer) {
        ContainerProperties props = new ContainerProperties(topic);
        props.setMessageListener((MessageListener<String, byte[]>) record -> consumer.accept(toMessage(record)));
        KafkaMessageListenerContainer<String, byte[]> container = new KafkaMessageListenerContainer<>(consumerFactory, props);
        container.start();
        log.info("Kafka consumer started for topic {}", topic);
    }

    private Message toMessage(ConsumerRecord<String, byte[]> record) {
        Map<String, String> headers = new HashMap<>();
        record.headers().forEach(h -> headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8)));
        return new Message(record.value(), record.key(), headers);
    }
}
