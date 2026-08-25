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
 * 系统用户实体
 * 对应后台登录用户, 采用 RBAC 分级权限管理
 *
 * @author Smart Farm Team
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 (登录账号, 唯一) */
    private String username;

    /** 密码 (BCrypt 加密存储) */
    private String password;

    /** 角色 (对应 RoleEnum 的 code, 如 SUPER_ADMIN / SYSTEM_ADMIN / USER) */
    private String role;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记: 0-未删除, 1-已删除 */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;
}
