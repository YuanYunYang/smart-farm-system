package com.farm.smart.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * 灌溉规则创建/修改 DTO
 *
 * @author Smart Farm Team
 */
@Data
public class IrrigationRuleDTO implements Serializable {

    /** 农场ID */
    @NotNull(message = "农场ID不能为空")
    private Long farmId;

    /** 地块ID */
    @NotNull(message = "地块ID不能为空")
    private Long fieldId;

    /** 规则名称 */
    @NotNull(message = "规则名称不能为空")
    private String name;

    /** 土壤湿度阈值 (%) - 低于此值触发 */
    @NotNull(message = "土壤湿度阈值不能为空")
    @Min(value = 0, message = "土壤湿度阈值不能小于0")
    @Max(value = 100, message = "土壤湿度阈值不能大于100")
    private Double soilMoistureThreshold;

    /** 目标土壤湿度 (%) */
    @NotNull(message = "目标土壤湿度不能为空")
    private Double targetSoilMoisture;

    /** 允许灌溉时段 - 开始 (HH:mm) */
    @NotNull(message = "允许灌溉开始时间不能为空")
    private LocalTime allowedStart;

    /** 允许灌溉时段 - 结束 (HH:mm) */
    @NotNull(message = "允许灌溉结束时间不能为空")
    private LocalTime allowedEnd;

    /** 单次灌溉最大时长 (秒), 默认 1800 (30分钟) */
    @Min(value = 60, message = "单次灌溉最大时长至少60秒")
    private Integer maxDurationSeconds;

    /** 每日最大灌溉次数, 默认 4 */
    @Min(value = 1, message = "每日灌溉次数至少1次")
    private Integer maxDailyCount;

    /** 雨天是否跳过: 0-否, 1-是 */
    private Integer skipIfRain;

    /** 是否启用: 0-停用, 1-启用 */
    private Integer enabled;
}
