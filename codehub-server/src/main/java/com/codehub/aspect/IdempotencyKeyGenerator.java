package com.codehub.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Builds stable fingerprints for JSON-style controller arguments. */
@Component
public class IdempotencyKeyGenerator {
    private final ObjectMapper mapper;

    public IdempotencyKeyGenerator(ObjectMapper objectMapper) {
        // Preserve application modules without changing its shared HTTP serializer.
        mapper = objectMapper.copy();
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public String generate(Long userId, Method method, Object[] args) {
        List<Object> businessArgs = new ArrayList<>();
        for (Object arg : args) {
            if (!(arg instanceof ServletRequest) && !(arg instanceof ServletResponse)) {
                // Nulls and argument positions are significant; do not concatenate strings.
                businessArgs.add(arg);
            }
        }
        try {
            byte[] json = mapper.writeValueAsBytes(businessArgs);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
            // Fully qualified signature keeps overloads and controllers separate.
            return "idempotent:v2:" + userId + ":" + method.toGenericString() + ":" + digest;
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            // Fail closed: a shared fallback key would incorrectly group unrelated requests.
            throw new IllegalStateException("无法生成请求防重指纹", e);
        }
    }
}
