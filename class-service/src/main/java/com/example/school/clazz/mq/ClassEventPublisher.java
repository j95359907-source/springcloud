package com.example.school.clazz.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 生产者：发送班级变更事件
 */
@Component
public class ClassEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public ClassEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String action, Long classId) {
        Map<String, Object> body = Map.of(
                "action", action,
                "classId", classId,
                "timestamp", System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(
                ClassEventMqConfig.EXCHANGE,
                ClassEventMqConfig.ROUTING_KEY,
                body
        );
    }
}
