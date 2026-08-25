package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.entity.Alarm;
import com.farm.smart.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警控制器
 * 告警查询、处理、统计
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/api/alarm")
@RequiredArgsConstructor
@Tag(name = "告警管理", description = "告警查询与处理")
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping("/list")
    @Operation(summary = "查询告警列表")
    public Result<List<Alarm>> listAlarms(
            @RequestParam Long farmId,
            @RequestParam(required = false) Integer handleStatus) {
        return Result.success(alarmService.listAlarms(farmId, handleStatus));
    }

    @GetMapping("/recent/{farmId}")
    @Operation(summary = "查询最近告警 (看板用)")
    public Result<List<Alarm>> getRecentAlarms(
            @PathVariable Long farmId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(alarmService.getRecentAlarms(farmId, limit));
    }

    @GetMapping("/count/today/{farmId}")
    @Operation(summary = "查询今日告警数量")
    public Result<Integer> countTodayAlarms(@PathVariable Long farmId) {
        return Result.success(alarmService.countTodayAlarms(farmId));
    }

    @PutMapping("/handle/{id}")
    @Operation(summary = "处理告警")
    public Result<Void> handleAlarm(
            @PathVariable Long id,
            @RequestParam String handler,
            @RequestParam String remark) {
        alarmService.handleAlarm(id, handler, remark);
        return Result.success();
    }
}
