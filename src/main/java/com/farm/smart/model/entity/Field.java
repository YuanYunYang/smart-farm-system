package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 地块实体 (一个地块绑定多个传感器和灌溉设备)
 *
 * @author Smart Farm Team
 */
@Data
@TableName("field")
public class Field implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 所属农场ID */
    private Long farmId;

    /** 地块名称 (如: A区温室1号) */
    private String name;

    /** 种植作物 */
    private String crop;

    /** 地块面积 (亩) */
    private Double area;

    /** 土壤类型 */
    private String soilType;

    /** 灌溉阀门设备ID (对应树莓派控制的继电器编号) */
    private String valveDeviceId;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
