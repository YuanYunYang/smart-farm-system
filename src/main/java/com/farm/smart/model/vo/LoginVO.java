package com.farm.smart.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录成功响应 VO
 *
 * @author Smart Farm Team
 */
@Data
public class LoginVO implements Serializable {

    /** JWT token */
    private String token;

    /** 用户名 */
    private String username;

    /** 角色 */
    private String role;
}
