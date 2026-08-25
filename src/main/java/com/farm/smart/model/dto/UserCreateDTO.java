package com.farm.smart.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建 DTO
 *
 * @author Smart Farm Team
 */
@Data
public class UserCreateDTO implements Serializable {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 (明文, 服务端 BCrypt 加密) */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 角色 (RoleEnum 的 code, 如 SYSTEM_ADMIN / USER) */
    @NotBlank(message = "角色不能为空")
    private String role;
}
