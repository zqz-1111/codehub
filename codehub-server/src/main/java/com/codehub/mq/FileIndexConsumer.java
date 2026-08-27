package com.codehub.mq;

import com.codehub.config.RabbitMQConfig;
import com.codehub.dto.FileIndexMessage;
import com.codehub.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 文件索引构建消费者
 *
 * 可靠性设计：
 * 1. 消费幂等 — Redis SETNX 记录已消费的messageId，防重复消费
 * 2. 失败重试 — 配置文件 retry.max-attempts=3，抛异常自动重试
 * 3. 死信兜底 — 重试耗尽后进入死信队列，人工介入或定时补偿
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileIndexConsumer {

    private final AiService aiService;
    private final StringRedisTemplate redisTemplate;

    private static final String CONSUMED_PREFIX = "mq:consumed:";

    @RabbitListener(queues = RabbitMQConfig.FILE_INDEX_QUEUE)
    public void handleFileIndex(FileIndexMessage message) {
        String messageId = message.getMessageId();

        // 1. 消费幂等检查：已消费过的消息直接跳过
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(CONSUMED_PREFIX + messageId, "1", 24, TimeUnit.HOURS);
        if (firstTime == null || !firstTime) {
            log.info("消息重复消费，跳过: messageId={}", messageId);
            return;
        }

        try {
            // 2. 执行索引构建（切分 + Embedding + 入库）
            aiService.buildIndex(
                    message.getRepoId(),
                    message.getFileId(),
                    message.getFilePath(),
                    message.getContent());

            log.info("索引构建完成: repoId={}, file={}", message.getRepoId(), message.getFilePath());

        } catch (Exception e) {
            // 3. 构建失败：删除幂等标记，让重试机制生效；重试耗尽进死信队列
            redisTemplate.delete(CONSUMED_PREFIX + messageId);
            log.error("索引构建失败，等待重试: messageId={}, error={}", messageId, e.getMessage());
            throw new RuntimeException("索引构建失败", e);
        }
    }

    /**
     * 死信队列消费者 — 兜底处理：记录日志，可接入告警
     */
    @RabbitListener(queues = RabbitMQConfig.FILE_DEAD_QUEUE)
    public void handleDeadLetter(FileIndexMessage message) {
        log.error("【死信】索引构建最终失败，需人工介入: repoId={}, file={}, messageId={}",
                message.getRepoId(), message.getFilePath(), message.getMessageId());
        // 生产环境这里可以：发告警通知 / 落库到补偿表 / 定时任务重新投递
    }
}
