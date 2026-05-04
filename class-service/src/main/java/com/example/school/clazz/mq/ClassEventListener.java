package com.example.school.clazz.mq;

// RabbitMQ 监听注解，声明当前方法消费指定队列。
import org.springframework.amqp.rabbit.annotation.RabbitListener;
// Redis 字符串操作模板。
import org.springframework.data.redis.core.StringRedisTemplate;
// Spring 组件注解。
import org.springframework.stereotype.Component;

// Redis 幂等 key 的过期时间。
import java.time.Duration;
// 消息体类型。
import java.util.Map;

/**
 * RabbitMQ 消费者：监听班级事件队列。
 *
 * 增强点：
 * 1. 使用 eventId 做消费幂等；
 * 2. 使用 Redis 记录已处理消息；
 * 3. 消费异常抛出后，配合 RabbitMQ 重试和死信队列处理失败消息。
 */
@Component
public class ClassEventListener {

    // Redis 幂等 key 前缀，最终 key 形如 mq:class-event:processed:{eventId}。
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:class-event:processed:";

    // 用 Redis 存储消费记录，保证多个消费者实例之间也能共享幂等状态。
    private final StringRedisTemplate stringRedisTemplate;

    public ClassEventListener(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 监听班级事件队列。
     *
     * 如果方法正常结束，Spring AMQP 会确认消息；
     * 如果抛出异常，会触发重试；重试仍失败时，消息会按配置进入死信队列。
     */
    @RabbitListener(queues = ClassEventMqConfig.QUEUE)
    public void onMessage(Map<String, Object> message) {
        // 从消息体中读取 eventId。
        String eventId = String.valueOf(message.get("eventId"));

        // eventId 是幂等判断的基础，没有 eventId 的消息直接视为异常消息。
        if (eventId == null || eventId.isBlank() || "null".equals(eventId)) {
            throw new IllegalArgumentException("RabbitMQ message eventId is required");
        }

        // 构造 Redis 幂等 key。
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + eventId;

        // setIfAbsent 是原子操作：只有 key 不存在时才写入成功。
        // 返回 true 表示第一次消费；返回 false 表示重复消息。
        Boolean firstConsume = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", Duration.ofDays(1));

        // 如果不是第一次消费，就直接跳过，避免重复处理业务。
        if (Boolean.FALSE.equals(firstConsume)) {
            System.out.println("[RabbitMQ] 重复消息已跳过: " + message);
            return;
        }

        // 这里演示消费逻辑；生产环境可替换成审计、通知、统计等异步任务。
        System.out.println("[RabbitMQ] 收到班级事件: " + message);
    }

    /*
     * ==================== 面试题（RabbitMQ 消费幂等） ====================
     * Q1：为什么 MQ 消费者要做幂等？
     * A：消息可能因为网络抖动、确认失败、重试等原因被重复投递，消费者必须能识别重复消息。
     *
     * Q2：这里为什么用 Redis setIfAbsent？
     * A：setIfAbsent 是原子操作，适合用 eventId 做“第一次处理成功”的标记。
     *
     * Q3：如果消费失败怎么办？
     * A：抛出异常后可触发重试；重试仍失败时，消息可以进入死信队列等待排查或补偿。
     * ================================================================
     */
}
