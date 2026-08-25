package com.farm.smart.mqtt;

import com.farm.smart.service.MqttMessageService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * MQTT 消息回调处理器
 * 实现 Eclipse Paho 的 MqttCallback 接口
 * 处理: 消息到达 / 连接断开 / 消息投递完成
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
public class MqttMessageCallback implements MqttCallback {

    // 延迟注入避免循环依赖 (MqttMessageService 依赖 MqttClientManager -> MqttClient)
    private final MqttMessageService mqttMessageService;

    public MqttMessageCallback(@Lazy MqttMessageService mqttMessageService) {
        this.mqttMessageService = mqttMessageService;
    }

    /**
     * 消息到达回调 (核心处理入口)
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.info("收到 MQTT 消息: topic={}, payload={}", topic, payload);

            // 委托给 MqttMessageService 处理具体业务逻辑
            mqttMessageService.handleIncomingMessage(topic, payload);

        } catch (Exception e) {
            log.error("处理 MQTT 消息异常: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 连接断开回调
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT 连接断开, 将自动重连: {}", cause.getMessage(), cause);
    }

    /**
     * 消息投递完成回调
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT 消息投递完成: {}", token);
    }
}
