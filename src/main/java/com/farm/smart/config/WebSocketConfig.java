package com.farm.smart.config;

import com.farm.smart.websocket.FarmWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * 注册 FarmWebSocketHandler, 路径为 /ws/farm
 * 允许所有来源跨域访问 (生产环境应限制具体域名)
 *
 * @author Smart Farm Team
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final FarmWebSocketHandler farmWebSocketHandler;

    public WebSocketConfig(FarmWebSocketHandler farmWebSocketHandler) {
        this.farmWebSocketHandler = farmWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 WebSocket 端点, 前端通过 ws://host:8080/ws/farm 连接
        registry.addHandler(farmWebSocketHandler, "/ws/farm")
                .setAllowedOrigins("*"); // 允许跨域
    }
}
