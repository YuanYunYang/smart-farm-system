package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.farm.smart.model.enums.AlarmLevel;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警记录实体
 * 记录传感器超阈值、设备离线、灌溉异常等告警
 *
 * @author Smart Farm Team
 */
@Data
@TableName("alarm")
public class Alarm implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 农场ID */
    private Long farmId;

    /** 地块ID (可空) */
    private Long fieldId;

    /** 传感器ID (可空, 设备告警时为空) */
    private Long sensorId;

    /** 告警级别 (见 AlarmLevel 枚举) */
    private AlarmLevel level;

    /** 告警类型: SENSOR_THRESHOLD / DEVICE_OFFLINE / IRRIGATION_ERROR */
    private String type;

    /** 告警标题 */
    private String title;

    /** 告警内容 */
    private String content;

    /** 处理状态: 0-未处理, 1-已处理, 2-已忽略 */
    private Integer handleStatus;

    /** 处理人 */
    private String handler;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理备注 */
    private String handleRemark;

    /** 告警时间 */
    private LocalDateTime alarmTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
