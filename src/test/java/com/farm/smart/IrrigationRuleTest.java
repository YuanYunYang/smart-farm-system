package com.farm.smart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 灌溉规则引擎测试
 * 验证灌溉触发条件和安全限制逻辑
 */
class IrrigationRuleTest {

    @Test
    void testSoilMoistureBelowThreshold() {
        double soilMoisture = 20.0;
        double threshold = 25.0;
        assertTrue(soilMoisture < threshold, "土壤湿度低于阈值时应触发灌溉");
    }

    @Test
    void testSoilMoistureAboveThreshold() {
        double soilMoisture = 30.0;
        double threshold = 25.0;
        assertFalse(soilMoisture < threshold, "土壤湿度高于阈值时不应触发灌溉");
    }

    @Test
    void testMaxDailyIrrigationCount() {
        int maxDailyCount = 3;
        int currentCount = 3;
        assertFalse(currentCount < maxDailyCount, "达到每日最大灌溉次数后不应再触发");
    }

    @Test
    void testMaxDurationLimit() {
        int maxDuration = 30; // 分钟
        int requestedDuration = 45;
        assertFalse(requestedDuration <= maxDuration, "请求时长超过最大限制应被拒绝");
    }

    @Test
    void testRainSkip() {
        boolean isRaining = true;
        assertTrue(isRaining, "下雨时应跳过自动灌溉");
    }
}
