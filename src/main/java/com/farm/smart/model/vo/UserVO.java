package com.farm.smart.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息 VO (不返回密码)
 *
 * @author Smart Farm Team
 */
@Data
public class UserVO implements Serializable {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 角色 */
    private String role;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
