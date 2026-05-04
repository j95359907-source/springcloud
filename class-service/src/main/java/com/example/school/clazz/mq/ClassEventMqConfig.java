package com.example.school.clazz.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /*
     * ==================== 面试题（RabbitMQ 配置） ====================
     * Q1：为什么使用 DirectExchange？
     * A：按 routingKey 精确路由，适合明确事件分类的业务场景。
     *
     * Q2：为什么要单独配置 MessageConverter 为 JSON？
     * A：避免 Java 原生序列化兼容/安全问题，跨语言可读性也更好。
     *
     * Q3：队列和交换机为什么设置 durable=true？
     * A：保证 RabbitMQ 重启后元数据不丢失。
     * ==============================================================
     */
}
