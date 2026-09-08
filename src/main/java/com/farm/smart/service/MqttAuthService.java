package com.farm.smart.service;

/**
 * MQTT 设备认证鉴权服务接口
 * 供 EMQX HTTP Auth 回调使用, 实现 per-device 身份验证与 ACL 控制
 *
 * @author Smart Farm Team
 */
public interface MqttAuthService {

    /**
     * EMQX 认证回调 - 验证传感器设备身份
     * 根据 username(sensorCode) 查询 sensor 表, 校验 device_secret
     *
     * @param clientId MQTT 客户端ID
     * @param username 用户名 (对应 sensor.sensor_code)
     * @param password 密码 (对应 sensor.device_secret)
     * @return true-认证通过, false-认证失败
     */
    boolean authenticate(String clientId, String username, String password);

    /**
     * EMQX ACL 回调 - 验证设备是否有权发布/订阅特定 topic
     * 传感器只能发布: farm/{farmId}/sensor/{sensorCode}/data, farm/{farmId}/device/status, farm/{farmId}/ota/progress
     * 传感器只能订阅: farm/{farmId}/ota/command
     *
     * @param username 用户名 (对应 sensor.sensor_code)
     * @param topic    MQTT topic
     * @param action   动作: publish / subscribe
     * @return true-允许, false-拒绝
     */
    boolean checkAcl(String username, String topic, String action);
}
