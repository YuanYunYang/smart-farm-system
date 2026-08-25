package com.farm.smart.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 - 注册认证拦截器
 * <p>
 * 排除路径:
 * <ul>
 *   <li>/auth/**      - 登录/当前用户接口</li>
 *   <li>/doc.html     - Knife4j 文档</li>
 *   <li>/swagger-ui/**, /v3/api-docs/**, /webjars/** - Swagger 资源</li>
 *   <li>/ws/**        - WebSocket 端点</li>
 *   <li>/api/sensor/**, /api/dashboard/** 等 - 现有业务接口需要带 token 访问</li>
 * </ul>
 *
 * @author Smart Farm Team
 */
@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/ws/**",
                        // 静态资源/错误页
                        "/error",
                        "/favicon.ico"
                );
    }
}
