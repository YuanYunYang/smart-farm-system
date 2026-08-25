package com.farm.smart.service;

import com.farm.smart.model.dto.LoginDTO;
import com.farm.smart.model.dto.UserCreateDTO;
import com.farm.smart.model.vo.LoginVO;
import com.farm.smart.model.vo.UserVO;

import java.util.List;

/**
 * 系统用户服务接口
 * <p>
 * 职责: 登录认证、用户管理(RBAC 校验)。
 * 当前操作者信息通过 request attribute (由 AuthInterceptor 写入) 获取。
 *
 * @author Smart Farm Team
 */
public interface UserService {

    /**
     * 用户登录
     *
     * @param dto 登录请求 (用户名 + 密码)
     * @return 登录成功返回 token 与用户信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 获取当前登录用户信息
     * 从请求头解析 token, 反查用户记录
     *
     * @return 当前用户信息
     */
    UserVO getCurrentUser();

    /**
     * 创建用户 (含 RBAC 校验)
     * <ul>
     *   <li>SUPER_ADMIN 可创建 SYSTEM_ADMIN / USER</li>
     *   <li>SYSTEM_ADMIN 可创建 USER</li>
     *   <li>USER 不能创建任何用户</li>
     * </ul>
     *
     * @param dto 用户创建请求
     * @return 创建后的用户信息 (不含密码)
     */
    UserVO createUser(UserCreateDTO dto);

    /**
     * 查询用户列表 (按创建时间倒序)
     */
    List<UserVO> listUsers();

    /**
     * 删除用户 (逻辑删除)
     * 不能删除自己, 不能操作同级或更高级用户
     *
     * @param id 目标用户ID
     * @return 是否删除成功
     */
    boolean deleteUser(Long id);

    /**
     * 启用/禁用用户
     * 不能操作同级或更高级用户
     *
     * @param id     目标用户ID
     * @param status 目标状态: 0-禁用, 1-启用
     * @return 是否更新成功
     */
    boolean updateStatus(Long id, Integer status);
}
