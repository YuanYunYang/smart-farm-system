package com.farm.smart.service;

import com.farm.smart.model.entity.Alarm;
import com.farm.smart.model.enums.AlarmLevel;

import java.util.List;
import java.util.Map;

/**
 * 告警服务接口
 *
 * @author Smart Farm Team
 */
public interface AlarmService {

    /**
     * 创建告警
     * 同时: 写入数据库 + WebSocket 实时推送
     *
     * @param farmId   农场ID
     * @param fieldId  地块ID (可空)
     * @param sensorId 传感器ID (可空)
     * @param level    告警级别
     * @param type     告警类型
     * @param title    告警标题
     * @param content  告警内容
     * @return 告警记录
     */
    Alarm createAlarm(Long farmId, Long fieldId, Long sensorId,
                      AlarmLevel level, String type, String title, String content);

    /**
     * 检查传感器数值是否超阈值, 触发告警
     */
    void checkSensorThreshold(Long farmId, Long fieldId, Long sensorId,
                               String sensorType, Double value,
                               Double thresholdMax, Double thresholdMin);

    /**
     * 设备离线告警
     */
    void deviceOfflineAlarm(Long farmId, Long sensorId, String sensorCode);

    /**
     * 灌溉异常告警
     */
    void irrigationErrorAlarm(Long farmId, Long fieldId, String message);

    /**
     * 查询告警列表
     */
    List<Alarm> listAlarms(Long farmId, Integer handleStatus);

    /**
     * 处理告警
     */
    boolean handleAlarm(Long id, String handler, String remark);

    /**
     * 告警统计 (今日告警数)
     */
    int countTodayAlarms(Long farmId);

    /**
     * 最近告警列表 (看板用)
     */
    List<Alarm> getRecentAlarms(Long farmId, int limit);
}
