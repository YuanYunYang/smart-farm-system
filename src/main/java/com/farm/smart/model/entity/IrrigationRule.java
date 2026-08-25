package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * 灌溉规则实体
 * 自动灌溉引擎的核心配置:
 * - 触发条件: 土壤湿度低于阈值
 * - 时间约束: 只在允许灌溉时段执行
 * - 安全限制: 最大单次时长、每日最大次数、雨天跳过
 *
 * @author Smart Farm Team
 */
@Data
@TableName("irrigation_rule")
public class IrrigationRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 农场ID */
    private Long farmId;

    /** 地块ID */
    private Long fieldId;

    /** 规则名称 */
    private String name;

    /** 土壤湿度阈值 (%) - 低于此值触发灌溉 */
    private Double soilMoistureThreshold;

    /** 目标土壤湿度 (%) - 灌溉到此值后停止 */
    private Double targetSoilMoisture;

    /** 允许灌溉时段 - 开始 (如 06:00) */
    private LocalTime allowedStart;

    /** 允许灌溉时段 - 结束 (如 22:00) */
    private LocalTime allowedEnd;

    /** 单次灌溉最大时长 (秒) */
    private Integer maxDurationSeconds;

    /** 每日最大灌溉次数 */
    private Integer maxDailyCount;

    /** 雨天是否跳过: 0-否, 1-是 */
    private Integer skipIfRain;

    /** 状态: 0-停用, 1-启用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
