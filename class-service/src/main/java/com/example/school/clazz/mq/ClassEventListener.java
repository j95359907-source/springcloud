package com.example.school.clazz.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 消费者：监听班级事件队列。
 */
@Component
public class ClassEventListener {

    @RabbitListener(queues = ClassEventMqConfig.QUEUE)
    public void onMessage(Map<String, Object> message) {
        // 演示消费逻辑：生产环境可替换为审计、通知、异步计算等任务。
        System.out.println("[RabbitMQ] 收到班级事件: " + message);
    }

    /*
     * ==================== 面试题（RabbitMQ 消费者） ====================
     * Q1：@RabbitListener 的作用是什么？
     * A：声明式监听指定队列，框架自动拉取消息并回调方法。
     *
     * Q2：消费失败怎么处理？
     * A：可配置重试、死信队列(DLX)、告警；当前项目以演示消费为主。
     * ================================================================
     */
}
