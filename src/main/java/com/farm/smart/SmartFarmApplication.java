package com.farm.smart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能农场监测系统 - 启动类
 * <p>
 * 功能模块:
 * - IoT 传感器数据采集 (MQTT + InfluxDB)
 * - 自动灌溉控制引擎
 * - AI 病虫害识别
 * - 实时数据看板 (WebSocket)
 * - 多级告警推送
 *
 * @author Smart Farm Team
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.farm.smart.repository")
public class SmartFarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFarmApplication.class, args);
        System.out.println("""

                ====================================================
                  Smart Farm System 启动成功!
                  API 文档: http://localhost:8080/doc.html
                  WebSocket: ws://localhost:8080/ws/farm
                ====================================================
                """);
    }
}
