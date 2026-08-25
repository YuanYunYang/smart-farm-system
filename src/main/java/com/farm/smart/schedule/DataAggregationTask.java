package com.farm.smart.schedule;

import com.farm.smart.service.DataAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据聚合定时任务
 * 每小时聚合传感器数据, 计算 avg/max/min, 写入 InfluxDB 聚合 measurement
 * 减少看板查询的扫描量
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAggregationTask {

    private final DataAggregationService dataAggregationService;

    /**
     * 每小时聚合传感器数据
     * "0 0 * * * ?" = 每整点执行
     */
    @Scheduled(cron = "${irrigation.schedule.aggregation-cron:0 0 * * * ?}")
    public void aggregateData() {
        log.info("定时任务: 开始每小时数据聚合");
        try {
            dataAggregationService.aggregateHourlyData();
        } catch (Exception e) {
            log.error("数据聚合任务异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 每天凌晨清理过期的告警缓存和统计数据
     * "0 0 2 * * ?" = 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMaintenance() {
        log.info("定时任务: 每日维护 - 清理过期数据");
        // TODO: 清理30天前的告警记录、灌溉日志等
        // 可根据实际需求实现数据归档逻辑
    }
}
