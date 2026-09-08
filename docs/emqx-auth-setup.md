# EMQX HTTP Auth 认证配置指南

## 概述

本系统使用 EMQX 的 HTTP 认证插件实现 **per-device 身份验证**和 **ACL 权限控制**。

EMQX 在设备 MQTT 连接时和发布/订阅操作前, 会向本系统的回调接口发送 HTTP 请求进行鉴权。

## 回调接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `http://<server>:8080/api/mqtt/auth` | POST | 设备身份认证 |
| `http://<server>:8080/api/mqtt/acl` | POST | 设备 ACL 权限校验 |

> 注意: 这两个接口已在 `InterceptorConfig` 中排除 JWT 认证拦截, 由 EMQX 直接调用。

### 认证请求 (POST /api/mqtt/auth)

**请求体 (JSON):**

```json
{
  "clientid": "esp32-a01-temp-1234567890",
  "username": "ESP32-A01-TEMP",
  "password": "secret_ESP32-A01-TEMP"
}
```

| 字段 | 说明 |
|------|------|
| `clientid` | MQTT 客户端ID (设备生成) |
| `username` | 传感器编号 (对应 `sensor.sensor_code`) |
| `password` | 设备认证密钥 (对应 `sensor.device_secret`) |

**响应体 (JSON):**

```json
{"result": "allow"}
```

或

```json
{"result": "deny"}
```

### ACL 请求 (POST /api/mqtt/acl)

**请求体 (JSON):**

```json
{
  "clientid": "esp32-a01-temp-1234567890",
  "username": "ESP32-A01-TEMP",
  "topic": "farm/1/sensor/ESP32-A01-TEMP/data",
  "action": "publish"
}
```

| 字段 | 说明 |
|------|------|
| `username` | 传感器编号 |
| `topic` | 要操作的 MQTT topic |
| `action` | `publish` (发布) 或 `subscribe` (订阅) |

**响应体 (JSON):**

```json
{"result": "allow"}
```

或

```json
{"result": "deny"}
```

## ACL 权限规则

传感器设备只能操作以下 topic:

### 允许发布 (publish)

| Topic 模式 | 说明 |
|------------|------|
| `farm/{farmId}/sensor/{sensorCode}/data` | 传感器数据上报 |
| `farm/{farmId}/device/status` | 设备心跳/状态 |
| `farm/{farmId}/ota/progress` | OTA 升级进度上报 |

### 允许订阅 (subscribe)

| Topic 模式 | 说明 |
|------------|------|
| `farm/{farmId}/ota/command` | 接收 OTA 升级指令 |

### 校验逻辑

1. 从 topic 中提取 `farmId`
2. 校验 topic 中的 `farmId` 与传感器所属农场一致
3. 校验 topic 中的 `sensorCode` 与 `username` 一致 (仅 sensor/data topic)
4. 校验 topic 匹配允许的模式

## Redis 缓存

- 认证成功结果缓存 5 分钟 (Key: `mqtt:auth:{username}`, Value: `farmId`)
- 认证失败结果缓存 1 分钟 (防止暴力枚举)
- ACL 校验时优先读取缓存, 缓存过期后自动回源查询

## EMQX 配置

### 方式一: EMQX Dashboard 配置 (EMQX 5.x)

1. 登录 EMQX Dashboard (`http://<emqx-host>:18083`)
2. 进入 **Authentication** -> **HTTP Server**
3. 创建 HTTP 认证:
   - URL: `http://<server-host>:8080/api/mqtt/auth`
   - Method: `POST`
   - Body (JSON):
     ```json
     {"clientid": "${clientid}", "username": "${username}", "password": "${password}"}
     ```
4. 进入 **Authorization** -> **HTTP**
5. 创建 HTTP ACL:
   - URL: `http://<server-host>:8080/api/mqtt/acl`
   - Method: `POST`
   - Body (JSON):
     ```json
     {"clientid": "${clientid}", "username": "${username}", "topic": "${topic}", "action": "${action}"}
     ```

### 方式二: EMQX 配置文件 (emqx.conf)

```hocon
authentication = [
  {
    mechanism = password_based
    backend = http
    method = post
    url = "http://smart-farm-server:8080/api/mqtt/auth"
    headers {
      content-type: "application/json"
    }
    body {
      clientid: "${clientid}"
      username: "${username}"
      password: "${password}"
    }
  }
]

authorization = [
  {
    type = http
    method = post
    url = "http://smart-farm-server:8080/api/mqtt/acl"
    headers {
      content-type: "application/json"
    }
    body {
      clientid: "${clientid}"
      username: "${username}"
      topic: "${topic}"
      action: "${action}"
    }
  }
]
```

## 设备密钥管理

### 查看设备密钥

```sql
SELECT id, sensor_code, device_secret, farm_id
FROM sensor
WHERE tenant_id = 1;
```

### 更新设备密钥

```sql
-- 更新单个设备密钥
UPDATE sensor SET device_secret = 'new_secret_value'
WHERE sensor_code = 'ESP32-A01-TEMP';

-- 批量生成随机密钥
UPDATE sensor SET device_secret = SUBSTRING(MD5(RAND()), 1, 32)
WHERE tenant_id = 1;
```

### 设备端 MQTT 连接配置

```python
# ESP32 / 树莓派 端配置示例
mqtt_username = "ESP32-A01-TEMP"       # 对应 sensor.sensor_code
mqtt_password = "secret_ESP32-A01-TEMP"  # 对应 sensor.device_secret
mqtt_client_id = "esp32-a01-temp-001"    # 唯一客户端ID
```

## 安全建议

1. **生产环境** 修改默认密钥, 使用随机生成的强密钥
2. EMQX 与本系统之间的 HTTP 通信建议使用 HTTPS
3. 对 `/api/mqtt/**` 路径配置 IP 白名单, 仅允许 EMQX 服务器访问
4. 定期轮换设备密钥
5. 监控认证失败频率, 配合 Redis 缓存防止暴力攻击
