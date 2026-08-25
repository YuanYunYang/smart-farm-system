package com.farm.smart.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 灌溉状态枚举
 *
 * @author Smart Farm Team
 */
@Getter
@AllArgsConstructor
public enum IrrigationStatus {

    /** 待执行 (规则触发, 指令尚未下发) */
    PENDING(0, "待执行"),

    /** 执行中 (指令已下发, 水阀已开启) */
    RUNNING(1, "执行中"),

    /** 已完成 (设备回复成功) */
    COMPLETED(2, "已完成"),

    /** 失败 (设备回复失败或超时) */
    FAILED(3, "失败"),

    /** 已取消 (因安全限制跳过) */
    CANCELLED(4, "已取消");

    /** 状态码 (存入数据库) */
    @EnumValue
    @JsonValue
    private final Integer code;

    /** 描述 */
    private final String desc;

    /**
     * 根据 code 获取枚举
     */
    public static IrrigationStatus getByCode(Integer code) {
        for (IrrigationStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
