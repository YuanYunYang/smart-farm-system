package com.farm.smart.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 农场 WebSocket 处理器
 * <p>
 * 功能:
 * - 管理所有连接的看板客户端会话
 * - 向所有在线客户端推送实时传感器数据、告警通知
 * - 支持按农场ID过滤推送
 *
 * 前端连接: ws://localhost:8080/ws/farm?farmId=1
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
public class FarmWebSocketHandler extends TextWebSocketHandler {

    /** 在线客户端会话集合 (线程安全) */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 客户端ID -> 农场ID 映射 */
    private final Map<String, Long> sessionFarmMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientId = session.getId();
        sessions.put(clientId, session);

        // 从握手参数中获取 farmId
        Long farmId = extractFarmId(session);
        if (farmId != null) {
            sessionFarmMap.put(clientId, farmId);
        }

        log.info("WebSocket 客户端连接: clientId={}, farmId={}, 当前在线数={}", clientId, farmId, sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = session.getId();
        sessions.remove(clientId);
        sessionFarmMap.remove(clientId);
        log.info("WebSocket 客户端断开: clientId={}, status={}, 当前在线数={}", clientId, status, sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 可扩展: 处理客户端发来的消息 (如订阅特定传感器)
        log.debug("收到 WebSocket 消息: clientId={}, payload={}", session.getId(), message.getPayload());
    }

    /**
     * 广播消息给所有在线客户端
     */
    public void broadcast(String message) {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            sendMessage(entry.getValue(), message);
        }
    }

    /**
     * 推送给指定农场的所有客户端
     */
    public void sendToFarm(Long farmId, String message) {
        for (Map.Entry<String, Long> entry : sessionFarmMap.entrySet()) {
            if (farmId.equals(entry.getValue())) {
                WebSocketSession session = sessions.get(entry.getKey());
                sendMessage(session, message);
            }
        }
    }

    /**
     * 发送消息给单个会话
     */
    private void sendMessage(WebSocketSession session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("WebSocket 发送消息失败: sessionId={}", session.getId(), e);
            }
        }
    }

    /**
     * 获取当前在线客户端数量
     */
    public int getOnlineCount() {
        return sessions.size();
    }

    /**
     * 从握手参数中提取 farmId
     */
    private Long extractFarmId(WebSocketSession session) {
        try {
            String query = session.getUri() != null ? session.getUri().getQuery() : null;
            if (query != null && query.contains("farmId=")) {
                String farmIdStr = query.split("farmId=")[1].split("&")[0];
                return Long.parseLong(farmIdStr);
            }
        } catch (Exception e) {
            log.warn("提取 farmId 失败: {}", e.getMessage());
        }
        return null;
    }
}
