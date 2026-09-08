package com.farm.smart.controller;

import com.farm.smart.service.MqttAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * MQTT 设备认证鉴权控制器
 * <p>
 * 供 EMQX HTTP Auth 插件回调使用:
 * - POST /api/mqtt/auth - 设备身份认证
 * - POST /api/mqtt/acl  - 设备发布/订阅权限校验 (ACL)
 * <p>
 * 返回格式: {"result": "allow"} 或 {"result": "deny"}
 *
 * @author Smart Farm Team
 */
@Slf4j
@RestController
@RequestMapping("/api/mqtt")
@RequiredArgsConstructor
@Tag(name = "MQTT设备认证", description = "EMQX HTTP Auth 认证与 ACL 鉴权回调")
public class MqttAuthController {

    private final MqttAuthService mqttAuthService;

    /**
     * EMQX 认证回调
     * 接收 {clientid, username, password}, 验证传感器设备身份
     */
    @PostMapping("/auth")
    @Operation(summary = "EMQX 认证回调", description = "验证传感器设备身份, 返回 allow/deny")
    public Map<String, Object> auth(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientid");
        String username = body.get("username");
        String password = body.get("password");

        log.debug("EMQX 认证请求: clientId={}, username={}", clientId, username);
        boolean allow = mqttAuthService.authenticate(clientId, username, password);

        Map<String, Object> result = new HashMap<>();
        result.put("result", allow ? "allow" : "deny");
        return result;
    }

    /**
     * EMQX ACL 回调
     * 验证设备是否有权发布/订阅特定 topic
     */
    @PostMapping("/acl")
    @Operation(summary = "EMQX ACL 回调", description = "验证设备发布/订阅权限, 返回 allow/deny")
    public Map<String, Object> acl(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String topic = body.get("topic");
        String action = body.get("action");

        log.debug("EMQX ACL 请求: username={}, topic={}, action={}", username, topic, action);
        boolean allow = mqttAuthService.checkAcl(username, topic, action);

        Map<String, Object> result = new HashMap<>();
        result.put("result", allow ? "allow" : "deny");
        return result;
    }
}
