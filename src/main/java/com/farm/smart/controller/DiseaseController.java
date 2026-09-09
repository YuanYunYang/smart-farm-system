package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.DiseaseDetectDTO;
import com.farm.smart.model.entity.DiseaseRecord;
import com.farm.smart.model.vo.DiseaseResultVO;
import com.farm.smart.service.DiseaseDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 病虫害识别控制器
 * 上传图片进行 AI 病虫害识别 + 历史记录查询
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/v1/api/disease")
@RequiredArgsConstructor
@Tag(name = "病虫害识别", description = "AI病虫害识别与历史记录")
public class DiseaseController {

    private final DiseaseDetectionService diseaseDetectionService;

    @PostMapping("/detect")
    @Operation(summary = "单张图片病虫害识别", description = "上传Base64图片进行AI病虫害识别")
    public Result<DiseaseResultVO> detectDisease(@RequestBody DiseaseDetectDTO dto) {
        return Result.success(diseaseDetectionService.detectDisease(dto));
    }

    @PostMapping("/batch-detect")
    @Operation(summary = "批量病虫害识别")
    public Result<List<DiseaseResultVO>> batchDetect(@RequestBody List<DiseaseDetectDTO> dtos) {
        return Result.success(diseaseDetectionService.batchDetect(dtos));
    }

    @GetMapping("/history")
    @Operation(summary = "查询病虫害识别历史")
    public Result<List<DiseaseRecord>> getHistory(
            @RequestParam Long farmId,
            @RequestParam(required = false) Long fieldId) {
        return Result.success(diseaseDetectionService.getHistory(farmId, fieldId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询病害识别记录详情")
    public Result<DiseaseRecord> getRecord(@PathVariable Long id) {
        return Result.success(diseaseDetectionService.getRecordById(id));
    }
}
