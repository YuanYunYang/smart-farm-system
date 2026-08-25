package com.farm.smart.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 病虫害识别结果 VO
 *
 * @author Smart Farm Team
 */
@Data
public class DiseaseResultVO implements Serializable {

    /** 识别记录ID */
    private Long recordId;

    /** 病害名称 */
    private String diseaseName;

    /** 置信度 (0-1) */
    private Double confidence;

    /** 置信度百分比 (方便前端展示, 如 "95.2%") */
    private String confidencePercent;

    /** AI 模型版本 */
    private String modelVersion;

    /** 建议处理方案 */
    private String suggestion;

    /** 识别来源 */
    private String source;

    /**
     * 多目标识别结果 (一张图识别到多个病害时)
     */
    private List<DetectionItem> detections;

    /**
     * 单个检测目标
     */
    @Data
    public static class DetectionItem implements Serializable {
        /** 病害名称 */
        private String className;
        /** 置信度 */
        private Double confidence;
        /** 边界框 [x1, y1, x2, y2] (归一化坐标) */
        private List<Double> bbox;
        /** 建议处理方案 */
        private String suggestion;
    }
}
