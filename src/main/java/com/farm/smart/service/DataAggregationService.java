package com.farm.smart.service;

import com.farm.smart.model.dto.DashboardQueryDTO;
import com.farm.smart.model.vo.DashboardVO;

import java.util.Map;

/**
 * 数据聚合服务接口
 * 定时聚合传感器数据 + 看板聚合查询
 *
 * @author Smart Farm Team
 */
public interface DataAggregationService {

    /**
     * 每小时聚合任务
     * 从 InfluxDB 提取原始传感器数据, 计算小时均值/最大值/最小值
     * 写入 InfluxDB 聚合 measurement, 减少查询扫描量
     */
    void aggregateHourlyData();

    /**
     * 获取看板聚合数据
     * 包含: 实时传感器值 + 24h趋势 + 灌溉统计 + 告警统计 + 病害识别统计
     */
    DashboardVO getDashboard(DashboardQueryDTO query);

    /**
     * 获取传感器历史趋势 (从 InfluxDB 查询)
     */
    Map<String, Object> getSensorTrend(Long farmId, String sensorType,
                                       String timeRange);
}
