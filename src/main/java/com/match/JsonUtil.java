package com.match;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        //忽略属性未对齐的报错
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static <T> T jsonToBo(Class<T> cls, String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return MAPPER.readValue(json, cls);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
