package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.entity.Sensor;
import com.farm.smart.model.vo.SensorDataVO;
import com.farm.smart.service.SensorDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 传感器数据控制器
 * 查询传感器设备列表 + 历史趋势数据
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/v1/api/sensor")
@RequiredArgsConstructor
@Tag(name = "传感器数据", description = "传感器设备列表与历史趋势数据查询")
public class SensorController {

    private final SensorDataService sensorDataService;

    @GetMapping("/list/{farmId}")
    @Operation(summary = "获取农场传感器设备列表")
    public Result<List<Sensor>> listSensors(@PathVariable Long farmId) {
        return Result.success(sensorDataService.listSensors(farmId));
    }

    @GetMapping("/latest/{sensorId}")
    @Operation(summary = "查询单个传感器最新数据")
    public Result<SensorDataVO> getLatestData(@PathVariable Long sensorId) {
        return Result.success(sensorDataService.getLatestData(sensorId));
    }

    @GetMapping("/latest/farm/{farmId}")
    @Operation(summary = "查询农场所有传感器最新数据")
    public Result<List<SensorDataVO>> getFarmLatestData(@PathVariable Long farmId) {
        return Result.success(sensorDataService.getFarmLatestData(farmId));
    }

    @GetMapping("/history")
    @Operation(summary = "查询传感器历史趋势数据 (InfluxDB)")
    public Result<Map<String, Object>> getHistoryTrend(
            @RequestParam Long farmId,
            @RequestParam String sensorType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(sensorDataService.getHistoryTrend(farmId, sensorType, startTime, endTime));
    }

    @GetMapping("/trend/24h/{farmId}")
    @Operation(summary = "查询24小时趋势数据")
    public Result<Map<String, Object>> get24HourTrend(@PathVariable Long farmId) {
        return Result.success(sensorDataService.get24HourTrend(farmId));
    }
}
