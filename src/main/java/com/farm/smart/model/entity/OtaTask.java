package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OTA 升级任务实体
 * 批量管理设备固件升级
 *
 * @author Smart Farm Team
 */
@Data
@TableName("ota_task")
public class OtaTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 关联固件ID */
    private Long firmwareId;

    /** 任务名称 */
    private String taskName;

    /** 目标类型: ALL-全部设备 / SELECTED-指定设备 / BY_FARM-按农场 */
    private String targetType;

    /** 目标农场ID (BY_FARM 时使用) */
    private Long targetFarmId;

    /** 目标设备ID列表 (SELECTED 时使用, 逗号分隔) */
    private String targetDeviceIds;

    /** 任务状态: PENDING-待执行 / RUNNING-执行中 / COMPLETED-已完成 / CANCELLED-已取消 */
    private String status;

    /** 设备总数 */
    private Integer totalCount;

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
