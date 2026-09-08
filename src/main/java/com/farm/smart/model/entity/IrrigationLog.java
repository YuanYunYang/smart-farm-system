package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.farm.smart.model.enums.IrrigationStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 灌溉记录实体
 * 记录每一次灌溉执行的全生命周期
 *
 * @author Smart Farm Team
 */
@Data
@TableName("irrigation_log")
public class IrrigationLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 关联的灌溉规则ID */
    private Long ruleId;

    /** 农场ID */
    private Long farmId;

    /** 地块ID */
    private Long fieldId;

    /** 阀门设备ID */
    private String valveDeviceId;

    /** 触发类型: 0-自动规则触发, 1-手动触发 */
    private Integer triggerType;

    /** 灌溉状态 (见 IrrigationStatus 枚举) */
    private IrrigationStatus status;

    /** 计划灌溉时长 (秒) */
    private Integer plannedDuration;

    /** 实际灌溉时长 (秒) */
    private Integer actualDuration;

    /** 触发时土壤湿度 (%) */
    private Double triggerSoilMoisture;

    /** 用水量 (升) */
    private Double waterUsage;

    /** MQTT 指令流水号 (用于关联设备回复) */
    private String commandId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行结果消息 */
    private String resultMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
