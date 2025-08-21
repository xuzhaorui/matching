package io.github.xuzhaorui.matching.messaging;

import java.util.Map;

public record Message(byte[] payload, String key, Map<String, String> headers) {
}
