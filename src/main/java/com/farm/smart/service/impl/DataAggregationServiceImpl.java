package com.farm.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.smart.model.dto.DashboardQueryDTO;
import com.farm.smart.model.entity.Alarm;
import com.farm.smart.model.entity.DiseaseRecord;
import com.farm.smart.model.entity.Sensor;
import com.farm.smart.model.vo.DashboardVO;
import com.farm.smart.repository.DiseaseRecordMapper;
import com.farm.smart.repository.SensorMapper;
import com.farm.smart.service.AlarmService;
import com.farm.smart.service.DataAggregationService;
import com.farm.smart.service.IrrigationService;
import com.farm.smart.service.SensorDataService;
import com.farm.smart.model.vo.SensorDataVO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据聚合服务实现
 * <p>
 * 功能:
 * 1. 定时聚合: 每小时从 InfluxDB 提取原始传感器数据, 计算均值/最大值/最小值, 写入聚合 measurement
 * 2. 看板聚合: 汇总实时传感器值 + 24h趋势 + 灌溉统计 + 告警统计 + 病害统计
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAggregationServiceImpl implements DataAggregationService {

    private final InfluxDBClient influxDBClient;
    private final SensorMapper sensorMapper;
    private final SensorDataService sensorDataService;
    private final IrrigationService irrigationService;
    private final AlarmService alarmService;
    private final DiseaseRecordMapper diseaseRecordMapper;

    /** 聚合 measurement 名称 */
    private static final String AGG_MEASUREMENT = "sensor_hourly_agg";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void aggregateHourlyData() {
        log.info("=== 开始执行每小时数据聚合 ===");
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(1);
        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        Instant stopInstant = endTime.atZone(ZoneId.systemDefault()).toInstant();

        // 查询所有传感器, 对每种类型进行聚合
        List<Sensor> sensors = sensorMapper.selectList(null);
        Set<String> sensorTypes = new HashSet<>();
        for (Sensor sensor : sensors) {
            if (sensor.getType() != null) {
                sensorTypes.add(sensor.getType().getCode());
            }
        }

        int aggCount = 0;
        for (String sensorType : sensorTypes) {
            try {
                // Flux 查询: 计算上一小时的均值/最大值/最小值
                String flux = String.format(
                        "from(bucket: \"sensor_data\")\n" +
                        "  |> range(start: %s, stop: %s)\n" +
                        "  |> filter(fn: (r) => r._measurement == \"sensor_reading\")\n" +
                        "  |> filter(fn: (r) => r.sensor_type == \"%s\")\n" +
                        "  |> aggregateWindow(every: 1h, fn: mean)\n" +
                        "  |> group(columns: [\"farm_id\", \"field_id\", \"sensor_id\"])\n" +
                        "  |> yield(name: \"avg\")",
                        startInstant, stopInstant, sensorType);

                List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
                try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
                    for (FluxTable table : tables) {
                        for (FluxRecord record : table.getRecords()) {
                            String farmId = (String) record.getValueByKey("farm_id");
                            String sensorId = (String) record.getValueByKey("sensor_id");
                            Object value = record.getValue();

                            if (farmId != null && value != null) {
                                Point point = Point.measurement(AGG_MEASUREMENT)
                                        .addTag("farm_id", farmId)
                                        .addTag("sensor_id", sensorId != null ? sensorId : "unknown")
                                        .addTag("sensor_type", sensorType)
                                        .addField("avg_value", ((Number) value).doubleValue())
                                        .time(Instant.from(record.getTime()), WritePrecision.MS);
                                writeApi.writePoint(point);
                                aggCount++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("聚合传感器类型 {} 失败: {}", sensorType, e.getMessage());
            }
        }

        log.info("=== 每小时数据聚合完成: 聚合 {} 条数据点 ===", aggCount);
    }

    @Override
    public DashboardVO getDashboard(DashboardQueryDTO query) {
        Long farmId = query.getFarmId();
        log.info("生成看板数据: farmId={}", farmId);

        DashboardVO vo = new DashboardVO();
        vo.setFarmId(farmId);
        vo.setGenerateTime(LocalDateTime.now());

        // 1. 传感器在线统计
        List<Sensor> sensors = sensorMapper.selectByFarmId(farmId);
        int online = 0, offline = 0;
        for (Sensor s : sensors) {
            if (s.getOnlineStatus() != null && s.getOnlineStatus() == 1) {
                online++;
            } else {
                offline++;
            }
        }
        vo.setOnlineSensorCount(online);
        vo.setOfflineSensorCount(offline);

        // 2. 今日告警数
        vo.setTodayAlarmCount(alarmService.countTodayAlarms(farmId));

        // 3. 本周灌溉统计
        Map<String, Object> irrigationStats = irrigationService.getWeeklyStats(farmId);
        vo.setWeekIrrigationCount(extractInt(irrigationStats, "count"));
        vo.setWeekWaterUsage(extractDouble(irrigationStats, "totalWater"));

        // 4. 今日病害识别次数
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        vo.setTodayDiseaseCount(diseaseRecordMapper.countToday(farmId, startOfDay, now));

        // 5. 最新传感器数据
        List<SensorDataVO> latestData = sensorDataService.getFarmLatestData(farmId);
        vo.setLatestSensorData(latestData);

        // 6. 24小时趋势数据
        vo.setTrend24h(get24HourTrendData(farmId));

        // 7. 最近告警
        List<Alarm> recentAlarms = alarmService.getRecentAlarms(farmId, 10);
        vo.setRecentAlarms(convertAlarmSummary(recentAlarms));

        return vo;
    }

    @Override
    public Map<String, Object> getSensorTrend(Long farmId, String sensorType, String timeRange) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime;

        // 支持的时间范围: 1h, 6h, 24h, 7d, 30d
        startTime = switch (timeRange) {
            case "1h" -> endTime.minusHours(1);
            case "6h" -> endTime.minusHours(6);
            case "7d" -> endTime.minusDays(7);
            case "30d" -> endTime.minusDays(30);
            default -> endTime.minusHours(24);
        };

        return sensorDataService.getHistoryTrend(farmId, sensorType, startTime, endTime);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取24小时趋势数据 (多传感器类型)
     */
    private List<DashboardVO.TrendPoint> get24HourTrendData(Long farmId) {
        List<DashboardVO.TrendPoint> points = new ArrayList<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);

        try {
            // 查询温度、湿度、土壤湿度、CO2 的24小时趋势
            Map<String, Object> tempTrend = sensorDataService.getHistoryTrend(farmId, "temperature", startTime, endTime);
            Map<String, Object> humTrend = sensorDataService.getHistoryTrend(farmId, "humidity", startTime, endTime);
            Map<String, Object> soilTrend = sensorDataService.getHistoryTrend(farmId, "soil_moisture", startTime, endTime);
            Map<String, Object> co2Trend = sensorDataService.getHistoryTrend(farmId, "co2", startTime, endTime);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tempPoints = (List<Map<String, Object>>) tempTrend.get("points");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> humPoints = (List<Map<String, Object>>) humTrend.get("points");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> soilPoints = (List<Map<String, Object>>) soilTrend.get("points");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> co2Points = (List<Map<String, Object>>) co2Trend.get("points");

            // 按时间对齐合并 (简化: 直接遍历温度数据点, 匹配其他数据)
            int size = Math.min(Math.min(tempPoints.size(), humPoints.size()),
                    Math.min(soilPoints.size(), co2Points.size()));
            for (int i = 0; i < size; i++) {
                DashboardVO.TrendPoint point = new DashboardVO.TrendPoint();
                point.setTime((String) tempPoints.get(i).get("time"));
                point.setTemperature(extractDouble(tempPoints.get(i), "value"));
                point.setHumidity(extractDouble(humPoints.get(i), "value"));
                point.setSoilMoisture(extractDouble(soilPoints.get(i), "value"));
                point.setCo2(extractDouble(co2Points.get(i), "value"));
                points.add(point);
            }
        } catch (Exception e) {
            log.error("获取24小时趋势数据失败: {}", e.getMessage());
        }

        return points;
    }

    /**
     * 将告警列表转换为看板告警摘要
     */
    private List<DashboardVO.AlarmSummary> convertAlarmSummary(List<Alarm> alarms) {
        List<DashboardVO.AlarmSummary> result = new ArrayList<>();
        for (Alarm alarm : alarms) {
            DashboardVO.AlarmSummary summary = new DashboardVO.AlarmSummary();
            summary.setLevel(alarm.getLevel() != null ? alarm.getLevel().getDesc() : "未知");
            summary.setTitle(alarm.getTitle());
            summary.setContent(alarm.getContent());
            summary.setAlarmTime(alarm.getAlarmTime() != null ? alarm.getAlarmTime().format(FMT) : "");
            result.add(summary);
        }
        return result;
    }

    /**
     * 从 Map 中安全提取 Integer
     */
    private Integer extractInt(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return 0;
        return ((Number) map.get(key)).intValue();
    }

    /**
     * 从 Map 中安全提取 Double
     */
    private Double extractDouble(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return 0.0;
        return ((Number) map.get(key)).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private Double extractDouble(Map<String, Object> map, String key, Double defaultValue) {
        if (map == null || map.get(key) == null) return defaultValue;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultValue;
    }
}
