package com.example.school.clazz.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：班级事件交换机/队列/绑定关系
 */
@Configuration
public class ClassEventMqConfig {
    public static final String EXCHANGE = "school.class.event.exchange";
    public static final String QUEUE = "school.class.event.queue";
    public static final String ROUTING_KEY = "school.class.event";

    @Bean
    public DirectExchange classEventExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue classEventQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding classEventBinding(Queue classEventQueue, DirectExchange classEventExchange) {
        return BindingBuilder.bind(classEventQueue).to(classEventExchange).with(ROUTING_KEY);
    }
}
