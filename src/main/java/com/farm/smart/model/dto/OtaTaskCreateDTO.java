package com.farm.smart.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * OTA 升级任务创建 DTO
 *
 * @author Smart Farm Team
 */
@Data
public class OtaTaskCreateDTO implements Serializable {

    /** 关联固件ID */
    @NotNull(message = "固件ID不能为空")
    private Long firmwareId;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    /** 目标类型: ALL-全部设备 / SELECTED-指定设备 / BY_FARM-按农场 */
    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    /** 目标农场ID (BY_FARM 时使用) */
    private Long targetFarmId;

    /** 目标设备ID列表 (SELECTED 时使用, 逗号分隔) */
    private String targetDeviceIds;

    /** 租户ID (由系统从上下文注入, 前端可不传) */
    private Long tenantId;
}
