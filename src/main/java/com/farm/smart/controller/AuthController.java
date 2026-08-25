package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.LoginDTO;
import com.farm.smart.model.vo.LoginVO;
import com.farm.smart.model.vo.UserVO;
import com.farm.smart.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>
 * 提供登录 (签发 JWT) 与当前用户信息查询接口。
 * 该路径 /auth/** 被认证拦截器排除, 登录无需 token;
 * /auth/current 内部手动解析 token 获取当前用户。
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录与当前用户信息")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "用户登录 (返回 JWT token)")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户信息")
    public Result<UserVO> current() {
        return Result.success(userService.getCurrentUser());
    }
}
