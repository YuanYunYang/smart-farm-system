package com.farm.smart.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 传感器数据事实 (Drools 规则引擎 Fact)
 * <p>
 * 作为规则引擎的输入事实, 包含传感器采集的关键数据,
 * 规则匹配后将告警结果写入 results 列表
 *
 * @author Smart Farm Team
 */
@Data
public class SensorDataFact implements Serializable {

    /** 农场ID */
    private Long farmId;

    /** 传感器ID */
    private Long sensorId;

    /** 传感器类型 (temperature/humidity/soil_moisture/light/co2) */
    private String sensorType;

    /** 传感器数值 */
    private Double value;

    /** 采集时间戳 (毫秒) */
    private Long timestamp;

    /** 规则评估结果列表 (规则命中后填充) */
    private List<AlarmResult> results = new ArrayList<>();

    /**
     * 添加规则评估结果
     *
     * @param result 告警结果
     */
    public void addResult(AlarmResult result) {
        results.add(result);
    }
}
