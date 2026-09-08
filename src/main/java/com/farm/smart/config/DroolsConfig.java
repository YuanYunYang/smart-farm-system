package com.farm.smart.config;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Drools 规则引擎配置类
 * <p>
 * 提供 KieServices Bean, 供 RuleEngineService 动态加载 DRL 规则
 * 规则文件位置: classpath:rules/default-alarm-rules.drl
 *
 * @author Smart Farm Team
 */
@Slf4j
@Configuration
public class DroolsConfig {

    /**
     * 创建 KieServices 单例 Bean
     * KieServices 是 Drools 规则引擎的入口, 用于创建 KieFileSystem、KieBuilder、KieContainer 等
     */
    @Bean
    public KieServices kieServices() {
        log.info("初始化 Drools KieServices");
        return KieServices.Factory.get();
    }
}
