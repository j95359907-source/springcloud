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

    /*
     * ==================== 面试题（RabbitMQ 生产者） ====================
     * Q1：为什么业务写库后还要发消息？
     * A：通过异步事件解耦后续流程（通知、审计、统计），缩短主链路耗时。
     *
     * Q2：消息体里为什么带 timestamp？
     * A：便于消费端做时序分析、幂等判断和问题排查。
     * ================================================================
     */
}
