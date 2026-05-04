package com.example.school.clazz.mq;

// RabbitMQ 绑定关系对象。
import org.springframework.amqp.core.Binding;
// RabbitMQ 绑定构建器。
import org.springframework.amqp.core.BindingBuilder;
// Direct 类型交换机。
import org.springframework.amqp.core.DirectExchange;
// RabbitMQ 队列对象。
import org.springframework.amqp.core.Queue;
// JSON 消息转换器。
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
// 消息转换器接口。
import org.springframework.amqp.support.converter.MessageConverter;
// Spring Bean 注解。
import org.springframework.context.annotation.Bean;
// Spring 配置类注解。
import org.springframework.context.annotation.Configuration;
// 按 Bean 名称精确注入，避免多个同类型 Bean 时 Spring 不知道选哪一个。
import org.springframework.beans.factory.annotation.Qualifier;

// Map 用来声明队列参数。
import java.util.Map;

/**
 * RabbitMQ 班级事件配置。
 *
 * 这里配置了正常消息链路和死信消息链路：
 * 1. 正常消息：EXCHANGE -> QUEUE；
 * 2. 失败消息：QUEUE -> DLX_EXCHANGE -> DLQ。
 */
@Configuration
public class ClassEventMqConfig {

    // 正常交换机名称。
    public static final String EXCHANGE = "school.class.event.exchange";
    // 正常队列名称。
    public static final String QUEUE = "school.class.event.queue";
    // 正常路由键。
    public static final String ROUTING_KEY = "school.class.event";

    // 死信交换机名称。
    public static final String DLX_EXCHANGE = "school.class.event.dlx.exchange";
    // 死信队列名称。
    public static final String DLQ = "school.class.event.dlq";
    // 死信路由键。
    public static final String DLX_ROUTING_KEY = "school.class.event.dead";

    // 创建正常交换机，durable=true 表示 RabbitMQ 重启后交换机仍然存在。
    @Bean
    public DirectExchange classEventExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    // 创建死信交换机，用来承接消费失败后的消息。
    @Bean
    public DirectExchange classEventDeadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    // 创建正常队列，并指定它的死信交换机和死信路由键。
    @Bean
    public Queue classEventQueue() {
        return new Queue(QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE,
                "x-dead-letter-routing-key", DLX_ROUTING_KEY
        ));
    }

    // 创建死信队列，失败消息最终会进入这里。
    @Bean
    public Queue classEventDeadLetterQueue() {
        return new Queue(DLQ, true);
    }

    // 把正常队列绑定到正常交换机。
    @Bean
    public Binding classEventBinding(@Qualifier("classEventQueue") Queue classEventQueue,
                                     @Qualifier("classEventExchange") DirectExchange classEventExchange) {
        return BindingBuilder.bind(classEventQueue).to(classEventExchange).with(ROUTING_KEY);
    }

    // 把死信队列绑定到死信交换机。
    @Bean
    public Binding classEventDeadLetterBinding(@Qualifier("classEventDeadLetterQueue") Queue classEventDeadLetterQueue,
                                               @Qualifier("classEventDeadLetterExchange") DirectExchange classEventDeadLetterExchange) {
        return BindingBuilder.bind(classEventDeadLetterQueue)
                .to(classEventDeadLetterExchange)
                .with(DLX_ROUTING_KEY);
    }

    // 使用 JSON 转换消息，避免 Java 原生序列化的安全和兼容问题。
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /*
     * ==================== 面试题（RabbitMQ 死信队列） ====================
     * Q1：什么是死信队列？
     * A：消费失败、消息过期、队列满或被拒绝的消息，可以被转发到死信队列。
     *
     * Q2：死信队列有什么价值？
     * A：失败消息不会直接丢失，可以后续告警、排查、人工补偿或定时重放。
     *
     * Q3：为什么这里用 JSON 消息？
     * A：JSON 可读、跨语言友好，也避开 Java 原生反序列化的安全限制。
     * ================================================================
     */
}
