package com.farm.smart.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.smart.common.BusinessException;
import com.farm.smart.common.JwtUtils;
import com.farm.smart.common.ResultCode;
import com.farm.smart.config.AuthInterceptor;
import com.farm.smart.model.dto.LoginDTO;
import com.farm.smart.model.dto.UserCreateDTO;
import com.farm.smart.model.entity.SysUser;
import com.farm.smart.model.enums.RoleEnum;
import com.farm.smart.model.vo.LoginVO;
import com.farm.smart.model.vo.UserVO;
import com.farm.smart.repository.UserMapper;
import com.farm.smart.service.UserService;
import com.farm.smart.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统用户服务实现
 * <p>
 * 核心逻辑:
 * 1. 登录: 用户名查询 + BCrypt 密码校验 + 状态校验 + 签发 JWT
 * 2. 创建用户: RBAC 分级校验 (操作者必须比目标角色等级更高)
 * 3. 删除/禁用: 不能操作自己, 不能操作同级或更高级用户
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 登录时无租户上下文，需忽略租户隔离查询用户
        TenantContext.setIgnore(true);
        try {
            // 1. 按用户名查询
            SysUser user = findByUsername(dto.getUsername());
            if (user == null) {
                throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
            }

            // 2. 校验密码 (BCrypt)
            if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
                throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
            }

            // 3. 校验账号状态
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(ResultCode.USER_DISABLED);
            }

            // 4. 签发 JWT token（携带租户ID）
            String token = JwtUtils.generateToken(user.getUsername(), user.getRole(), user.getTenantId());

            LoginVO vo = new LoginVO();
            vo.setToken(token);
            vo.setUsername(user.getUsername());
            vo.setRole(user.getRole());
            log.info("用户登录成功: username={}, role={}, tenantId={}", user.getUsername(), user.getRole(), user.getTenantId());
            return vo;
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public UserVO getCurrentUser() {
        // /auth/** 被拦截器排除, 此处手动解析 token
        HttpServletRequest request = currentRequest();
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String token = authHeader.substring(7).trim();
        String username = JwtUtils.getUsernameFromToken(token);
        if (username == null || JwtUtils.isTokenExpired(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        SysUser user = findByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateDTO dto) {
        // 1. 获取当前操作者角色
        RoleEnum currentRole = requireCurrentRole();

        // 2. 解析目标角色
        RoleEnum targetRole = RoleEnum.getByCode(dto.getRole());
        if (targetRole == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色类型无效: " + dto.getRole());
        }

        // 3. RBAC 校验: 操作者等级必须严格高于目标角色
        //    - USER 不能创建任何用户
        //    - SYSTEM_ADMIN 只能创建 USER
        //    - SUPER_ADMIN 可创建 SYSTEM_ADMIN / USER (但不能创建另一个超管)
        if (!currentRole.canOperate(targetRole)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前角色「" + currentRole.getDescription() + "」无权创建「"
                            + targetRole.getDescription() + "」用户");
        }

        // 4. 用户名唯一性校验
        if (findByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // 5. 创建用户 (BCrypt 加密密码)
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setRole(targetRole.getCode());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        log.info("创建用户成功: id={}, username={}, role={}, 操作者={}",
                user.getId(), user.getUsername(), user.getRole(), currentRole.getCode());
        return toVO(user);
    }

    @Override
    public List<UserVO> listUsers() {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysUser::getCreateTime);
        List<SysUser> users = userMapper.selectList(wrapper);
        return users.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        SysUser target = userMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 获取当前操作者
        SysUser current = requireCurrentUser();

        // 不能删除自己
        if (current.getId().equals(id)) {
            throw new BusinessException(ResultCode.CANNOT_DELETE_SELF);
        }

        // 不能操作同级或更高级用户
        checkOperatePermission(current, target);

        int rows = userMapper.deleteById(id);
        log.info("删除用户: id={}, username={}, 操作者={}",
                id, target.getUsername(), current.getUsername());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值无效, 只能为 0 或 1");
        }

        SysUser target = userMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        SysUser current = requireCurrentUser();
        // 不能操作同级或更高级用户
        checkOperatePermission(current, target);

        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        int rows = userMapper.updateById(update);
        log.info("更新用户状态: id={}, status={}, 操作者={}",
                id, status == 1 ? "启用" : "禁用", current.getUsername());
        return rows > 0;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 按用户名查询用户 (MyBatis-Plus 逻辑删除自动过滤)
     */
    private SysUser findByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 获取当前请求对象 (从 RequestContextHolder)
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "无法获取当前请求上下文");
        }
        return attrs.getRequest();
    }

    /**
     * 获取当前操作者角色枚举 (从 request attribute 读取, 由 AuthInterceptor 写入)
     */
    private RoleEnum requireCurrentRole() {
        HttpServletRequest request = currentRequest();
        Object roleAttr = request.getAttribute(AuthInterceptor.CURRENT_ROLE);
        if (roleAttr == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        RoleEnum role = RoleEnum.getByCode(roleAttr.toString());
        if (role == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前用户角色无效");
        }
        return role;
    }

    /**
     * 获取当前操作者完整信息 (角色 + 用户记录)
     */
    private SysUser requireCurrentUser() {
        HttpServletRequest request = currentRequest();
        Object usernameAttr = request.getAttribute(AuthInterceptor.CURRENT_USERNAME);
        if (usernameAttr == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser current = findByUsername(usernameAttr.toString());
        if (current == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "当前操作者不存在");
        }
        return current;
    }

    /**
     * 校验操作者是否有权操作目标用户
     * 规则: 操作者角色等级必须严格高于目标用户角色等级
     */
    private void checkOperatePermission(SysUser current, SysUser target) {
        RoleEnum currentRole = RoleEnum.getByCode(current.getRole());
        RoleEnum targetRole = RoleEnum.getByCode(target.getRole());
        if (currentRole == null || targetRole == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "角色解析异常");
        }
        if (!currentRole.canOperate(targetRole)) {
            throw new BusinessException(ResultCode.CANNOT_OPERATE_HIGHER_ROLE);
        }
    }

    /**
     * 实体转 VO (剔除密码)
     */
    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
