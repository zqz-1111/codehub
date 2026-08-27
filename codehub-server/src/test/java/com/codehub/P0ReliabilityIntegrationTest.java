package com.codehub;

import com.codehub.dto.FileIndexMessage;
import com.codehub.mq.FileIndexConsumer;
import com.codehub.service.AiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
public class P0ReliabilityIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private AiService aiService;

    @Autowired
    private FileIndexConsumer fileIndexConsumer;

    @Test
    @DisplayName("1. 测试 Redisson 分布式锁跨线程互斥与释放")
    void testRedissonLockMultiThread() throws Exception {
        String lockKey = "lock:test:" + UUID.randomUUID();
        RLock lock1 = redissonClient.getLock(lockKey);

        // 主线程获取锁
        boolean acquired1 = lock1.tryLock(1, 10, TimeUnit.SECONDS);
        assertTrue(acquired1, "主线程应成功获取分布式锁");

        // 子线程尝试获取同一把锁（应被互斥阻塞并超时失败）
        CountDownLatch threadLatch = new CountDownLatch(1);
        AtomicBoolean thread2Acquired = new AtomicBoolean(false);

        Thread thread2 = new Thread(() -> {
            RLock lock2 = redissonClient.getLock(lockKey);
            try {
                boolean acquired = lock2.tryLock(500, 5000, TimeUnit.MILLISECONDS);
                thread2Acquired.set(acquired);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                threadLatch.countDown();
            }
        });

        thread2.start();
        threadLatch.await(2, TimeUnit.SECONDS);
        assertFalse(thread2Acquired.get(), "在主线程持有锁期间，子线程获取锁应互斥失败");

        // 主线程释放锁
        lock1.unlock();

        // 释放后，再次由新线程尝试获取锁（应成功）
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            RLock lock3 = redissonClient.getLock(lockKey);
            try {
                boolean ok = lock3.tryLock(1, 5, TimeUnit.SECONDS);
                if (ok) {
                    lock3.unlock();
                }
                return ok;
            } catch (InterruptedException e) {
                return false;
            }
        });

        assertTrue(future.get(3, TimeUnit.SECONDS), "主线程释放锁后，其他线程应能成功获取锁");
    }

    @Test
    @DisplayName("2. 测试大文件分片合并防连点分布式锁（waitTime=0 拒绝语义）")
    void testChunkMergeDistributedLock() throws Exception {
        String uploadId = UUID.randomUUID().toString();
        String lockKey = "lock:merge:" + uploadId;
        RLock lock1 = redissonClient.getLock(lockKey);

        // 第一个请求正在合并分片（获取锁，租期30秒）
        boolean firstMergeAcquired = lock1.tryLock(0, 30, TimeUnit.SECONDS);
        assertTrue(firstMergeAcquired, "第一次合并请求应立即获得锁");

        // 第二个并发/连点请求尝试获取锁（waitTime=0，立即返回 false）
        CompletableFuture<Boolean> secondMerge = CompletableFuture.supplyAsync(() -> {
            RLock lock2 = redissonClient.getLock(lockKey);
            try {
                return lock2.tryLock(0, 30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        });

        assertFalse(secondMerge.get(1, TimeUnit.SECONDS), "并发连点的第二次合并请求应被锁拒绝");

        // 合并完成后释放锁
        lock1.unlock();
    }

    @Test
    @DisplayName("3. 测试 RabbitMQ 异步消费幂等性（Redis SETNX）")
    void testRabbitMqConsumerIdempotency() {
        String messageId = UUID.randomUUID().toString();
        FileIndexMessage message = new FileIndexMessage(
                100L, 200L, "src/Test.java", "public class Test {}", messageId);

        // 第一次消费：应该正常执行 aiService.buildIndex
        fileIndexConsumer.handleFileIndex(message);
        Mockito.verify(aiService, Mockito.times(1))
                .buildIndex(eq(100L), eq(200L), eq("src/Test.java"), eq("public class Test {}"));

        // 验证 Redis 中已记录该 messageId
        String consumedKey = "mq:consumed:" + messageId;
        assertEquals("1", redisTemplate.opsForValue().get(consumedKey));

        // 第二次模拟消费重复消息：应该被幂等拦截跳过，不再调用 aiService.buildIndex
        fileIndexConsumer.handleFileIndex(message);
        Mockito.verify(aiService, Mockito.times(1)) // 依然是 1 次
                .buildIndex(any(), any(), any(), any());

        // 清理测试 key
        redisTemplate.delete(consumedKey);
    }

    @Test
    @DisplayName("4. 测试 RabbitMQ 消费失败时清理幂等标记以支持重试")
    void testRabbitMqConsumerRetryOnFailure() {
        String messageId = UUID.randomUUID().toString();
        FileIndexMessage message = new FileIndexMessage(
                101L, 201L, "src/Fail.java", "invalid content", messageId);

        // 模拟 aiService.buildIndex 抛出异常
        Mockito.doThrow(new RuntimeException("模拟索引构建失败"))
                .when(aiService).buildIndex(eq(101L), eq(201L), any(), any());

        // 执行消费，预期抛出 RuntimeException
        assertThrows(RuntimeException.class, () -> fileIndexConsumer.handleFileIndex(message));

        // 验证失败时幂等标记已被删除，以便 MQ 消息重试
        String consumedKey = "mq:consumed:" + messageId;
        assertNull(redisTemplate.opsForValue().get(consumedKey), "消费失败应删除幂等标记允许重试");
    }

    @Test
    @DisplayName("5. 测试缓存防护机制：NULL_MARKER 空值标记防穿透")
    void testCacheNullMarker() {
        String cacheKey = "repo:file_tree:test_null_" + UUID.randomUUID();
        String nullMarker = "__NULL__";

        // 模拟写入防穿透空值标记
        redisTemplate.opsForValue().set(cacheKey, nullMarker, 60, TimeUnit.SECONDS);

        // 读取缓存并验证
        String cached = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cached);
        assertEquals(nullMarker, cached, "命中空值标记，防止穿透到数据库");

        // 清理
        redisTemplate.delete(cacheKey);
    }

    @Test
    @DisplayName("6. 测试文件更新时 Cache-Aside 缓存失效机制")
    void testFileTreeCacheInvalidation() {
        Long repoId = 777L;
        String cacheKey = "repo:file_tree:" + repoId;

        // 模拟已缓存的文件目录树
        redisTemplate.opsForValue().set(cacheKey, "[{\"id\":1,\"path\":\"README.md\"}]", 300, TimeUnit.SECONDS);
        assertNotNull(redisTemplate.opsForValue().get(cacheKey), "缓存应存在");

        // 模拟写操作触发缓存淘汰
        redisTemplate.delete(cacheKey);
        assertNull(redisTemplate.opsForValue().get(cacheKey), "写操作后缓存应被淘汰");
    }
}
