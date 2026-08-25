package com.farm.smart.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 告警级别枚举
 *
 * @author Smart Farm Team
 */
@Getter
@AllArgsConstructor
public enum AlarmLevel {

    /** 提示级别 (常规监控提醒) */
    INFO(1, "提示"),

    /** 警告级别 (需要关注) */
    WARN(2, "警告"),

    /** 严重级别 (需要立即处理) */
    CRITICAL(3, "严重"),

    /** 紧急级别 (设备故障/灌溉异常等) */
    URGENT(4, "紧急");

    /** 级别编码 (存入数据库) */
    @EnumValue
    @JsonValue
    private final Integer code;

    /** 描述 */
    private final String desc;

    /**
     * 根据 code 获取枚举
     */
    public static AlarmLevel getByCode(Integer code) {
        for (AlarmLevel level : values()) {
            if (level.getCode().equals(code)) {
                return level;
            }
        }
        return null;
    }
}
