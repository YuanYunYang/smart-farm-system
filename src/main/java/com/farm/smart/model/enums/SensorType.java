package com.farm.smart.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 传感器类型枚举
 *
 * @author Smart Farm Team
 */
@Getter
@AllArgsConstructor
public enum SensorType {

    /** 空气温度 (DHT22) */
    TEMPERATURE("temperature", "空气温度", "°C"),

    /** 空气湿度 (DHT22) */
    HUMIDITY("humidity", "空气湿度", "%"),

    /** 土壤湿度 (FC-28) */
    SOIL_MOISTURE("soil_moisture", "土壤湿度", "%"),

    /** 光照强度 (BH1750) */
    LIGHT("light", "光照强度", "lux"),

    /** CO2 浓度 (MH-Z19B) */
    CO2("co2", "CO2浓度", "ppm");

    /** 传感器标识 (与硬件上报的 JSON 字段对应, 存入数据库) */
    @EnumValue
    @JsonValue
    private final String code;

    /** 中文名称 */
    private final String name;

    /** 单位 */
    private final String unit;

    /**
     * 根据 code 获取枚举
     */
    public static SensorType getByCode(String code) {
        for (SensorType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
