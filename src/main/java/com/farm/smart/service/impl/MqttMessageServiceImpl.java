package com.farm.smart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.smart.mqtt.MqttClientManager;
import com.farm.smart.mqtt.MqttTopicHandler;
import com.farm.smart.model.dto.SensorDataDTO;
import com.farm.smart.service.MqttMessageService;
import com.farm.smart.service.SensorDataService;
import com.farm.smart.service.IrrigationService;
import com.farm.smart.websocket.FarmWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT 消息服务实现
 * 负责: 控制指令下发 + 接收消息分发 + WebSocket 推送
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageServiceImpl implements MqttMessageService {

    // 延迟注入避免循环依赖
    @Lazy
    private final SensorDataService sensorDataService;
    @Lazy
    private final IrrigationService irrigationService;

    private final MqttClientManager mqttClientManager;
    private final MqttTopicHandler topicHandler;
    private final FarmWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sendIrrigationCommand(Long farmId, Long fieldId,
                                         String valveDeviceId, String action,
                                         Integer duration) {
        // 生成指令流水号
        String commandId = "IRR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", commandId);
        payload.put("fieldId", fieldId);
        payload.put("valveDeviceId", valveDeviceId);
        payload.put("action", action);  // OPEN / CLOSE
        payload.put("duration", duration); // 秒
        payload.put("timestamp", System.currentTimeMillis());

        try {
            String topic = topicHandler.buildControlReqTopic(farmId);
            String json = objectMapper.writeValueAsString(payload);
            mqttClientManager.publish(topic, json);

            log.info("下发灌溉控制指令: farmId={}, fieldId={}, action={}, duration={}s, commandId={}",
                    farmId, fieldId, action, duration, commandId);
            return commandId;

        } catch (Exception e) {
            log.error("下发灌溉控制指令失败: farmId={}, fieldId={}", farmId, fieldId, e);
            throw new RuntimeException("MQTT 灌溉指令下发失败", e);
        }
    }

    @Override
    public void sendConfigCommand(Long farmId, String sensorCode, Map<String, Object> config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sensorCode", sensorCode);
        payload.put("config", config);
        payload.put("timestamp", System.currentTimeMillis());

        try {
            String topic = topicHandler.buildConfigReqTopic(farmId);
            String json = objectMapper.writeValueAsString(payload);
            mqttClientManager.publish(topic, json);
            log.info("下发设备配置指令: farmId={}, sensorCode={}", farmId, sensorCode);
        } catch (Exception e) {
            log.error("下发设备配置指令失败", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleIncomingMessage(String topic, String payload) {
        // 解析 topic 确定消息类型
        MqttTopicHandler.TopicParseResult parseResult = topicHandler.parseTopic(topic);

        try {
            Map<String, Object> message = objectMapper.readValue(payload, Map.class);

            switch (parseResult.type()) {
                case SENSOR_DATA -> {
                    // 传感器数据上报 -> 交给 SensorDataService 处理
                    SensorDataDTO dto = parseSensorData(message, parseResult.farmId(), parseResult.sensorId());
                    sensorDataService.processSensorData(dto);
                }
                case CONTROL_RESPONSE -> {
                    // 设备控制回复 -> 交给 IrrigationService 处理
                    handleControlResponse(message);
                }
                case DEVICE_STATUS -> {
                    // 设备状态/心跳 -> 更新在线状态
                    handleDeviceStatus(message, parseResult.farmId());
                }
                case UNKNOWN -> log.warn("收到未识别类型的 MQTT 消息: topic={}", topic);
            }
        } catch (Exception e) {
            log.error("解析 MQTT 消息失败: topic={}, payload={}", topic, payload, e);
        }
    }

    @Override
    public void pushToWebSocket(SensorDataDTO dto) {
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "SENSOR_DATA");
            wsMessage.put("farmId", dto.getFarmId());
            wsMessage.put("fieldId", dto.getFieldId());
            wsMessage.put("sensorId", dto.getSensorId());
            wsMessage.put("data", dto.getData());
            wsMessage.put("timestamp", dto.getTimestamp());
            wsMessage.put("battery", dto.getBattery());

            String json = objectMapper.writeValueAsString(wsMessage);
            webSocketHandler.sendToFarm(dto.getFarmId(), json);
        } catch (Exception e) {
            log.error("WebSocket 推送传感器数据失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 解析传感器数据上报消息
     */
    @SuppressWarnings("unchecked")
    private SensorDataDTO parseSensorData(Map<String, Object> message, Long farmId, String sensorId) {
        SensorDataDTO dto = new SensorDataDTO();
        dto.setFarmId(farmId);
        dto.setSensorId(sensorId);
        dto.setFieldId(message.get("fieldId") != null ? Long.valueOf(message.get("fieldId").toString()) : null);
        dto.setTimestamp(message.get("timestamp") != null ? Long.valueOf(message.get("timestamp").toString()) : System.currentTimeMillis());
        dto.setData((Map<String, Double>) message.get("data"));
        dto.setBattery(message.get("battery") != null ? Double.valueOf(message.get("battery").toString()) : null);
        return dto;
    }

    /**
     * 处理设备控制回复
     */
    @SuppressWarnings("unchecked")
    private void handleControlResponse(Map<String, Object> message) {
        String commandId = (String) message.get("commandId");
        Boolean success = (Boolean) message.get("success");
        Integer actualDuration = message.get("actualDuration") != null
                ? Integer.valueOf(message.get("actualDuration").toString()) : null;
        Double waterUsage = message.get("waterUsage") != null
                ? Double.valueOf(message.get("waterUsage").toString()) : null;
        String resultMessage = (String) message.get("message");

        // 委托给灌溉服务处理
        irrigationService.handleIrrigationResponse(commandId, success, actualDuration, waterUsage, resultMessage);
    }

    /**
     * 处理设备状态/心跳消息
     */
    @SuppressWarnings("unchecked")
    private void handleDeviceStatus(Map<String, Object> message, Long farmId) {
        String sensorCode = (String) message.get("sensorCode");
        Integer status = message.get("status") != null
                ? Integer.valueOf(message.get("status").toString()) : 1;
        // 更新传感器在线状态
        if (sensorCode != null) {
            sensorDataService.updateSensorStatus(sensorCode, status);
        }
    }
}
