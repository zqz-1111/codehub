package com.codehub.aspect;

import com.codehub.dto.CreateRepoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyKeyGeneratorTest {
    private final IdempotencyKeyGenerator generator = new IdempotencyKeyGenerator(new ObjectMapper());
    private final Method method = endpoint("create", Object.class);

    @Test
    void separatesPreviouslyCollidingRepositoryNames() {
        CreateRepoRequest first = new CreateRepoRequest();
        first.setName("Aa");
        CreateRepoRequest second = new CreateRepoRequest();
        second.setName("BB");
        assertEquals((first + "|").hashCode(), (second + "|").hashCode(), "Old algorithm collides");
        assertNotEquals(key(first), key(second));
        assertTrue(key(first).matches(".*:[a-f0-9]{64}$"));
    }

    @Test
    void mapInsertionOrderDoesNotChangeNestedFingerprint() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("name", "demo");
        left.put("options", new LinkedHashMap<>(Map.of("a", 1, "b", 2)));
        Map<String, Object> right = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("b", 2);
        nested.put("a", 1);
        right.put("options", nested);
        right.put("name", "demo");
        assertEquals(key(left), key(right));
    }

    @Test
    void separatesUsersAndOverloadedMethods() {
        assertNotEquals(generator.generate(1L, method, new Object[]{"x"}),
                generator.generate(2L, method, new Object[]{"x"}));
        assertNotEquals(generator.generate(1L, method, new Object[]{"x"}),
                generator.generate(1L, endpoint("create", String.class), new Object[]{"x"}));
    }

    @Test
    void preservesNullPositionsAndArgumentBoundariesButIgnoresTransport() {
        assertNotEquals(generator.generate(1L, method, new Object[]{null, "x"}),
                generator.generate(1L, method, new Object[]{"x", null}));
        assertNotEquals(generator.generate(1L, method, new Object[]{"a|b", "c"}),
                generator.generate(1L, method, new Object[]{"a", "b|c"}));
        assertEquals(key("x"), generator.generate(1L, method,
                new Object[]{"x", new MockHttpServletRequest(), new MockHttpServletResponse()}));
    }

    @Test
    void serializationFailureDoesNotUseSharedFallbackKey() {
        assertThrows(IllegalStateException.class, () -> key(new BrokenPayload()));
    }

    private String key(Object value) {
        return generator.generate(1L, method, new Object[]{value});
    }

    private static Method endpoint(String name, Class<?> parameter) {
        try {
            return Endpoints.class.getDeclaredMethod(name, parameter);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static class Endpoints {
        public void create(Object payload) { }
        public void create(String payload) { }
    }

    public static class BrokenPayload {
        public String getValue() { throw new IllegalArgumentException("Cannot serialize"); }
    }
}
