package com.example.school.clazz.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 消费者：监听班级事件
 */
@Component
public class ClassEventListener {

    @RabbitListener(queues = ClassEventMqConfig.QUEUE)
    public void onMessage(Map<String, Object> message) {
        // 教学演示：收到消息后打印，可替换为审计、通知、异步任务等逻辑
        System.out.println("[RabbitMQ] 收到班级事件: " + message);
    }
}
