package com.farm.smart.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * MyBatis-Plus 多租户处理器
 * <p>
 * 自动在所有 SQL 的 WHERE 条件中追加 tenant_id = #{当前租户}，
 * INSERT 时自动填充 tenant_id 字段。
 * 忽略租户隔离的表（如 tenant 表本身）不追加条件。
 *
 * @author Smart Farm Team
 */
@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    /**
     * 不需要租户隔离的表
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "tenant"            // 租户表本身
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return new LongValue(tenantId);
        }
        return new NullValue();
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 超管跨租户操作时忽略
        if (TenantContext.isIgnore()) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName);
    }
}
