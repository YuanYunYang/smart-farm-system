package com.farm.smart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.smart.model.entity.Alarm;
import com.farm.smart.model.enums.AlarmLevel;
import com.farm.smart.repository.AlarmMapper;
import com.farm.smart.service.AlarmService;
import com.farm.smart.websocket.FarmWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 告警服务实现
 * 多级推送: WebSocket 实时 + 数据库记录 + Redis 防重复
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmMapper alarmMapper;
    private final FarmWebSocketHandler webSocketHandler;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 时间格式 */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 告警去重 Redis Key 前缀 (同一传感器同一告警5分钟内不重复) */
    private static final String ALARM_DEDUP_PREFIX = "alarm:dedup:";

    @Override
    public Alarm createAlarm(Long farmId, Long fieldId, Long sensorId,
                              AlarmLevel level, String type, String title, String content) {
        // Redis 去重: 同一传感器同一类型告警, 5分钟内不重复创建
        String dedupKey = ALARM_DEDUP_PREFIX + sensorId + ":" + type;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(dedupKey))) {
            log.debug("告警去重, 5分钟内已存在相同告警: key={}", dedupKey);
            return null;
        }

        Alarm alarm = new Alarm();
        alarm.setFarmId(farmId);
        alarm.setFieldId(fieldId);
        alarm.setSensorId(sensorId);
        alarm.setLevel(level);
        alarm.setType(type);
        alarm.setTitle(title);
        alarm.setContent(content);
        alarm.setHandleStatus(0); // 未处理
        alarm.setAlarmTime(LocalDateTime.now());
        alarm.setCreateTime(LocalDateTime.now());
        alarmMapper.insert(alarm);

        // 设置去重缓存 (5分钟过期)
        stringRedisTemplate.opsForValue().set(dedupKey, String.valueOf(alarm.getId()), 5, TimeUnit.MINUTES);

        // WebSocket 实时推送给看板
        pushAlarmToWebSocket(farmId, alarm);

        log.warn("创建告警: level={}, type={}, title={}, content={}", level.getDesc(), type, title, content);
        return alarm;
    }

    @Override
    public void checkSensorThreshold(Long farmId, Long fieldId, Long sensorId,
                                      String sensorType, Double value,
                                      Double thresholdMax, Double thresholdMin) {
        // 检查上限
        if (thresholdMax != null && value > thresholdMax) {
            String type = "SENSOR_THRESHOLD_MAX";
            String title = sensorType + "超上限告警";
            String content = String.format("传感器(%d) %s 当前值 %.2f 超过上限阈值 %.2f",
                    sensorId, sensorType, value, thresholdMax);
            AlarmLevel level = determineAlarmLevel(sensorType, value, thresholdMax, true);
            createAlarm(farmId, fieldId, sensorId, level, type, title, content);
        }

        // 检查下限
        if (thresholdMin != null && value < thresholdMin) {
            String type = "SENSOR_THRESHOLD_MIN";
            String title = sensorType + "低于下限告警";
            String content = String.format("传感器(%d) %s 当前值 %.2f 低于下限阈值 %.2f",
                    sensorId, sensorType, value, thresholdMin);
            AlarmLevel level = determineAlarmLevel(sensorType, value, thresholdMin, false);
            createAlarm(farmId, fieldId, sensorId, level, type, title, content);
        }
    }

    @Override
    public void deviceOfflineAlarm(Long farmId, Long sensorId, String sensorCode) {
        String type = "DEVICE_OFFLINE";
        String title = "设备离线告警";
        String content = String.format("传感器 %s (ID:%d) 已超过10分钟未上报数据, 疑似离线", sensorCode, sensorId);
        createAlarm(farmId, null, sensorId, AlarmLevel.URGENT, type, title, content);
    }

    @Override
    public void irrigationErrorAlarm(Long farmId, Long fieldId, String message) {
        String type = "IRRIGATION_ERROR";
        String title = "灌溉异常告警";
        createAlarm(farmId, fieldId, null, AlarmLevel.CRITICAL, type, title, message);
    }

    @Override
    public List<Alarm> listAlarms(Long farmId, Integer handleStatus) {
        log.info("查询告警列表: farmId={}, handleStatus={}", farmId, handleStatus);
        // 使用 BaseMapper 条件查询
        return alarmMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getFarmId, farmId)
                .eq(handleStatus != null, Alarm::getHandleStatus, handleStatus)
                .orderByDesc(Alarm::getAlarmTime)
                .last("LIMIT 100"));
    }

    @Override
    public boolean handleAlarm(Long id, String handler, String remark) {
        int result = alarmMapper.updateHandleStatus(id, handler, remark, LocalDateTime.now());
        log.info("处理告警: id={}, handler={}, result={}", id, handler, result);
        return result > 0;
    }

    @Override
    public int countTodayAlarms(Long farmId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        return alarmMapper.countToday(farmId, startOfDay, now);
    }

    @Override
    public List<Alarm> getRecentAlarms(Long farmId, int limit) {
        return alarmMapper.selectRecent(farmId, limit);
    }

    /**
     * 根据传感器类型和超限程度确定告警级别
     */
    private AlarmLevel determineAlarmLevel(String sensorType, Double value,
                                            Double threshold, boolean isMax) {
        double ratio = isMax ? value / threshold : threshold / value;
        if (ratio >= 1.5) {
            return AlarmLevel.CRITICAL; // 超限50%以上为严重
        } else if (ratio >= 1.2) {
            return AlarmLevel.WARN;     // 超限20%-50%为警告
        } else {
            return AlarmLevel.INFO;      // 轻微超限为提示
        }
    }

    /**
     * WebSocket 推送告警通知
     */
    private void pushAlarmToWebSocket(Long farmId, Alarm alarm) {
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "ALARM");
            wsMessage.put("level", alarm.getLevel().getDesc());
            wsMessage.put("title", alarm.getTitle());
            wsMessage.put("content", alarm.getContent());
            wsMessage.put("alarmTime", alarm.getAlarmTime().format(FMT));
            wsMessage.put("alarmId", alarm.getId());

            String json = objectMapper.writeValueAsString(wsMessage);
            webSocketHandler.sendToFarm(farmId, json);
        } catch (Exception e) {
            log.error("WebSocket 推送告警失败: {}", e.getMessage(), e);
        }
    }
}
