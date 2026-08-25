package com.farm.smart.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 病虫害识别请求 DTO
 * 前端上传图片进行 AI 识别
 *
 * @author Smart Farm Team
 */
@Data
public class DiseaseDetectDTO implements Serializable {

    /** 农场ID */
    @NotNull(message = "农场ID不能为空")
    private Long farmId;

    /** 地块ID */
    @NotNull(message = "地块ID不能为空")
    private Long fieldId;

    /**
     * Base64 编码的图片数据
     * 前端将图片转为 Base64 后上传 (去掉 data:image/xxx;base64, 前缀)
     */
    @NotNull(message = "图片数据不能为空")
    private String imageBase64;

    /** 识别来源: local / cloud (不传则使用配置默认值) */
    private String source;
}
