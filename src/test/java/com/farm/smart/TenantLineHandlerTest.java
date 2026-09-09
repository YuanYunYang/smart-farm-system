package com.farm.smart;

import com.farm.smart.tenant.TenantContext;
import com.farm.smart.tenant.TenantLineHandlerImpl;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 多租户隔离处理器测试
 */
class TenantLineHandlerTest {

    private TenantLineHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new TenantLineHandlerImpl();
    }

    @Test
    void testGetTenantIdWhenSet() {
        TenantContext.setTenantId(100L);
        assertNotNull(handler.getTenantId(), "设置 tenantId 后应返回非 null");
    }

    @Test
    void testIgnoreTableForTenantTable() {
        assertTrue(handler.ignoreTable("tenant"), "tenant 表应被忽略");
    }

    @Test
    void testDoNotIgnoreBusinessTable() {
        TenantContext.setIgnore(false);
        assertFalse(handler.ignoreTable("sensor"), "sensor 表不应被忽略");
        assertFalse(handler.ignoreTable("farm"), "farm 表不应被忽略");
    }

    @Test
    void testIgnoreAllTablesWhenSuperAdmin() {
        TenantContext.setIgnore(true);
        assertTrue(handler.ignoreTable("sensor"), "超管模式下所有表应被忽略");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
}
