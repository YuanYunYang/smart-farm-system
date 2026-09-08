package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备 OTA 升级进度实体
 * 记录单个设备的固件升级过程
 *
 * @author Smart Farm Team
 */
@Data
@TableName("ota_device_progress")
public class OtaDeviceProgress implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 关联升级任务ID */
    private Long taskId;

    /** 设备ID (对应 sensor.sensor_code) */
    private String deviceId;

    /** 设备类型: ESP32 / RPI */
    private String deviceType;

    /** 升级状态: PENDING-等待 / DOWNLOADING-下载中 / INSTALLING-安装中 / SUCCESS-成功 / FAILED-失败 */
    private String status;

    /** 当前固件版本 */
    private String currentVersion;

    /** 目标固件版本 */
    private String targetVersion;

    /** 错误信息 (失败时记录) */
    private String errorMsg;

    /** 升级完成时间 */
    private LocalDateTime upgradeTime;
}
