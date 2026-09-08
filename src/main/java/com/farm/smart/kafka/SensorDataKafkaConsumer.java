package com.farm.smart.kafka;

import com.farm.smart.model.dto.SensorDataDTO;
import com.farm.smart.service.SensorDataService;
import com.farm.smart.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 传感器数据 Kafka 消费者
 * <p>
 * 批量消费 Kafka 中的传感器消息，异步处理数据写入 InfluxDB + Redis + WebSocket 推送 + 阈值告警检查。
 * 将 MQTT 消息处理与数据库写入解耦，即使数据库短暂不可用也不丢消息（Kafka 持久化 + 手动 offset 提交）。
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataKafkaConsumer {

    private final SensorDataService sensorDataService;
    private final MeterRegistry meterRegistry;

    /**
     * 批量消费传感器数据
     */
    @KafkaListener(topics = "farm-sensor-data", groupId = "smart-farm-group")
    public void handleSensorData(List<SensorDataDTO> messages, Acknowledgment ack) {
        log.debug("批量消费 {} 条传感器消息", messages.size());

        Counter counter = Counter.builder("farm.kafka.consumed.total")
                .tag("topic", "farm-sensor-data")
                .register(meterRegistry);

        try {
            for (SensorDataDTO message : messages) {
                // Kafka 消费无 HTTP 请求上下文，需忽略租户隔离
                TenantContext.setIgnore(true);
                try {
                    // 异步处理：写入 InfluxDB + Redis 缓存 + WebSocket 推送 + 告警检查
                    sensorDataService.processSensorData(message);
                    counter.increment();
                } finally {
                    TenantContext.clear();
                }
            }
            // 手动提交 offset
            ack.acknowledge();
        } catch (Exception e) {
            log.error("批量消费传感器数据失败, 消息数={}", messages.size(), e);
            // 不提交 offset，Kafka 会重试
        }
    }
}
