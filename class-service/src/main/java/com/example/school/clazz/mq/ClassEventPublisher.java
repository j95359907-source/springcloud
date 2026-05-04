package com.example.school.clazz.mq;

// RabbitMQ 发送消息的核心模板类。
import org.springframework.amqp.rabbit.core.RabbitTemplate;
// Spring 组件注解。
import org.springframework.stereotype.Component;

// Map 用来构造消息体。
import java.util.Map;
// UUID 用来生成消息唯一 ID。
import java.util.UUID;

/**
 * RabbitMQ 生产者：发布班级变更事件。
 *
 * 业务场景：
 * 班级新增、修改、删除之后，主流程先完成数据库操作，
 * 然后通过 MQ 发一个事件，后续审计、通知、统计等任务可以异步处理。
 */
@Component
public class ClassEventPublisher {

    // RabbitTemplate 封装了发送消息到 RabbitMQ 的细节。
    private final RabbitTemplate rabbitTemplate;

    public ClassEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发布班级事件。
     *
     * @param action  事件动作，例如 CREATE、UPDATE、DELETE
     * @param classId 班级 ID
     */
    public void publish(String action, Long classId) {
        // 每条消息生成唯一 eventId，消费者用它做幂等判断。
        String eventId = UUID.randomUUID().toString().replace("-", "");

        // 构造消息体，使用 Map 是为了演示清晰，生产也可以定义专门的 DTO。
        Map<String, Object> body = Map.of(
                "eventId", eventId,
                "action", action,
                "classId", classId,
                "timestamp", System.currentTimeMillis()
        );

        // 发送消息到指定交换机，并携带 routingKey。
        rabbitTemplate.convertAndSend(
                ClassEventMqConfig.EXCHANGE,
                ClassEventMqConfig.ROUTING_KEY,
                body
        );
    }

    /*
     * ==================== 面试题（RabbitMQ 生产者） ====================
     * Q1：为什么消息里要有 eventId？
     * A：消费者可以用 eventId 做幂等判断，避免同一条消息重复消费造成重复处理。
     *
     * Q2：为什么写库后再发消息？
     * A：主链路先保证业务数据落库，再用消息驱动后续异步流程，例如审计、通知、统计。
     *
     * Q3：生产环境如何进一步保证消息发送可靠？
     * A：可以使用 publisher confirm、事务消息、Outbox 表等方案。
     * ================================================================
     */
}
