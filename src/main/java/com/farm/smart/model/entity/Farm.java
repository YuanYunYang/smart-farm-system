package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 农场实体 (一个农场包含多个地块)
 *
 * @author Smart Farm Team
 */
@Data
@TableName("farm")
public class Farm implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 农场名称 */
    private String name;

    /** 农场位置 (省市区/经纬度) */
    private String location;

    /** 农场面积 (亩) */
    private Double area;

    /** 负责人 */
    private String manager;

    /** 联系电话 */
    private String phone;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
