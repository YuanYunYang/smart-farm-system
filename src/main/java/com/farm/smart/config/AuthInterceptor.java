package com.farm.smart.config;

import com.farm.smart.common.JwtUtils;
import com.farm.smart.common.Result;
import com.farm.smart.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器
 * <p>
 * 校验请求头中的 JWT token, 解析后将用户名与角色存入 request attribute,
 * 供后续 Controller / Service 做权限校验。无效 token 返回 401 JSON。
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 将用户名存入 request 的 attribute key */
    public static final String CURRENT_USERNAME = "currentUsername";

    /** 将角色存入 request 的 attribute key */
    public static final String CURRENT_ROLE = "currentRole";

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 提取 Authorization header (格式: Bearer <token>)
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeUnauthorized(response, "未提供认证 token");
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return writeUnauthorized(response, "认证 token 不能为空");
        }

        // 2. 解析 token
        String username = JwtUtils.getUsernameFromToken(token);
        String role = JwtUtils.getRoleFromToken(token);
        if (username == null || role == null || JwtUtils.isTokenExpired(token)) {
            return writeUnauthorized(response, "token 无效或已过期");
        }

        // 3. 存入 request attribute, 供后续使用
        request.setAttribute(CURRENT_USERNAME, username);
        request.setAttribute(CURRENT_ROLE, role);
        log.debug("认证通过: username={}, role={}", username, role);
        return true;
    }

    /**
     * 返回 401 未认证 JSON 响应
     */
    private boolean writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return false;
    }
}
