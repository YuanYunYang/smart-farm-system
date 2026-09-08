package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户实体
 * <p>
 * SaaS 多租户架构核心表，每个租户代表一个独立农场组织/企业。
 * 租户下的所有数据（农场、地块、传感器、告警、用户）通过 tenant_id 隔离。
 *
 * @author Smart Farm Team
 */
@Data
@TableName("tenant")
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户编码（唯一，用于 JWT 中标识租户） */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 租户类型：TRIAL-试用 / STANDARD-标准版 / ENTERPRISE-企业版 */
    private String tenantType;

    /** 租户状态：0-禁用 1-启用 */
    private Integer status;

    /** 传感器数量上限（根据套餐分配） */
    private Integer sensorLimit;

    /** 用户数量上限 */
    private Integer userLimit;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 到期时间 */
    private LocalDateTime expireTime;

    /** 创建时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
