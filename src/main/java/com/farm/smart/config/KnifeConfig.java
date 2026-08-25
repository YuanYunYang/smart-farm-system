package com.farm.smart.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j (Swagger) API 文档配置
 * 访问地址: http://localhost:8080/doc.html
 *
 * @author Smart Farm Team
 */
@Configuration
public class KnifeConfig {

    @Bean
    public OpenAPI smartFarmOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能农场监测系统 API 文档")
                        .description("Spring Boot AI+IoT 智能农场监测系统接口文档\n\n" +
                                "## 功能模块\n" +
                                "- 农场/地块管理\n" +
                                "- 传感器数据采集与查询\n" +
                                "- 自动灌溉控制引擎\n" +
                                "- AI 病虫害识别\n" +
                                "- 实时看板数据\n" +
                                "- 告警管理")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Smart Farm Team")
                                .email("contact@smart-farm.com")
                                .url("https://github.com/yourusername/smart-farm-system"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
