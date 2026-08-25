package com.farm.smart.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

/**
 * MQTT 客户端管理器
 * 封装 MQTT 消息发布操作 (订阅由 MqttConfig 初始化时完成)
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
public class MqttClientManager {

    private final MqttClient mqttClient;

    public MqttClientManager(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    /**
     * 发布消息到指定 topic
     *
     * @param topic   目标 topic
     * @param payload 消息内容 (JSON 字符串)
     * @param qos     服务质量等级 (0/1/2)
     * @param retained 是否保留消息
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(qos);
            message.setRetained(retained);
            mqttClient.publish(topic, message);
            log.info("MQTT 发布消息: topic={}, payload={}", topic, payload);
        } catch (Exception e) {
            log.error("MQTT 发布消息失败: topic={}, error={}", topic, e.getMessage(), e);
            throw new RuntimeException("MQTT 消息发布失败", e);
        }
    }

    /**
     * 发布消息 (默认 QoS=1, 不保留)
     */
    public void publish(String topic, String payload) {
        publish(topic, payload, 1, false);
    }

    /**
     * 检查 MQTT 客户端是否已连接
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}
