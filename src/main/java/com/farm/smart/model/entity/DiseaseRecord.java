package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 病害识别记录实体
 * 存储 AI 识别的病虫害结果, 关联地块
 *
 * @author Smart Farm Team
 */
@Data
@TableName("disease_record")
public class DiseaseRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 农场ID */
    private Long farmId;

    /** 地块ID */
    private Long fieldId;

    /** 识别的病害名称 (如: 番茄早疫病) */
    private String diseaseName;

    /** 置信度 (0-1) */
    private Double confidence;

    /** AI 模型版本 */
    private String modelVersion;

    /** 上传图片路径 */
    private String imagePath;

    /** 建议处理方案 */
    private String suggestion;

    /** 识别来源: local-本地YOLOv8, cloud-云端API */
    private String source;

    /** 识别时间 */
    private LocalDateTime detectTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
