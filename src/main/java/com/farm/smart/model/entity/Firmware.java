package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 固件包实体
 * 管理 ESP32/树莓派等边缘设备的固件版本
 *
 * @author Smart Farm Team
 */
@Data
@TableName("firmware")
public class Firmware implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 设备类型: ESP32 / RPI */
    private String deviceType;

    /** 固件版本号 (如 1.2.0) */
    private String version;

    /** 固件文件下载URL */
    private String fileUrl;

    /** 固件文件大小(字节) */
    private Long fileSize;

    /** MD5校验和 */
    private String checksumMd5;

    /** 状态: DRAFT-草稿 / PUBLISHED-已发布 / DEPRECATED-已废弃 */
    private String status;

    /** 固件描述/更新说明 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;
}
