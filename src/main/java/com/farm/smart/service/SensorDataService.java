package com.farm.smart.service;

import com.farm.smart.model.dto.SensorDataDTO;
import com.farm.smart.model.entity.Sensor;
import com.farm.smart.model.vo.SensorDataVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 传感器数据服务接口
 * 负责: InfluxDB 写入/查询 + MySQL 最新值缓存 + WebSocket 推送
 *
 * @author Smart Farm Team
 */
public interface SensorDataService {

    /**
     * 处理 MQTT 上报的传感器数据
     * 1. 写入 InfluxDB (时序存储)
     * 2. 更新 MySQL 传感器最新值缓存
     * 3. WebSocket 推送给看板
     * 4. 检查阈值, 触发告警
     *
     * @param dto MQTT 上报数据
     */
    void processSensorData(SensorDataDTO dto);

    /**
     * 查询某传感器最新数据
     */
    SensorDataVO getLatestData(Long sensorId);

    /**
     * 查询农场所有传感器最新数据
     */
    List<SensorDataVO> getFarmLatestData(Long farmId);

    /**
     * 查询传感器历史趋势数据 (从 InfluxDB)
     *
     * @param farmId    农场ID
     * @param sensorType 传感器类型
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 时间序列数据点
     */
    Map<String, Object> getHistoryTrend(Long farmId, String sensorType,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime);

    /**
     * 从 InfluxDB 查询 24 小时趋势数据
     */
    Map<String, Object> get24HourTrend(Long farmId);

    /**
     * 获取农场下所有传感器设备列表
     */
    List<Sensor> listSensors(Long farmId);

    /**
     * 更新传感器在线状态
     */
    void updateSensorStatus(String sensorCode, Integer status);
}
