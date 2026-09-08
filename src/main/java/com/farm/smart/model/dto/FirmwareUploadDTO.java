package com.farm.smart.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 固件上传 DTO
 *
 * @author Smart Farm Team
 */
@Data
public class FirmwareUploadDTO implements Serializable {

    /** 设备类型: ESP32 / RPI */
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    /** 固件版本号 (如 1.2.0) */
    @NotBlank(message = "固件版本号不能为空")
    private String version;

    /** 固件文件下载URL */
    @NotBlank(message = "固件文件URL不能为空")
    private String fileUrl;

    /** 固件文件大小(字节) */
    private Long fileSize;

    /** MD5校验和 */
    private String checksumMd5;

    /** 固件描述/更新说明 */
    private String description;
}
