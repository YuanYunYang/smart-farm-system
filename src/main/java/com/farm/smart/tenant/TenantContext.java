package com.farm.smart.tenant;

/**
 * 租户上下文 - 基于 ThreadLocal 的请求级租户隔离
 * <p>
 * 每次请求由 AuthInterceptor 从 JWT 中提取 tenantId 并注入此上下文，
 * MyBatis-Plus TenantLineInnerInterceptor 自动在 SQL 中追加 tenant_id 条件。
 *
 * @author Smart Farm Team
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = new ThreadLocal<>();

    /**
     * 设置当前租户 ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前租户 ID
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 是否忽略租户隔离（超管跨租户操作时使用）
     */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE.get());
    }

    /**
     * 设置忽略租户隔离
     */
    public static void setIgnore(boolean ignore) {
        IGNORE.set(ignore);
    }

    /**
     * 清除上下文（请求结束时必须调用，防止线程池内存泄漏）
     */
    public static void clear() {
        TENANT_ID.remove();
        IGNORE.remove();
    }
}
