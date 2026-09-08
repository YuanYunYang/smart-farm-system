package com.farm.smart.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT Topic 路由处理器
 * 解析 topic 路径, 提取农场ID/传感器ID等参数, 分发到对应的处理逻辑
 * <p>
 * Topic 规划:
 * - farm/{farmId}/sensor/{sensorId}/data    传感器数据上报 (上行)
 * - farm/{farmId}/control/req                控制指令下发 (下行)
 * - farm/{farmId}/control/resp               设备控制回复 (上行)
 * - farm/{farmId}/device/status              设备心跳/状态 (上行)
 * - farm/{farmId}/config/req                 配置更新指令 (下行)
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
public class MqttTopicHandler {

    // Topic 正则匹配模式
    private static final Pattern SENSOR_DATA_PATTERN =
            Pattern.compile("farm/(\\d+)/sensor/([^/]+)/data");

    private static final Pattern CONTROL_RESP_PATTERN =
            Pattern.compile("farm/(\\d+)/control/resp");

    private static final Pattern DEVICE_STATUS_PATTERN =
            Pattern.compile("farm/(\\d+)/device/status");

    /** OTA 升级进度上报 topic 模式 */
    private static final Pattern OTA_PROGRESS_PATTERN =
            Pattern.compile("farm/(\\d+)/ota/progress");

    /**
     * 解析 topic 并返回处理类型
     *
     * @param topic MQTT topic
     * @return 消息类型 + 解析出的参数
     */
    public TopicParseResult parseTopic(String topic) {
        Matcher sensorMatcher = SENSOR_DATA_PATTERN.matcher(topic);
        if (sensorMatcher.matches()) {
            return new TopicParseResult(MessageType.SENSOR_DATA,
                    Long.parseLong(sensorMatcher.group(1)),
                    sensorMatcher.group(2));
        }

        Matcher controlMatcher = CONTROL_RESP_PATTERN.matcher(topic);
        if (controlMatcher.matches()) {
            return new TopicParseResult(MessageType.CONTROL_RESPONSE,
                    Long.parseLong(controlMatcher.group(1)), null);
        }

        Matcher deviceMatcher = DEVICE_STATUS_PATTERN.matcher(topic);
        if (deviceMatcher.matches()) {
            return new TopicParseResult(MessageType.DEVICE_STATUS,
                    Long.parseLong(deviceMatcher.group(1)), null);
        }

        Matcher otaMatcher = OTA_PROGRESS_PATTERN.matcher(topic);
        if (otaMatcher.matches()) {
            return new TopicParseResult(MessageType.OTA_PROGRESS,
                    Long.parseLong(otaMatcher.group(1)), null);
        }

        log.warn("未识别的 MQTT topic: {}", topic);
        return new TopicParseResult(MessageType.UNKNOWN, null, null);
    }

    /**
     * 构建传感器数据上报 topic
     */
    public String buildSensorDataTopic(Long farmId, String sensorId) {
        return String.format("farm/%d/sensor/%s/data", farmId, sensorId);
    }

    /**
     * 构建控制指令下发 topic
     */
    public String buildControlReqTopic(Long farmId) {
        return String.format("farm/%d/control/req", farmId);
    }

    /**
     * 构建设备配置 topic
     */
    public String buildConfigReqTopic(Long farmId) {
        return String.format("farm/%d/config/req", farmId);
    }

    /**
     * 构建 OTA 升级指令下发 topic
     */
    public String buildOtaCommandTopic(Long farmId) {
        return String.format("farm/%d/ota/command", farmId);
    }

    /**
     * 构建 OTA 升级进度上报 topic
     */
    public String buildOtaProgressTopic(Long farmId) {
        return String.format("farm/%d/ota/progress", farmId);
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        SENSOR_DATA,         // 传感器数据上报
        CONTROL_RESPONSE,    // 设备控制回复
        DEVICE_STATUS,       // 设备状态
        OTA_PROGRESS,        // OTA 升级进度上报
        UNKNOWN              // 未知
    }

    /**
     * Topic 解析结果
     */
    public record TopicParseResult(MessageType type, Long farmId, String sensorId) {
    }
}
