package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.DashboardQueryDTO;
import com.farm.smart.model.vo.DashboardVO;
import com.farm.smart.service.DataAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据看板控制器
 * 聚合实时传感器值、趋势数据、灌溉统计、告警统计等看板数据
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/v1/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "数据看板", description = "看板聚合数据与趋势查询")
public class DashboardController {

    private final DataAggregationService dataAggregationService;

    @PostMapping("/overview")
    @Operation(summary = "获取看板聚合数据", description = "包含: 实时传感器值 + 24h趋势 + 灌溉统计 + 告警统计 + 病害统计")
    public Result<DashboardVO> getDashboard(@RequestBody DashboardQueryDTO query) {
        return Result.success(dataAggregationService.getDashboard(query));
    }

    @GetMapping("/trend")
    @Operation(summary = "查询传感器趋势数据", description = "支持时间范围: 1h, 6h, 24h, 7d, 30d")
    public Result<Map<String, Object>> getSensorTrend(
            @RequestParam Long farmId,
            @RequestParam String sensorType,
            @RequestParam(defaultValue = "24h") String timeRange) {
        return Result.success(dataAggregationService.getSensorTrend(farmId, sensorType, timeRange));
    }
}
