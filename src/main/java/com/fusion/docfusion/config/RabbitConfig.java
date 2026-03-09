package com.fusion.docfusion.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String FILL_TASK_QUEUE = "doc-fusion.fill-task.queue";
    public static final String FILL_TASK_EXCHANGE = "doc-fusion.fill-task.exchange";
    public static final String FILL_TASK_ROUTING_KEY = "doc-fusion.fill-task.key";

    @Bean
    public Queue fillTaskQueue() {
        return new Queue(FILL_TASK_QUEUE, true);
    }

    @Bean
    public DirectExchange fillTaskExchange() {
        return new DirectExchange(FILL_TASK_EXCHANGE, true, false);
    }

    @Bean
    public Binding fillTaskBinding(Queue fillTaskQueue, DirectExchange fillTaskExchange) {
        return BindingBuilder.bind(fillTaskQueue).to(fillTaskExchange).with(FILL_TASK_ROUTING_KEY);
    }
}

