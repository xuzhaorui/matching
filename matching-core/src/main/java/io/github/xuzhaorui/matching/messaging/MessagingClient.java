package io.github.xuzhaorui.matching.messaging;

import java.util.function.Consumer;

public interface MessagingClient {
    void send(String topic, byte[] payload);
    void subscribe(String topic, Consumer<Message> consumer);
}
