package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.farm.smart.model.enums.SensorType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 传感器设备实体
 * 对应 ESP32 采集节点上的各类传感器
 *
 * @author Smart Farm Team
 */
@Data
@TableName("sensor")
public class Sensor implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 所属农场ID */
    private Long farmId;

    /** 所属地块ID */
    private Long fieldId;

    /** 传感器编号 (与硬件上报的 sensorId 对应, 如 ESP32-A01) */
    private String sensorCode;

    /** 传感器名称 */
    private String name;

    /** 传感器类型 (见 SensorType 枚举) */
    private SensorType type;

    /** 硬件型号 (如 DHT22, FC-28, BH1750, MH-Z19B) */
    private String model;

    /** 采集间隔 (秒) */
    private Integer intervalSeconds;

    /** 预警上限 */
    private Double thresholdMax;

    /** 预警下限 */
    private Double thresholdMin;

    /** 设备认证密钥 (用于 EMQX HTTP Auth 校验) */
    private String deviceSecret;

    /** 在线状态: 0-离线, 1-在线 */
    private Integer onlineStatus;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
