package com.farm.smart.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * MQTT 上报的传感器数据 DTO
 * <p>
 * 树莓派网关聚合多节点数据后, 通过 MQTT topic 上报的 JSON 结构:
 * <pre>
 * {
 *   "farmId": 1,
 *   "fieldId": 1,
 *   "sensorId": "ESP32-A01",
 *   "timestamp": 1716000000000,
 *   "data": {
 *     "temperature": 28.5,
 *     "humidity": 65.0,
 *     "soil_moisture": 35.2,
 *     "light": 12000,
 *     "co2": 450
 *   },
 *   "battery": 3.7
 * }
 * </pre>
 *
 * @author Smart Farm Team
 */
@Data
public class SensorDataDTO implements Serializable {

    /** 农场ID */
    private Long farmId;

    /** 地块ID */
    private Long fieldId;

    /** 传感器节点ID (对应 sensor.sensor_code) */
    private String sensorId;

    /** 采集时间戳 (毫秒) */
    private Long timestamp;

    /**
     * 传感器数据键值对
     * key: SensorType.code (temperature / humidity / soil_moisture / light / co2)
     * value: 数值
     */
    private Map<String, Double> data;

    /** 电池电压 (V) - 边缘节点供电监控 */
    private Double battery;
}
