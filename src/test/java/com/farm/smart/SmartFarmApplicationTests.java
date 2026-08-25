package com.farm.smart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Boot 上下文加载测试
 * <p>
 * 注意: 此测试需要 MySQL、Redis、InfluxDB、EMQX 等基础设施运行
 * 如仅需验证编译, 可使用 mvn compile
 *
 * @author Smart Farm Team
 */
@SpringBootTest
class SmartFarmApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能正常加载
        System.out.println("Spring Boot 上下文加载成功!");
    }
}
