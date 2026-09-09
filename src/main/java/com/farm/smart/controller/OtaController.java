package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.FirmwareUploadDTO;
import com.farm.smart.model.dto.OtaTaskCreateDTO;
import com.farm.smart.model.entity.Firmware;
import com.farm.smart.model.entity.OtaDeviceProgress;
import com.farm.smart.model.entity.OtaTask;
import com.farm.smart.service.OtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OTA 固件升级控制器
 * <p>
 * 管理固件包上传/发布、升级任务创建/查询/取消
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/v1/api/ota")
@RequiredArgsConstructor
@Tag(name = "OTA固件升级", description = "固件管理与设备升级任务")
public class OtaController {

    private final OtaService otaService;

    /**
     * 上传固件
     */
    @PostMapping("/firmware")
    @Operation(summary = "上传固件包")
    public Result<Firmware> uploadFirmware(@Valid @RequestBody FirmwareUploadDTO dto) {
        return Result.success(otaService.uploadFirmware(dto));
    }

    /**
     * 固件列表
     */
    @GetMapping("/firmware/list")
    @Operation(summary = "查询固件列表")
    public Result<List<Firmware>> listFirmware() {
        return Result.success(otaService.list());
    }

    /**
     * 发布固件
     */
    @PostMapping("/firmware/{firmwareId}/publish")
    @Operation(summary = "发布固件 (DRAFT -> PUBLISHED)")
    public Result<Void> publishFirmware(@PathVariable Long firmwareId) {
        if (otaService.publishFirmware(firmwareId)) {
            return Result.success();
        }
        return Result.error(500, "固件发布失败");
    }

    /**
     * 创建升级任务
     */
    @PostMapping("/task")
    @Operation(summary = "创建 OTA 升级任务")
    public Result<OtaTask> createOtaTask(@Valid @RequestBody OtaTaskCreateDTO dto) {
        return Result.success(otaService.createOtaTask(dto));
    }

    /**
     * 任务详情
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询升级任务详情 (含设备进度)")
    public Result<Map<String, Object>> getTaskDetail(@PathVariable Long taskId) {
        OtaTask task = otaService.getTaskDetail(taskId);
        if (task == null) {
            return Result.error(404, "升级任务不存在");
        }
        List<OtaDeviceProgress> progressList = otaService.getDeviceProgress(taskId);

        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("deviceProgress", progressList);
        return Result.success(data);
    }

    /**
     * 任务列表
     */
    @GetMapping("/task/list")
    @Operation(summary = "查询升级任务列表")
    public Result<List<OtaTask>> listTasks() {
        return Result.success(otaService.listTasks());
    }

    /**
     * 取消任务
     */
    @PostMapping("/task/{taskId}/cancel")
    @Operation(summary = "取消升级任务")
    public Result<Void> cancelTask(@PathVariable Long taskId) {
        if (otaService.cancelTask(taskId)) {
            return Result.success();
        }
        return Result.error(500, "取消任务失败");
    }
}
