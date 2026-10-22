package com.codehub.aspect;

import com.codehub.annotation.Idempotent;
import com.codehub.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Redis commands are mocked here; this suite does not certify a real Redis/Spring deployment. */
class IdempotentAspectTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private IdempotentAspect aspect;
    private final Map<String, String> markers = new ConcurrentHashMap<>();
    private final Endpoints target = new Endpoints();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(call -> markers.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null);
        // Model the Redis compare-and-delete contract; actual Lua still needs a live Redis test.
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            List<String> keys = call.getArgument(1);
            String token = call.getArgument(2);
            return markers.remove(keys.get(0), token) ? 1L : 0L;
        });
        aspect = new IdempotentAspect(redis, new IdempotencyKeyGenerator(new ObjectMapper()));
    }

    @Test
    void successKeepsMarkerAndRejectsDuplicateWithBusinessCode429() throws Throwable {
        ProceedingJoinPoint first = request("create", 1L);
        when(first.proceed()).thenReturn("created");
        assertEquals("created", aspect.around(first));
        ProceedingJoinPoint duplicate = request("create", 1L);
        BusinessException error = assertThrows(BusinessException.class, () -> aspect.around(duplicate));
        assertEquals(429, error.getCode());
        verify(duplicate, never()).proceed();
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertEquals(1, markers.size());
    }

    @Test
    void businessFailureReleasesOwnMarkerAndAllowsRetry() throws Throwable {
        RuntimeException failure = new RuntimeException("business failed");
        ProceedingJoinPoint first = request("create", 1L);
        when(first.proceed()).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> aspect.around(first)));
        assertTrue(markers.isEmpty());
        ProceedingJoinPoint retry = request("create", 1L);
        when(retry.proceed()).thenReturn("retried");
        assertEquals("retried", aspect.around(retry));
    }

    @Test
    void expiredRequestCannotReleaseSuccessorMarker() throws Throwable {
        ProceedingJoinPoint first = request("create", 1L);
        RuntimeException failure = new RuntimeException("old request failed");
        AtomicReference<String> oldToken = new AtomicReference<>();
        when(first.proceed()).thenAnswer(call -> {
            String key = markers.keySet().iterator().next();
            oldToken.set(markers.get(key));
            markers.put(key, "successor-token"); // Simulate expiration followed by a new owner.
            throw failure;
        });
        assertSame(failure, assertThrows(RuntimeException.class, () -> aspect.around(first)));
        assertNotEquals("successor-token", oldToken.get());
        assertEquals(List.of("successor-token"), List.copyOf(markers.values()));
        verify(redis, never()).delete(anyString());
    }

    @Test
    void cleanupFailurePreservesOriginalBusinessException() throws Throwable {
        RuntimeException cleanup = new RuntimeException("Redis unavailable during cleanup");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(cleanup);
        RuntimeException original = new RuntimeException("business failed");
        ProceedingJoinPoint first = request("create", 1L);
        when(first.proceed()).thenThrow(original);
        assertSame(original, assertThrows(RuntimeException.class, () -> aspect.around(first)));
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
    }

    @Test
    void concurrentDuplicateCannotEnterBusinessWhileFirstIsRunning() throws Throwable {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ProceedingJoinPoint first = request("create", 1L);
        when(first.proceed()).thenAnswer(call -> {
            entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
            return "created";
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> running = executor.submit(() -> {
                try { return aspect.around(first); }
                catch (Throwable e) { throw new RuntimeException(e); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            ProceedingJoinPoint duplicate = request("create", 1L);
            assertEquals(429, assertThrows(BusinessException.class, () -> aspect.around(duplicate)).getCode());
            verify(duplicate, never()).proceed();
            finish.countDown();
            assertEquals("created", running.get(5, TimeUnit.SECONDS));
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unknownRedisOutcomeFailsClosedWithoutExecutingBusiness() throws Throwable {
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(null);
        ProceedingJoinPoint request = request("create", 1L);
        assertEquals(503, assertThrows(BusinessException.class, () -> aspect.around(request)).getCode());
        verify(request, never()).proceed();
    }

    @Test
    void invalidWindowDoesNotWriteRedisAndAnonymousRequestStillReachesAuthentication() throws Throwable {
        ProceedingJoinPoint invalid = request("invalid", 1L);
        assertThrows(IllegalStateException.class, () -> aspect.around(invalid));
        ProceedingJoinPoint anonymous = request("create", null);
        when(anonymous.proceed()).thenReturn("authentication handles it");
        assertEquals("authentication handles it", aspect.around(anonymous));
        verifyNoInteractions(values);
    }

    private ProceedingJoinPoint request(String methodName, Long userId) throws Exception {
        MockHttpServletRequest http = new MockHttpServletRequest();
        if (userId != null) http.setAttribute("userId", userId);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(Endpoints.class.getMethod(methodName, String.class, MockHttpServletRequest.class));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getTarget()).thenReturn(target);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{"same-payload", http});
        return point;
    }

    public static class Endpoints {
        @Idempotent public void create(String payload, MockHttpServletRequest request) { }
        @Idempotent(windowSeconds = 0) public void invalid(String payload, MockHttpServletRequest request) { }
    }
}
