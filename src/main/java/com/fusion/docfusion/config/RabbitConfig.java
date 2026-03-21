package com.fusion.docfusion.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String FILL_TASK_QUEUE = "doc-fusion.fill-task.queue";
    public static final String FILL_TASK_EXCHANGE = "doc-fusion.fill-task.exchange";
    public static final String FILL_TASK_ROUTING_KEY = "doc-fusion.fill-task.key";

    /**
     * 重试与死信：
     * - 主队列消费失败后 nack(requeue=false) -> 进入 DLX(retry routing key) -> 重试队列
     * - 重试队列 TTL 到期 -> DLX(main routing key) -> 回到主队列再次消费
     * - 超过最大重试次数的消息由消费者主动投递到 DLQ（parking lot），避免无限循环
     */
    public static final String FILL_TASK_DLX_EXCHANGE = "doc-fusion.fill-task.dlx";
    public static final String FILL_TASK_RETRY_QUEUE = "doc-fusion.fill-task.retry.queue";
    public static final String FILL_TASK_RETRY_ROUTING_KEY = "doc-fusion.fill-task.retry";
    public static final String FILL_TASK_DLQ = "doc-fusion.fill-task.dlq";
    public static final String FILL_TASK_DLQ_ROUTING_KEY = "doc-fusion.fill-task.dead";

    /**
     * 重试间隔（毫秒）。可按需调大：10s/30s/60s…
     */
    public static final int FILL_TASK_RETRY_TTL_MS = 10_000;

    @Bean
    public Queue fillTaskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", FILL_TASK_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", FILL_TASK_RETRY_ROUTING_KEY);
        return new Queue(FILL_TASK_QUEUE, true, false, false, args);
    }

    @Bean
    public DirectExchange fillTaskExchange() {
        return new DirectExchange(FILL_TASK_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange fillTaskDlxExchange() {
        return new DirectExchange(FILL_TASK_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue fillTaskRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", FILL_TASK_RETRY_TTL_MS);
        // TTL 到期后回到主交换机，再次进入主队列
        args.put("x-dead-letter-exchange", FILL_TASK_EXCHANGE);
        args.put("x-dead-letter-routing-key", FILL_TASK_ROUTING_KEY);
        return new Queue(FILL_TASK_RETRY_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue fillTaskDlq() {
        return new Queue(FILL_TASK_DLQ, true);
    }

    @Bean
    public Binding fillTaskBinding(Queue fillTaskQueue, DirectExchange fillTaskExchange) {
        return BindingBuilder.bind(fillTaskQueue).to(fillTaskExchange).with(FILL_TASK_ROUTING_KEY);
    }

    @Bean
    public Binding fillTaskRetryBinding(Queue fillTaskRetryQueue, DirectExchange fillTaskDlxExchange) {
        return BindingBuilder.bind(fillTaskRetryQueue).to(fillTaskDlxExchange).with(FILL_TASK_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding fillTaskDlqBinding(Queue fillTaskDlq, DirectExchange fillTaskDlxExchange) {
        return BindingBuilder.bind(fillTaskDlq).to(fillTaskDlxExchange).with(FILL_TASK_DLQ_ROUTING_KEY);
    }
}

