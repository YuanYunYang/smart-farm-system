package com.farm.smart.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.farm.smart.tenant.TenantLineHandlerImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置
 * <p>
 * 注册三大核心插件：
 * 1. TenantLineInnerInterceptor - 多租户 SQL 自动隔离
 * 2. PaginationInnerInterceptor - 分页插件
 * 3. OptimisticLockerInnerInterceptor - 乐观锁
 *
 * @author Smart Farm Team
 */
@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final TenantLineHandlerImpl tenantLineHandler;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 多租户隔离（必须放在最前面）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // 2. 分页
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 3. 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
