package com.farm.smart.controller;

import com.farm.smart.common.Result;
import com.farm.smart.model.dto.UserCreateDTO;
import com.farm.smart.model.vo.UserVO;
import com.farm.smart.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器 (RBAC 分级)
 * <p>
 * 所有接口需要携带有效 JWT token (由 AuthInterceptor 校验)。
 * 创建/删除/启停操作均受角色分级权限控制。
 *
 * @author Smart Farm Team
 */
@RestController
@RequestMapping("/v1/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查与启停 (RBAC 分级)")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "创建用户 (受角色分级限制)")
    public Result<UserVO> createUser(@RequestBody @Valid UserCreateDTO dto) {
        return Result.success(userService.createUser(dto));
    }

    @GetMapping("/list")
    @Operation(summary = "查询用户列表")
    public Result<List<UserVO>> listUsers() {
        return Result.success(userService.listUsers());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户 (逻辑删除, 不能删除自己)")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用用户")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.success();
    }
}
