package com.farm.smart.service;

import com.farm.smart.model.dto.DiseaseDetectDTO;
import com.farm.smart.model.entity.DiseaseRecord;
import com.farm.smart.model.vo.DiseaseResultVO;

import java.util.List;

/**
 * 病虫害识别服务接口
 *
 * @author Smart Farm Team
 */
public interface DiseaseDetectionService {

    /**
     * 单张图片病虫害识别
     * 1. 调用 AI 服务 (本地 YOLOv8 或云端 API)
     * 2. 解析识别结果 (病害名称、置信度、处理建议)
     * 3. 记录到数据库, 关联地块
     *
     * @param dto 识别请求 (含 Base64 图片)
     * @return 识别结果
     */
    DiseaseResultVO detectDisease(DiseaseDetectDTO dto);

    /**
     * 批量病虫害识别
     *
     * @param dtos 识别请求列表
     * @return 识别结果列表
     */
    List<DiseaseResultVO> batchDetect(List<DiseaseDetectDTO> dtos);

    /**
     * 查询病害识别历史记录
     */
    List<DiseaseRecord> getHistory(Long farmId, Long fieldId);

    /**
     * 根据ID查询识别记录详情
     */
    DiseaseRecord getRecordById(Long id);
}
