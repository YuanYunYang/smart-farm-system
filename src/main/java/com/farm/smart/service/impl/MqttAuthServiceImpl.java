package com.farm.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.smart.model.entity.Sensor;
import com.farm.smart.repository.SensorMapper;
import com.farm.smart.service.MqttAuthService;
import com.farm.smart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT 设备认证鉴权服务实现
 * <p>
 * 供 EMQX HTTP Auth 回调使用:
 * - 认证: 根据 username(sensorCode) 查 sensor 表, 校验 device_secret
 * - ACL: 校验设备是否有权发布/订阅特定 topic
 * - 使用 Redis 缓存认证结果 (5分钟TTL)
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttAuthServiceImpl implements MqttAuthService {

    private final SensorMapper sensorMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 认证缓存 Redis Key 前缀 */
    private static final String AUTH_CACHE_PREFIX = "mqtt:auth:";

    /** 认证成功缓存 TTL (5分钟) */
    private static final long AUTH_CACHE_TTL_MINUTES = 5;

    /** 认证失败缓存 TTL (1分钟, 防止暴力枚举) */
    private static final long AUTH_FAIL_CACHE_TTL_MINUTES = 1;

    /** 允许传感器发布的 topic 模式 */
    private static final Pattern SENSOR_DATA_TOPIC = Pattern.compile("farm/(\\d+)/sensor/([^/]+)/data");
    private static final Pattern DEVICE_STATUS_TOPIC = Pattern.compile("farm/(\\d+)/device/status");
    private static final Pattern OTA_PROGRESS_TOPIC = Pattern.compile("farm/(\\d+)/ota/progress");

    /** 允许传感器订阅的 topic 模式 */
    private static final Pattern OTA_COMMAND_TOPIC = Pattern.compile("farm/(\\d+)/ota/command");

    @Override
    public boolean authenticate(String clientId, String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            log.warn("EMQX 认证失败: 参数为空, clientId={}, username={}", clientId, username);
            return false;
        }

        // 先查 Redis 缓存
        String cacheKey = AUTH_CACHE_PREFIX + username;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("deny".equals(cached)) {
                log.debug("EMQX 认证缓存命中(拒绝): username={}", username);
                return false;
            }
            // 缓存中存储的是 farmId, 表示认证成功
            log.debug("EMQX 认证缓存命中(通过): username={}, farmId={}", username, cached);
            return true;
        }

        // MQTT 认证无 HTTP 请求上下文, 需忽略租户隔离
        TenantContext.setIgnore(true);
        try {
            // 根据 username(sensorCode) 查询传感器
            Sensor sensor = sensorMapper.selectOne(
                    new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorCode, username));

            if (sensor == null) {
                log.warn("EMQX 认证失败: 传感器不存在, username={}", username);
                cacheDeny(cacheKey);
                return false;
            }

            // 校验设备密钥
            if (sensor.getDeviceSecret() == null || !sensor.getDeviceSecret().equals(password)) {
                log.warn("EMQX 认证失败: 设备密钥不匹配, username={}", username);
                cacheDeny(cacheKey);
                return false;
            }

            // 认证成功, 缓存 farmId (供 ACL 使用)
            String farmIdStr = String.valueOf(sensor.getFarmId());
            stringRedisTemplate.opsForValue().set(cacheKey, farmIdStr, AUTH_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("EMQX 认证通过: username={}, sensorId={}, farmId={}", username, sensor.getId(), sensor.getFarmId());
            return true;

        } catch (Exception e) {
            log.error("EMQX 认证异常: username={}, error={}", username, e.getMessage(), e);
            return false;
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public boolean checkAcl(String username, String topic, String action) {
        if (username == null || topic == null || action == null) {
            log.warn("EMQX ACL 校验失败: 参数为空, username={}, topic={}, action={}", username, topic, action);
            return false;
        }

        log.debug("EMQX ACL 校验: username={}, topic={}, action={}", username, topic, action);

        // 从缓存获取认证时记录的 farmId
        String cacheKey = AUTH_CACHE_PREFIX + username;
        String cachedFarmId = stringRedisTemplate.opsForValue().get(cacheKey);

        Long sensorFarmId = null;
        if (cachedFarmId != null && !"deny".equals(cachedFarmId)) {
            sensorFarmId = Long.valueOf(cachedFarmId);
        } else {
            // 缓存未命中或已过期, 重新查询传感器
            TenantContext.setIgnore(true);
            try {
                Sensor sensor = sensorMapper.selectOne(
                        new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorCode, username));
                if (sensor == null) {
                    log.warn("EMQX ACL 拒绝: 传感器不存在, username={}", username);
                    return false;
                }
                sensorFarmId = sensor.getFarmId();
            } finally {
                TenantContext.clear();
            }
        }

        // 校验 topic 中的 farmId 与传感器所属农场一致
        Long topicFarmId = extractFarmIdFromTopic(topic);
        if (topicFarmId == null || !topicFarmId.equals(sensorFarmId)) {
            log.warn("EMQX ACL 拒绝: topic 农场ID({})与传感器农场ID({})不匹配, username={}, topic={}",
                    topicFarmId, sensorFarmId, username, topic);
            return false;
        }

        // 校验 topic 是否在允许列表中
        if ("publish".equalsIgnoreCase(action)) {
            return checkPublishPermission(username, topic);
        } else if ("subscribe".equalsIgnoreCase(action)) {
            return checkSubscribePermission(topic);
        }

        log.warn("EMQX ACL 拒绝: 未知 action={}, username={}, topic={}", action, username, topic);
        return false;
    }

    /**
     * 校验发布权限
     * 传感器只能发布:
     * - farm/{farmId}/sensor/{sensorCode}/data
     * - farm/{farmId}/device/status
     * - farm/{farmId}/ota/progress
     */
    private boolean checkPublishPermission(String username, String topic) {
        // 传感器数据上报 topic
        Matcher sensorMatcher = SENSOR_DATA_TOPIC.matcher(topic);
        if (sensorMatcher.matches()) {
            String topicSensorCode = sensorMatcher.group(2);
            if (username.equals(topicSensorCode)) {
                return true;
            }
            log.warn("EMQX ACL 拒绝: topic 中的 sensorCode({})与 username({})不匹配",
                    topicSensorCode, username);
            return false;
        }

        // 设备状态 topic
        if (DEVICE_STATUS_TOPIC.matcher(topic).matches()) {
            return true;
        }

        // OTA 进度上报 topic
        if (OTA_PROGRESS_TOPIC.matcher(topic).matches()) {
            return true;
        }

        log.warn("EMQX ACL 拒绝: topic 不在允许发布的列表中, topic={}", topic);
        return false;
    }

    /**
     * 校验订阅权限
     * 传感器只能订阅:
     * - farm/{farmId}/ota/command (接收 OTA 升级指令)
     */
    private boolean checkSubscribePermission(String topic) {
        if (OTA_COMMAND_TOPIC.matcher(topic).matches()) {
            return true;
        }

        log.warn("EMQX ACL 拒绝: topic 不在允许订阅的列表中, topic={}", topic);
        return false;
    }

    /**
     * 从 topic 中提取农场ID
     */
    private Long extractFarmIdFromTopic(String topic) {
        Matcher matcher = Pattern.compile("farm/(\\d+)/").matcher(topic);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 缓存认证失败结果
     */
    private void cacheDeny(String cacheKey) {
        stringRedisTemplate.opsForValue().set(cacheKey, "deny", AUTH_FAIL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }
}
