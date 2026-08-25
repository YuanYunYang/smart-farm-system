package com.farm.smart.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统状态码枚举
 *
 * @author Smart Farm Team
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /* 成功 */
    SUCCESS(200, "操作成功"),

    /* 通用失败 (400-499) */
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    /* 业务失败 (1000-1999) */
    FARM_NOT_FOUND(1001, "农场不存在"),
    FIELD_NOT_FOUND(1002, "地块不存在"),
    SENSOR_NOT_FOUND(1003, "传感器不存在"),
    SENSOR_OFFLINE(1004, "传感器已离线"),

    /* 灌溉相关 (2000-2099) */
    IRRIGATION_RULE_NOT_FOUND(2001, "灌溉规则不存在"),
    IRRIGATION_TIME_LIMIT(2002, "当前时间不在允许灌溉时段"),
    IRRIGATION_MAX_DURATION(2003, "超出单次灌溉最大时长限制"),
    IRRIGATION_DAILY_LIMIT(2004, "超出每日灌溉次数限制"),
    IRRIGATION_RAINY_SKIP(2005, "雨天跳过灌溉"),
    IRRIGATION_ALREADY_RUNNING(2006, "灌溉正在进行中"),

    /* AI 识别相关 (3000-3099) */
    DISEASE_DETECT_FAILED(3001, "病虫害识别失败"),
    DISEASE_IMAGE_EMPTY(3002, "图片不能为空"),
    DISEASE_AI_SERVICE_ERROR(3003, "AI识别服务异常"),

    /* 告警相关 (4000-4099) */
    ALARM_THRESHOLD_EXCEEDED(4001, "传感器数值超阈值"),
    ALARM_DEVICE_OFFLINE(4002, "设备离线"),

    /* 系统错误 (500) */
    INTERNAL_ERROR(500, "系统内部错误"),
    MQTT_CONNECT_FAILED(5001, "MQTT连接失败"),
    INFLUXDB_ERROR(5002, "时序数据库操作异常");

    /** 状态码 */
    private final Integer code;

    /** 提示信息 */
    private final String message;
}
