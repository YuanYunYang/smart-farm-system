package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.entity.Farm;
import com.farm.smart.model.entity.Field;
import com.farm.smart.service.FarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 农场/地块管理控制器
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/v1/api/farm")
@RequiredArgsConstructor
@Tag(name = "农场管理", description = "农场和地块的CRUD接口")
public class FarmController {

    private final FarmService farmService;

    // ==================== 农场管理 ====================

    @GetMapping("/list")
    @Operation(summary = "获取所有农场列表")
    public Result<List<Farm>> listFarms() {
        return Result.success(farmService.listFarms());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取农场详情")
    public Result<Farm> getFarm(@PathVariable Long id) {
        return Result.success(farmService.getFarmById(id));
    }

    @PostMapping
    @Operation(summary = "创建农场")
    public Result<Farm> createFarm(@RequestBody Farm farm) {
        return Result.success(farmService.createFarm(farm));
    }

    @PutMapping
    @Operation(summary = "更新农场信息")
    public Result<Farm> updateFarm(@RequestBody Farm farm) {
        return Result.success(farmService.updateFarm(farm));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除农场")
    public Result<Void> deleteFarm(@PathVariable Long id) {
        farmService.deleteFarm(id);
        return Result.success();
    }

    // ==================== 地块管理 ====================

    @GetMapping("/{farmId}/fields")
    @Operation(summary = "获取农场地块列表")
    public Result<List<Field>> listFields(@PathVariable Long farmId) {
        return Result.success(farmService.listFields(farmId));
    }

    @PostMapping("/field")
    @Operation(summary = "创建地块")
    public Result<Field> createField(@RequestBody Field field) {
        return Result.success(farmService.createField(field));
    }

    @PutMapping("/field")
    @Operation(summary = "更新地块信息")
    public Result<Field> updateField(@RequestBody Field field) {
        return Result.success(farmService.updateField(field));
    }

    @DeleteMapping("/field/{id}")
    @Operation(summary = "删除地块")
    public Result<Void> deleteField(@PathVariable Long id) {
        farmService.deleteField(id);
        return Result.success();
    }
}
