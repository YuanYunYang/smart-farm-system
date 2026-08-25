package com.farm.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.smart.model.dto.SensorDataDTO;
import com.farm.smart.model.entity.Sensor;
import com.farm.smart.model.enums.SensorType;
import com.farm.smart.model.vo.SensorDataVO;
import com.farm.smart.repository.SensorMapper;
import com.farm.smart.service.AlarmService;
import com.farm.smart.service.SensorDataService;
import com.farm.smart.websocket.FarmWebSocketHandler;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 传感器数据服务实现
 * <p>
 * 数据流: MQTT消息 → 解析SensorDataDTO → InfluxDB(时序) + Redis(最新值缓存) + WebSocket(实时推送) + 阈值告警检查
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorMapper sensorMapper;
    private final InfluxDBClient influxDBClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FarmWebSocketHandler webSocketHandler;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** InfluxDB measurement 名称 */
    private static final String MEASUREMENT = "sensor_reading";

    /** Redis 最新值缓存 Key 前缀 */
    private static final String LATEST_CACHE_PREFIX = "sensor:latest:";

    /** 时间格式 */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @SuppressWarnings("unchecked")
    public void processSensorData(SensorDataDTO dto) {
        log.info("处理传感器数据: farmId={}, sensorId={}, timestamp={}",
                dto.getFarmId(), dto.getSensorId(), dto.getTimestamp());

        // 1. 查询传感器配置 (获取阈值)
        Sensor sensor = getSensorByCode(dto.getSensorId());
        if (sensor == null) {
            log.warn("未找到传感器配置: sensorCode={}", dto.getSensorId());
            return;
        }

        // 确保 fieldId 不为空
        if (dto.getFieldId() == null) {
            dto.setFieldId(sensor.getFieldId());
        }

        // 2. 写入 InfluxDB (时序存储)
        writeToInfluxDB(dto);

        // 3. 更新 Redis 最新值缓存
        updateLatestCache(sensor, dto);

        // 4. 更新传感器在线状态和心跳时间
        sensorMapper.updateOnlineStatus(sensor.getId(), 1);
        sensor.setOnlineStatus(1);
        sensor.setLastHeartbeat(LocalDateTime.now());

        // 5. WebSocket 推送给看板
        pushToWebSocket(sensor, dto);

        // 6. 检查阈值, 触发告警
        if (dto.getData() != null) {
            for (Map.Entry<String, Double> entry : dto.getData().entrySet()) {
                SensorType sensorType = SensorType.getByCode(entry.getKey());
                if (sensorType != null) {
                    alarmService.checkSensorThreshold(
                            dto.getFarmId(),
                            dto.getFieldId(),
                            sensor.getId(),
                            sensorType.getName(),
                            entry.getValue(),
                            sensor.getThresholdMax(),
                            sensor.getThresholdMin());
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public SensorDataVO getLatestData(Long sensorId) {
        Sensor sensor = sensorMapper.selectById(sensorId);
        if (sensor == null) {
            throw new IllegalArgumentException("传感器不存在: id=" + sensorId);
        }

        // 从 Redis 读取最新值缓存
        String cacheKey = LATEST_CACHE_PREFIX + sensor.getSensorCode();
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);

        SensorDataVO vo = new SensorDataVO();
        vo.setSensorId(sensor.getId());
        vo.setSensorCode(sensor.getSensorCode());
        vo.setSensorName(sensor.getName());
        vo.setType(sensor.getType());
        vo.setUnit(sensor.getType() != null ? sensor.getType().getUnit() : null);
        vo.setOnlineStatus(sensor.getOnlineStatus());

        if (cached != null) {
            vo.setValue(cached.get("value") != null ? Double.valueOf(cached.get("value").toString()) : null);
            vo.setOverThreshold(Boolean.valueOf(cached.get("overThreshold").toString()));
            String timeStr = cached.get("time").toString();
            vo.setDataTime(LocalDateTime.parse(timeStr, FMT));
        }
        return vo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SensorDataVO> getFarmLatestData(Long farmId) {
        // 查询农场所有传感器
        List<Sensor> sensors = sensorMapper.selectByFarmId(farmId);
        List<SensorDataVO> result = new ArrayList<>();

        for (Sensor sensor : sensors) {
            SensorDataVO vo = getLatestData(sensor.getId());
            result.add(vo);
        }
        return result;
    }

    @Override
    public Map<String, Object> getHistoryTrend(Long farmId, String sensorType,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        log.info("查询传感器历史趋势: farmId={}, type={}, start={}, end={}",
                farmId, sensorType, startTime, endTime);

        // 构建 Flux 查询语句
        String flux = String.format(
                "from(bucket: \"sensor_data\")\n" +
                "  |> range(start: %s, stop: %s)\n" +
                "  |> filter(fn: (r) => r._measurement == \"%s\")\n" +
                "  |> filter(fn: (r) => r.farm_id == \"%d\")\n" +
                "  |> filter(fn: (r) => r.sensor_type == \"%s\")\n" +
                "  |> aggregateWindow(every: 5m, fn: mean, createEmpty: false)\n" +
                "  |> sort(columns: [\"_time\"])",
                formatInstant(startTime), formatInstant(endTime),
                MEASUREMENT, farmId, sensorType);

        return executeFluxQuery(flux, sensorType);
    }

    @Override
    public Map<String, Object> get24HourTrend(Long farmId) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);
        return getHistoryTrend(farmId, "soil_moisture", startTime, endTime);
    }

    @Override
    public List<Sensor> listSensors(Long farmId) {
        return sensorMapper.selectByFarmId(farmId);
    }

    @Override
    public void updateSensorStatus(String sensorCode, Integer status) {
        Sensor sensor = getSensorByCode(sensorCode);
        if (sensor != null) {
            sensorMapper.updateOnlineStatus(sensor.getId(), status);
            log.info("更新传感器状态: code={}, status={}", sensorCode, status == 1 ? "在线" : "离线");

            // 如果设备变为离线, 触发告警
            if (status == 0) {
                alarmService.deviceOfflineAlarm(sensor.getFarmId(), sensor.getId(), sensorCode);
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 写入 InfluxDB 时序数据
     */
    private void writeToInfluxDB(SensorDataDTO dto) {
        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            // 为每个传感器类型创建一个数据点
            if (dto.getData() != null) {
                for (Map.Entry<String, Double> entry : dto.getData().entrySet()) {
                    Point point = Point.measurement(MEASUREMENT)
                            .addTag("farm_id", String.valueOf(dto.getFarmId()))
                            .addTag("field_id", String.valueOf(dto.getFieldId()))
                            .addTag("sensor_id", dto.getSensorId())
                            .addTag("sensor_type", entry.getKey())
                            .addField("value", entry.getValue())
                            .time(Instant.ofEpochMilli(dto.getTimestamp()), WritePrecision.MS);

                    writeApi.writePoint(point);
                }
            }
            log.debug("传感器数据写入 InfluxDB: sensorId={}", dto.getSensorId());
        } catch (Exception e) {
            log.error("InfluxDB 写入失败: sensorId={}, error={}", dto.getSensorId(), e.getMessage());
        }
    }

    /**
     * 更新 Redis 最新值缓存
     */
    private void updateLatestCache(Sensor sensor, SensorDataDTO dto) {
        if (dto.getData() == null || dto.getData().isEmpty()) {
            return;
        }

        // 为每个传感器类型缓存最新值
        for (Map.Entry<String, Double> entry : dto.getData().entrySet()) {
            String cacheKey = LATEST_CACHE_PREFIX + sensor.getSensorCode() + ":" + entry.getKey();

            Map<String, Object> cacheValue = new HashMap<>();
            cacheValue.put("value", entry.getValue());
            cacheValue.put("sensorType", entry.getKey());
            cacheValue.put("unit", SensorType.getByCode(entry.getKey()) != null
                    ? SensorType.getByCode(entry.getKey()).getUnit() : "");
            cacheValue.put("overThreshold", checkThreshold(sensor, entry.getValue()));
            cacheValue.put("time", LocalDateTime.now().format(FMT));

            // 缓存 24 小时过期
            redisTemplate.opsForValue().set(cacheKey, cacheValue, 24, java.util.concurrent.TimeUnit.HOURS);
        }
    }

    /**
     * 检查数值是否超阈值
     */
    private boolean checkThreshold(Sensor sensor, Double value) {
        if (sensor.getThresholdMax() != null && value > sensor.getThresholdMax()) {
            return true;
        }
        return sensor.getThresholdMin() != null && value < sensor.getThresholdMin();
    }

    /**
     * WebSocket 推送实时传感器数据
     */
    private void pushToWebSocket(Sensor sensor, SensorDataDTO dto) {
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "SENSOR_DATA");
            wsMessage.put("farmId", dto.getFarmId());
            wsMessage.put("fieldId", dto.getFieldId());
            wsMessage.put("sensorId", sensor.getSensorCode());
            wsMessage.put("sensorName", sensor.getName());
            wsMessage.put("data", dto.getData());
            wsMessage.put("timestamp", dto.getTimestamp());
            wsMessage.put("battery", dto.getBattery());
            wsMessage.put("serverTime", LocalDateTime.now().format(FMT));

            String json = objectMapper.writeValueAsString(wsMessage);
            webSocketHandler.sendToFarm(dto.getFarmId(), json);
        } catch (Exception e) {
            log.error("WebSocket 推送失败: {}", e.getMessage());
        }
    }

    /**
     * 根据传感器编号查询传感器配置
     */
    private Sensor getSensorByCode(String sensorCode) {
        return sensorMapper.selectOne(new LambdaQueryWrapper<Sensor>()
                .eq(Sensor::getSensorCode, sensorCode));
    }

    /**
     * 执行 Flux 查询并返回结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeFluxQuery(String flux, String sensorType) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> points = new ArrayList<>();

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Map<String, Object> point = new HashMap<>();
                    Instant time = record.getTime();
                    if (time != null) {
                        point.put("time", LocalDateTime.ofInstant(time, ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    }
                    point.put("value", record.getValue());
                    points.add(point);
                }
            }
        } catch (Exception e) {
            log.error("InfluxDB 查询失败: {}", e.getMessage(), e);
            // 查询失败时返回空列表, 不影响看板加载
            result.put("error", "查询失败: " + e.getMessage());
        }

        result.put("sensorType", sensorType);
        result.put("points", points);
        return result;
    }

    /**
     * 格式化 LocalDateTime 为 InfluxDB Flux 时间字面量
     */
    private String formatInstant(LocalDateTime ldt) {
        if (ldt == null) {
            return "now()";
        }
        Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
        return instant.toString();
    }
}
