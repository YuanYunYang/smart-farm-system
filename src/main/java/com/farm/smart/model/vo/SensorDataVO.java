package com.farm.smart.model.vo;

import com.farm.smart.model.enums.SensorType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 传感器数据 VO (最新值 + 查询结果)
 *
 * @author Smart Farm Team
 */
@Data
public class SensorDataVO implements Serializable {

    /** 传感器ID */
    private Long sensorId;

    /** 传感器编号 */
    private String sensorCode;

    /** 传感器名称 */
    private String sensorName;

    /** 传感器类型 */
    private SensorType type;

    /** 单位 */
    private String unit;

    /** 数值 */
    private Double value;

    /** 是否超过阈值 */
    private Boolean overThreshold;

    /** 在线状态 */
    private Integer onlineStatus;

    /** 数据时间 */
    private LocalDateTime dataTime;
}
