package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.IrrigationRuleDTO;
import com.farm.smart.model.entity.IrrigationLog;
import com.farm.smart.model.entity.IrrigationRule;
import com.farm.smart.service.IrrigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 灌溉控制控制器
 * 手动灌溉控制 + 灌溉规则管理 + 灌溉日志查询
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/api/irrigation")
@RequiredArgsConstructor
@Tag(name = "灌溉控制", description = "手动灌溉、灌溉规则管理与日志查询")
public class IrrigationController {

    private final IrrigationService irrigationService;

    // ==================== 手动灌溉 ====================

    @PostMapping("/trigger")
    @Operation(summary = "手动触发灌溉")
    public Result<IrrigationLog> triggerIrrigation(
            @RequestParam Long fieldId,
            @RequestParam(defaultValue = "300") Integer durationSeconds) {
        return Result.success(irrigationService.manualTrigger(fieldId, durationSeconds));
    }

    @GetMapping("/logs")
    @Operation(summary = "查询灌溉日志")
    public Result<List<IrrigationLog>> listLogs(
            @RequestParam(required = false) Long farmId,
            @RequestParam(required = false) Long fieldId) {
        return Result.success(irrigationService.listLogs(farmId, fieldId));
    }

    @GetMapping("/stats/weekly/{farmId}")
    @Operation(summary = "查询本周灌溉统计")
    public Result<Map<String, Object>> getWeeklyStats(@PathVariable Long farmId) {
        return Result.success(irrigationService.getWeeklyStats(farmId));
    }

    // ==================== 灌溉规则管理 ====================

    @GetMapping("/rules/{farmId}")
    @Operation(summary = "查询农场灌溉规则列表")
    public Result<List<IrrigationRule>> listRules(@PathVariable Long farmId) {
        return Result.success(irrigationService.listRules(farmId));
    }

    @PostMapping("/rules")
    @Operation(summary = "创建灌溉规则")
    public Result<IrrigationRule> createRule(@RequestBody IrrigationRuleDTO dto) {
        return Result.success(irrigationService.createRule(dto));
    }

    @PutMapping("/rules")
    @Operation(summary = "更新灌溉规则")
    public Result<IrrigationRule> updateRule(@RequestBody IrrigationRule rule) {
        return Result.success(irrigationService.updateRule(rule));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除灌溉规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        irrigationService.deleteRule(id);
        return Result.success();
    }
}
