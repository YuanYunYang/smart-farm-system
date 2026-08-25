package com.farm.smart.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InfluxDB 2.0 配置类
 * 使用官方 influxdb-client-java 客户端连接 InfluxDB 2.x
 * 时序数据库用于存储传感器历史数据, 支持高效的时间范围查询和聚合
 *
 * @author Smart Farm Team
 */
@Slf4j
@Configuration
public class InfluxDbConfig {

    /**
     * 创建 InfluxDB 客户端
     * 使用官方 InfluxDBClientFactory 创建连接
     */
    @Bean
    public InfluxDBClient influxDBClient(
            @Value("${influxdb.url}") String url,
            @Value("${influxdb.token}") String token,
            @Value("${influxdb.org}") String org,
            @Value("${influxdb.bucket}") String bucket) {
        log.info("初始化 InfluxDB 客户端: url={}, org={}, bucket={}", url, org, bucket);
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }
}
