package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.AlarmResult;
import com.farm.smart.model.dto.SensorDataFact;
import com.farm.smart.model.entity.AlarmRule;
import com.farm.smart.service.RuleEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则控制器
 * <p>
 * 管理 Drools 规则: CRUD + 测试评估 + 热加载
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/api/rule")
@RequiredArgsConstructor
@Tag(name = "告警规则引擎", description = "Drools 规则 CRUD、测试与重载")
public class RuleController {

    private final RuleEngineService ruleEngineService;

    /**
     * 查询规则列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询告警规则列表")
    public Result<List<AlarmRule>> listRules() {
        return Result.success(ruleEngineService.list());
    }

    /**
     * 查询规则详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询规则详情")
    public Result<AlarmRule> getRule(@PathVariable Long id) {
        return Result.success(ruleEngineService.getById(id));
    }

    /**
     * 创建规则
     */
    @PostMapping
    @Operation(summary = "创建告警规则")
    public Result<AlarmRule> createRule(@RequestBody AlarmRule rule) {
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        ruleEngineService.save(rule);
        // 新增规则后重载
        ruleEngineService.reloadRules();
        return Result.success(rule);
    }

    /**
     * 更新规则
     */
    @PutMapping
    @Operation(summary = "更新告警规则")
    public Result<Void> updateRule(@RequestBody AlarmRule rule) {
        ruleEngineService.updateById(rule);
        // 更新规则后重载
        ruleEngineService.reloadRules();
        return Result.success();
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除告警规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        ruleEngineService.removeById(id);
        // 删除规则后重载
        ruleEngineService.reloadRules();
        return Result.success();
    }

    /**
     * 启用/禁用规则
     */
    @PutMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用告警规则")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestParam boolean enabled) {
        ruleEngineService.toggleRule(id, enabled);
        return Result.success();
    }

    /**
     * 测试规则评估
     */
    @PostMapping("/test")
    @Operation(summary = "测试规则评估", description = "输入传感器数据事实, 返回匹配的告警结果")
    public Result<List<AlarmResult>> testRule(@RequestBody SensorDataFact fact) {
        return Result.success(ruleEngineService.evaluateRule(fact));
    }

    /**
     * 重新加载规则
     */
    @PostMapping("/reload")
    @Operation(summary = "重新加载规则", description = "从数据库和默认规则文件重新加载所有规则")
    public Result<Void> reloadRules() {
        ruleEngineService.reloadRules();
        return Result.success();
    }
}
