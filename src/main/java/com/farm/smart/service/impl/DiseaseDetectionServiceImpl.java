package com.farm.smart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.smart.model.dto.DiseaseDetectDTO;
import com.farm.smart.model.entity.DiseaseRecord;
import com.farm.smart.model.vo.DiseaseResultVO;
import com.farm.smart.repository.DiseaseRecordMapper;
import com.farm.smart.service.DiseaseDetectionService;
import com.farm.smart.ai.DiseaseAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 病虫害识别服务实现
 * 调用 AI 服务进行病虫害检测, 记录结果到数据库
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiseaseDetectionServiceImpl implements DiseaseDetectionService {

    private final DiseaseAiClient diseaseAiClient;
    private final DiseaseRecordMapper diseaseRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiseaseResultVO detectDisease(DiseaseDetectDTO dto) {
        log.info("开始病虫害识别: farmId={}, fieldId={}", dto.getFarmId(), dto.getFieldId());

        // 调用 AI 识别服务
        DiseaseResultVO result = diseaseAiClient.detectDisease(
                dto.getImageBase64(), dto.getSource());

        // 记录到数据库
        DiseaseRecord record = new DiseaseRecord();
        record.setFarmId(dto.getFarmId());
        record.setFieldId(dto.getFieldId());
        record.setDiseaseName(result.getDiseaseName());
        record.setConfidence(result.getConfidence());
        record.setModelVersion(result.getModelVersion());
        record.setSuggestion(result.getSuggestion());
        record.setSource(result.getSource());
        record.setDetectTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());

        // 图片存储路径 (实际项目应保存到 OSS/MinIO, 此处记录 Base64 长度作为占位)
        record.setImagePath("uploaded/" + System.currentTimeMillis() + ".jpg");

        diseaseRecordMapper.insert(record);
        result.setRecordId(record.getId());

        log.info("病虫害识别完成: disease={}, confidence={}, recordId={}",
                result.getDiseaseName(), result.getConfidencePercent(), record.getId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DiseaseResultVO> batchDetect(List<DiseaseDetectDTO> dtos) {
        List<DiseaseResultVO> results = new ArrayList<>();
        for (DiseaseDetectDTO dto : dtos) {
            try {
                results.add(detectDisease(dto));
            } catch (Exception e) {
                log.error("批量识别中单条失败: farmId={}, error={}", dto.getFarmId(), e.getMessage());
                // 跳过失败项, 继续处理后续
            }
        }
        log.info("批量识别完成: 总数={}, 成功={}", dtos.size(), results.size());
        return results;
    }

    @Override
    public List<DiseaseRecord> getHistory(Long farmId, Long fieldId) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(30); // 最近30天
        return diseaseRecordMapper.selectHistory(farmId, fieldId, startTime, endTime);
    }

    @Override
    public DiseaseRecord getRecordById(Long id) {
        DiseaseRecord record = diseaseRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("病害识别记录不存在: id=" + id);
        }
        return record;
    }
}
