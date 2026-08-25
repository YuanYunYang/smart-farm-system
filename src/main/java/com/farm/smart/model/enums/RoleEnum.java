package com.farm.smart.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统用户角色枚举
 * <p>
 * 角色分级:
 * <ul>
 *   <li>SUPER_ADMIN  - 超级管理员, 系统最高权限, 可创建系统管理员</li>
 *   <li>SYSTEM_ADMIN - 系统管理员, 可创建普通用户</li>
 *   <li>USER         - 普通用户, 不能创建任何用户</li>
 * </ul>
 * 角色等级采用数值比较: 数值越大权限越高。
 *
 * @author Smart Farm Team
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    /** 普通用户 */
    USER("USER", "普通用户", 1),

    /** 系统管理员 */
    SYSTEM_ADMIN("SYSTEM_ADMIN", "系统管理员", 2),

    /** 超级管理员 */
    SUPER_ADMIN("SUPER_ADMIN", "超级管理员", 3);

    /** 角色编码 (存入数据库 sys_user.role 字段) */
    private final String code;

    /** 角色描述 */
    private final String description;

    /** 角色等级 (数值越大权限越高, 用于 RBAC 比较) */
    private final Integer level;

    /**
     * 根据 code 获取枚举
     */
    public static RoleEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (RoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 判断当前角色是否具有操作目标角色的权限
     * 仅当操作者等级严格高于目标时允许 (不能操作同级或更高级)
     */
    public boolean canOperate(RoleEnum target) {
        if (target == null) {
            return true;
        }
        return this.level > target.level;
    }
}
