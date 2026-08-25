package com.farm.smart.service;

import com.farm.smart.model.dto.SensorDataDTO;

import java.util.Map;

/**
 * MQTT 消息服务接口
 * 负责下发控制指令到边缘设备 (如灌溉阀门控制)
 *
 * @author Smart Farm Team
 */
public interface MqttMessageService {

    /**
     * 下发灌溉控制指令到树莓派网关
     * Topic: farm/{farmId}/control/req
     *
     * @param farmId        农场ID
     * @param fieldId       地块ID
     * @param valveDeviceId 阀门设备ID
     * @param action        动作: OPEN / CLOSE
     * @param duration      持续时间 (秒), OPEN 时有效
     * @return 指令流水号 (commandId)
     */
    String sendIrrigationCommand(Long farmId, Long fieldId,
                                  String valveDeviceId, String action,
                                  Integer duration);

    /**
     * 下发设备配置更新指令
     * Topic: farm/{farmId}/config/req
     */
    void sendConfigCommand(Long farmId, String sensorCode, Map<String, Object> config);

    /**
     * 解析收到的 MQTT 消息 (传感器数据上报 / 设备回复)
     */
    void handleIncomingMessage(String topic, String payload);

    /**
     * 将传感器数据 DTO 推送给 WebSocket 看板
     */
    void pushToWebSocket(SensorDataDTO dto);
}
