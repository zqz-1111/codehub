package com.codehub.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置 — 文件索引异步构建
 *
 * 拓扑结构：
 * file.direct交换机 → file.index.queue（正常消费）
 *                   → file.index.dlx交换机 → file.index.dead.queue（死信兜底）
 */
@Configuration
public class RabbitMQConfig {

    // ========== 常量 ==========
    public static final String FILE_EXCHANGE = "file.direct";
    public static final String FILE_INDEX_QUEUE = "file.index.queue";
    public static final String FILE_INDEX_KEY = "file.index";

    public static final String FILE_DLX_EXCHANGE = "file.index.dlx";
    public static final String FILE_DEAD_QUEUE = "file.index.dead.queue";
    public static final String FILE_DEAD_KEY = "file.index.dead";

    // ========== 交换机 ==========
    @Bean
    public DirectExchange fileExchange() {
        return new DirectExchange(FILE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange fileDlxExchange() {
        return new DirectExchange(FILE_DLX_EXCHANGE, true, false);
    }

    // ========== 队列 ==========
    @Bean
    public Queue fileIndexQueue() {
        return QueueBuilder.durable(FILE_INDEX_QUEUE)
                .withArgument("x-dead-letter-exchange", FILE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FILE_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue fileDeadQueue() {
        return QueueBuilder.durable(FILE_DEAD_QUEUE).build();
    }

    // ========== 绑定 ==========
    @Bean
    public Binding fileIndexBinding() {
        return BindingBuilder.bind(fileIndexQueue()).to(fileExchange()).with(FILE_INDEX_KEY);
    }

    @Bean
    public Binding fileDeadBinding() {
        return BindingBuilder.bind(fileDeadQueue()).to(fileDlxExchange()).with(FILE_DEAD_KEY);
    }

    // ========== JSON消息转换器 ==========
    @Bean
    public MessageConverter jacksonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(true); // 自动生成messageId，用于消费幂等
        return converter;
    }
}
