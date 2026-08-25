package com.farm.smart.config;

import com.farm.smart.mqtt.MqttMessageCallback;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 配置类
 * 连接 EMQX Broker, 订阅传感器数据上报 topic 和设备控制回复 topic
 *
 * @author Smart Farm Team
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {

    /** MQTT Broker 地址 */
    private String brokerUrl;

    /** 客户端ID (需唯一) */
    private String clientId;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 心跳间隔 (秒) */
    private int keepAliveInterval;

    /** 连接超时 (秒) */
    private int connectionTimeout;

    /** 默认 QoS */
    private int defaultQos;

    /** 订阅 topic 列表 (通配符: farm/+/sensor/+/data, farm/+/control/resp) */
    private String[] topics;

    /**
     * 创建 MQTT 客户端实例
     * 使用 MemoryPersistence 避免磁盘持久化, 适合边缘+云端通信
     */
    @Bean
    public MqttClient mqttClient(MqttMessageCallback callback) {
        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setKeepAliveInterval(keepAliveInterval);
            options.setConnectionTimeout(connectionTimeout);
            options.setAutomaticReconnect(true); // 断线自动重连
            options.setCleanSession(false); // 保留会话, 确保离线消息不丢失

            // 设置回调
            client.setCallback(callback);

            // 连接
            client.connect(options);
            log.info("MQTT 客户端连接成功: broker={}, clientId={}", brokerUrl, clientId);

            // 订阅 topic
            for (String topic : topics) {
                client.subscribe(topic, defaultQos);
                log.info("MQTT 订阅 topic: {} (QoS={})", topic, defaultQos);
            }

            return client;

        } catch (Exception e) {
            log.error("MQTT 客户端初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("MQTT 连接失败", e);
        }
    }
}
