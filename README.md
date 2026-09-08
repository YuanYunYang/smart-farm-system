# Smart Farm System - 智能农场监测系统

> 基于 Spring Boot 3.2 + AI + IoT 的 **SaaS 级** 智慧农业监测平台，支持多租户隔离、Kafka 削峰、读写分离、自动扩缩容，通过树莓派+ESP32 边缘节点采集多传感器数据，MQTT 协议上云，内置自动灌溉引擎和 AI 病虫害识别。

---

## 目录

- [SaaS 架构总览](#saas-架构总览)
- [多租户设计](#多租户设计)
- [系统架构图](#系统架构图)
- [硬件 BOM 清单](#硬件-bom-清单)
- [核心功能](#核心功能)
- [设备认证鉴权](#设备认证鉴权)
- [OTA 固件升级](#ota-固件升级)
- [Drools 规则引擎](#drools-规则引擎)
- [前端可视化看板](#前端可视化看板)
- [技术栈](#技术栈)
- [快速启动](#快速启动)
- [生产部署](#生产部署)
- [API 概览](#api-概览)
- [MQTT Topic 规划表](#mqtt-topic-规划表)
- [自动灌溉规则](#自动灌溉规则配置示例)
- [监控告警](#监控告警)
- [项目结构](#项目结构)
- [CI/CD 流水线](#cicd-流水线)

---

## SaaS 架构总览

系统采用 **多租户 + 边云协同** 架构，边缘侧（树莓派+ESP32）采集传感器数据，经 MQTT 上云后通过 Kafka 削峰异步落库，支持多农场、多租户并行接入，K8s 水平扩展 3~10 实例。

### SaaS 关键能力

| 能力 | 实现方案 | 说明 |
|------|---------|------|
| **多租户隔离** | MyBatis-Plus TenantLineInterceptor + `tenant_id` 字段 | 每个租户独立管理农场/传感器/灌溉规则，行级数据隔离 |
| **消息削峰** | Kafka 3分区 + 异步消费 | 传感器数据先入 Kafka 再落库，支持高并发上报 |
| **读写分离** | dynamic-datasource + MySQL 主从 | 读操作路由到从库，写操作走主库 |
| **水平扩展** | K8s HPA + EMQX 共享订阅 | 3~10 Pod 自动扩缩容，MQTT 共享订阅避免消息重复 |
| **限流防护** | Redis + AOP RateLimit | 接口级限流，防止恶意请求打垮服务 |
| **安全防护** | CORS + XSS 过滤 + JWT | XSS 转义、跨域白名单、JWT 携带租户上下文 |
| **监控告警** | Actuator + Prometheus + Grafana | HTTP P99 延迟、JVM 内存、传感器上报频率等指标 |
| **生产部署** | Docker 多阶段构建 + K8s + CI/CD | 全自动构建→推送→部署流水线 |

### 数据流说明

| 方向 | 通道 | 说明 |
|------|------|------|
| 传感器 → ESP32 | GPIO/I2C | DHT22/FC-28/BH1750/MH-Z19B 数据采集 |
| ESP32 → 树莓派 | WiFi/MQTT | 采集节点将数据聚合上报到边缘网关 |
| 树莓派 → 平台 | MQTT 上行 | 传感器数据上报至云端 |
| 平台 → Kafka | 异步队列 | 数据先入 Kafka topic，削峰后异步写入 InfluxDB |
| 平台 → 树莓派 | MQTT 下行 | 灌溉控制指令下发到网关 |
| 树莓派 → 继电器 | GPIO | 控制电磁水阀开关 |
| 平台 → 前端 | WebSocket | 实时传感器数据 + 告警推送 |

---

## 多租户设计

### 租户隔离机制

```
请求入口 (HTTP/MQTT)
    │
    ├── HTTP 请求: JWT 解析 tenantId → TenantContext (ThreadLocal)
    │
    └── MQTT 消息: farm.tenantId 自动注入 → TenantContext
            │
            ▼
    MyBatis-Plus TenantLineInterceptor
            │
            ▼
    SQL 自动追加: WHERE tenant_id = #{tenantId}
```

### 租户表结构

```sql
CREATE TABLE `tenant` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_code`   VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `tenant_name`   VARCHAR(128) NOT NULL COMMENT '租户名称',
    `status`        TINYINT      DEFAULT 1 COMMENT '0-禁用 1-启用',
    `sensor_limit`  INT          DEFAULT 500 COMMENT '传感器数量上限',
    `expire_time`   DATETIME     COMMENT '到期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`)
);
```

所有业务表（`farm`、`field`、`sensor`、`irrigation_rule`、`irrigation_log`、`alarm`、`disease_record`、`sys_user`）均包含 `tenant_id` 字段并建立索引。

### 核心组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `TenantContext` | `tenant/TenantContext.java` | ThreadLocal 存储当前请求的租户 ID |
| `TenantLineHandlerImpl` | `tenant/TenantLineHandlerImpl.java` | MyBatis-Plus 租户拦截器，自动注入 SQL 条件 |
| `AuthInterceptor` | `config/AuthInterceptor.java` | 从 JWT 提取 tenantId 并设置到 TenantContext |
| `TenantMapper` | `repository/TenantMapper.java` | 租户管理 CRUD |

---

## 系统架构图

![智能农场监测系统架构图](docs/images/architecture.svg)

> **图例**：紫色边框 = 核心焦点模块（灌溉规则引擎）；紫色虚线 = 主数据流路径（MQTT 上云 + 指令下发）；青色实线 = 控制闭环（GPIO → 继电器 → 水阀）；灰色实线 = 常规依赖调用。

**数据流向（控制闭环）：**

```
传感器采集 → ESP32 节点 → 树莓派网关(聚合) → MQTT上云 → Kafka削峰 → 平台处理
                                                                          ↓
  水阀控制 ← 继电器 ← 树莓派GPIO ← MQTT指令下发 ← 灌溉规则引擎(定时检查)
```

---

## 硬件 BOM 清单

| 序号 | 组件 | 型号 | 用途 | 参考价格(元) | 数量 |
|------|------|------|------|-------------|------|
| 1 | 主控板 | 树莓派 4B 4GB | 边缘网关，聚合数据+MQTT上云+GPIO控制 | 280 | 1 |
| 2 | 采集节点 | ESP32 DevKit V1 | 传感器数据采集+WiFi上传 | 25 | 2 |
| 3 | 温湿度传感器 | DHT22 (AM2302) | 空气温度/湿度采集 | 18 | 2 |
| 4 | 土壤湿度传感器 | FC-28 (YL-38) | 土壤湿度采集 | 8 | 2 |
| 5 | 光照传感器 | BH1750FVI | 光照强度采集 | 12 | 2 |
| 6 | CO2传感器 | MH-Z19B | CO2浓度采集 | 65 | 1 |
| 7 | 继电器模块 | 5V 单路继电器 | 控制电磁水阀开关 | 8 | 1 |
| 8 | 电磁水阀 | 12V DN15 常闭型 | 灌溉水管控制 | 25 | 1 |
| 9 | 电源适配器 | 12V 2A | 水阀+继电器供电 | 20 | 1 |
| 10 | MicroSD卡 | 32GB Class10 | 树莓派系统存储 | 25 | 1 |
| 11 | 杜邦线 | 母对母/公对母 各40条 | 接线 | 10 | 1套 |
| 12 | 电源 | 5V 3A USB-C | 树莓派供电 | 25 | 1 |
| 13 | 面包板 | 830孔 | 原型搭建 | 8 | 1 |
| **合计** | | | | **约 ¥780** | |

---

## 核心功能

| 模块 | 功能说明 |
|------|---------|
| 多租户农场管理 | 每个租户独立管理农场/地块/传感器，行级数据隔离，支持传感器配额限制 |
| 传感器数据采集 | ESP32 多节点采集温湿度/土壤湿度/光照/CO2，树莓派聚合后 MQTT 上云，经 Kafka 削峰写入 InfluxDB + Redis 缓存 |
| 实时数据看板 | WebSocket 实时推送传感器数据，24小时趋势图，灌溉/告警/病害统计 |
| 自动灌溉引擎 | 规则触发（土壤湿度<阈值 && 时段允许），安全限制（最大时长/日次数/雨天跳过），MQTT 下发控制指令 |
| AI 病虫害识别 | 上传图片调用 YOLOv8 模型识别病害，返回类型+置信度+处理建议，支持批量识别 |
| 多级告警系统 | 传感器超阈值/设备离线/灌溉异常告警，WebSocket 实时推送 + 数据库记录，Redis 防重复 |
| 数据聚合 | 定时每小时聚合传感器数据（均值/最大/最小），降低查询扫描量 |
| 接口限流 | 基于 Redis 的令牌桶限流，按接口维度配置 QPS 上限 |
| 设备认证鉴权 | 基于 EMQX HTTP Auth 实现 per-device 身份认证，每台传感器独立 deviceSecret，ACL 控制 Topic 权限 |
| OTA 固件升级 | 支持固件版本管理、批量升级任务、MQTT 指令下发、设备进度追踪与 WebSocket 推送 |
| Drools 规则引擎 | 集成 Drools 8.44 动态规则引擎，支持复合条件告警（如"高温+低湿+低电量"）、规则热加载与试跑 |
| 前端可视化看板 | 独立 HTML 单页看板，WebSocket 实时推送传感器数据与告警，Chart.js 渲染趋势图与饼图 |
| API 文档 | Knife4j (Swagger) 自动生成接口文档，访问 `/doc.html` |

---

## 设备认证鉴权

### 架构说明

系统通过 EMQX HTTP Auth 插件实现 **per-device 身份认证**，每台传感器（ESP32 节点/树莓派网关）在注册时分配独立的 `deviceSecret`，EMQX 在设备连接和发布/订阅前回调系统接口进行校验。

```
传感器 --MQTT连接--> EMQX --HTTP POST /api/mqtt/auth--> 系统校验 sensorCode + deviceSecret
传感器 --发布/订阅--> EMQX --HTTP POST /api/mqtt/acl---> 系统校验 Topic 权限
```

### 认证流程

| 步骤 | 说明 |
|------|------|
| 传感器注册 | 系统为每台传感器生成 `deviceSecret`，存入 `sensor` 表 |
| EMQX 配置 | 在 EMQX Dashboard 配置 HTTP Auth 插件，指向 `http://server:8080/api/mqtt/auth` 和 `/api/mqtt/acl` |
| 连接认证 | 传感器以 `username=sensorCode, password=deviceSecret` 连接 EMQX，EMQX 回调 `/api/mqtt/auth` |
| ACL 鉴权 | 传感器发布/订阅时 EMQX 回调 `/api/mqtt/acl`，校验设备仅能操作自身农场相关 Topic |
| 缓存优化 | 认证结果缓存至 Redis，降低高频回调对数据库的冲击 |

### 核心组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `MqttAuthController` | `controller/MqttAuthController.java` | EMQX 认证/ACL 回调端点 |
| `MqttAuthServiceImpl` | `service/impl/MqttAuthServiceImpl.java` | 认证逻辑：DeviceSecret 校验 + Redis 缓存 + Topic ACL |
| `Sensor.deviceSecret` | `model/entity/Sensor.java` | 传感器密钥字段 |

详细配置步骤请参考 [docs/emqx-auth-setup.md](docs/emqx-auth-setup.md)。

---

## OTA 固件升级

### 架构说明

系统提供 OTA 固件升级能力，支持对 ESP32 采集节点和树莓派网关进行远程固件更新，无需现场操作。

```
固件上传(DRAFT) → 发布(PUBLISHED) → 创建升级任务 → MQTT下发升级指令
                                                    ↓
设备下载固件 → 上报进度(DOWNLOADING/INSTALLING) → 成功/失败 → 任务统计更新 → WebSocket推送
```

### 核心流程

| 阶段 | 说明 |
|------|------|
| 固件管理 | 上传固件包元信息（版本/URL/MD5/SHA256），默认 DRAFT，发布后可被任务引用 |
| 任务编排 | 支持全量/指定设备升级，立即或定时策略 |
| 指令下发 | 通过 MQTT 下发升级指令，包含固件 URL、版本号、校验和 |
| 进度追踪 | 设备上报 `DOWNLOADING → INSTALLING → SUCCESS/FAILED`，系统更新进度与任务统计 |
| 完成通知 | 任务全部设备终态后置为 COMPLETED，WebSocket 推送完成摘要 |

### 数据模型

| 实体 | 说明 |
|------|------|
| `Firmware` | 固件包：版本、文件URL、大小、MD5/SHA256、状态 |
| `OtaTask` | 升级任务：固件ID、目标范围、策略、状态、设备统计 |
| `OtaDeviceProgress` | 设备进度：任务ID、设备ID、当前/目标版本、状态 |

---

## Drools 规则引擎

### 架构说明

系统集成 Drools 8.44 规则引擎，将告警逻辑从硬编码解耦为可动态管理的 DRL 规则，支持复合条件告警如"高温 + 低土壤湿度 + 低电量"。

```
传感器上报数据 → 构造 SensorDataFact → KieSession.fireAllRules() → 触发规则 → 生成 AlarmResult → 落库告警
```

### 核心能力

| 能力 | 说明 |
|------|------|
| 动态规则加载 | 启动时加载默认规则 + 数据库启用规则，编译为 KieContainer |
| 规则热重载 | 规则 CRUD 后自动重载，无需停机 |
| 复合条件告警 | 支持多传感器属性联合判断，如"温度 > 35℃ 且土壤湿度 < 20%" |
| 规则试跑 | `testRule()` 接口可在不落库的情况下验证规则正确性 |

### 默认规则示例

```drl
// 高温干旱复合告警（CRITICAL）
rule "High Temperature and Drought Critical"
    salience 100
    no-loop true
    when
        $fact : SensorDataFact(
            $temp : getDouble("temperature"), $temp != null, $temp > 35.0,
            $soil : getDouble("soil_moisture"), $soil != null, $soil < 20.0
        )
    then
        AlarmResult result = new AlarmResult();
        result.setDeviceId($fact.getSensorId());
        result.setLevel("CRITICAL");
        result.setTitle("高温干旱复合告警");
        result.setContent("温度 " + $temp + "℃ 且土壤湿度 " + $soil + "%，存在作物热害风险");
        $fact.addResult(result);
end

// CO2 浓度超标告警（WARNING）
rule "CO2 Over Threshold Warning"
    salience 50
    no-loop true
    when
        $fact : SensorDataFact($co2 : getDouble("co2"), $co2 != null, $co2 > 1000.0)
    then
        AlarmResult result = new AlarmResult();
        result.setDeviceId($fact.getSensorId());
        result.setLevel("WARNING");
        result.setTitle("CO2浓度超标告警");
        result.setContent("CO2浓度 " + $co2 + " ppm 超过 1000 ppm 阈值");
        $fact.addResult(result);
end
```

---

## 前端可视化看板

### 看板说明

系统提供独立的前端可视化看板（`frontend/index.html`），浏览器直接打开即可使用，通过 WebSocket 实时接收传感器数据与告警。

### 功能模块

| 模块 | 说明 |
|------|------|
| 概览看板 | 农场数、传感器数、告警数、灌溉次数统计卡片 |
| 传感器实时数据 | 温度/湿度/土壤湿度/光照/CO2 实时数值卡片 |
| 24小时趋势图 | Chart.js 折线图展示传感器数据趋势 |
| 灌溉管理 | 灌溉规则列表、灌溉日志、手动触发灌溉 |
| 告警列表 | 最新告警事件，按级别颜色区分 |
| OTA 升级 | 固件版本管理与升级进度展示 |
| 规则管理 | Drools 规则列表与启用/禁用 |

### 使用方式

```bash
# 1. 启动后端服务
mvn clean package -DskipTests
java -jar target/smart-farm-system-1.0.0.jar

# 2. 浏览器打开看板
open frontend/index.html

# 3. 在看板顶栏输入 JWT Token 建立 WebSocket 连接
```

看板自动连接 `ws://localhost:8080/ws/farm`，实时接收传感器数据、告警事件和灌溉状态。

---

## 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 17 | LTS 版本 |
| 框架 | Spring Boot | 3.2.5 | 主框架 |
| ORM | MyBatis-Plus | 3.5.7 | MySQL 数据访问 + 租户拦截 |
| 动态数据源 | dynamic-datasource | 4.3.1 | 读写分离路由 |
| 消息队列 | Apache Kafka | 3.7 | 传感器数据削峰、异步解耦 |
| 时序数据库 | InfluxDB | 2.7 | 传感器时序数据存储 |
| 业务数据库 | MySQL | 8.0 | 主从读写分离 |
| 缓存 | Redis | 7 | 最新值缓存 + 限流 + 告警去重 |
| 消息协议 | MQTT (Eclipse Paho) | 1.2.5 | 边缘设备通信，EMQX 共享订阅 |
| MQTT Broker | EMQX | 5.7 | MQTT 消息中间件 |
| 实时推送 | WebSocket | - | 前端看板数据推送 |
| AI | YOLOv8 + FastAPI | - | 病虫害识别 (HTTP API) |
| 安全 | CORS + XSS Filter + JWT | - | 跨域防护 + XSS 转义 + 租户级认证 |
| 监控 | Actuator + Micrometer + Prometheus | - | 指标采集 + P99 分位统计 |
| 可视化 | Grafana | latest | 监控面板 |
| 规则引擎 | Drools | 8.44.0.Final | 复杂告警规则动态管理与热加载 |
| 前端看板 | Chart.js + WebSocket | 4.4.1 | 独立 HTML 可视化看板，实时数据推送 |
| API 文档 | Knife4j | 4.4.0 | OpenAPI 3 文档 |
| 容器化 | Docker + Docker Compose | - | 多阶段构建 + 基础设施编排 |
| 编排 | Kubernetes | - | HPA 自动扩缩容 + Ingress |
| CI/CD | GitHub Actions | - | 编译→测试→镜像推送→K8s 部署 |
| 边缘节点 | ESP32 + Arduino | - | 传感器采集 |
| 边缘网关 | 树莓派 + Python | - | 数据聚合+MQTT上云+GPIO控制 |

---

## 快速启动

### 前置条件

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- （硬件部分）树莓派 4B + ESP32 + 传感器套件

### 1. 启动基础设施（开发环境）

```bash
git clone https://github.com/yourusername/smart-farm-system.git
cd smart-farm-system
docker-compose up -d
```

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 业务数据库 (root/Farm@2024) |
| Redis | 6379 | 缓存 (密码 Farm@2024) |
| EMQX Dashboard | 18083 | MQTT Broker 管理界面 (admin/Farm@2024) |
| InfluxDB UI | 8086 | 时序数据库 (admin/Farm@2024) |

### 2. 编译并启动应用

```bash
mvn clean compile
mvn spring-boot:run
```

### 3. 验证启动

| 服务 | 地址 |
|------|------|
| API 文档 (Knife4j) | http://localhost:8080/doc.html |
| 健康检查 | http://localhost:8080/actuator/health |
| Prometheus 指标 | http://localhost:8080/actuator/prometheus |
| WebSocket | ws://localhost:8080/ws/farm?farmId=1 |

### 4. （可选）启动 AI 识别服务

如需启用 AI 病虫害识别，需部署 YOLOv8 服务（参考 [docs/hardware-setup.md](docs/hardware-setup.md)），然后在 `application.yml` 中设置 `ai.disease.enabled: true`。未启用时系统返回模拟结果。

### 5. （可选）硬件部署

1. 将 `scripts/esp32_sensor_node.ino` 烧录到 ESP32 开发板
2. 将 `scripts/raspberry_pi_gateway.py` 部署到树莓派
3. 按接线表连接传感器（参考 [docs/hardware-setup.md](docs/hardware-setup.md)）

---

## 生产部署

### Docker Compose 生产环境

```bash
# 启动全部生产基础设施（MySQL主从 + Redis + EMQX + Kafka + InfluxDB + Prometheus + Grafana）
docker-compose -f docker-compose-prod.yml up -d

# 编译并启动应用
mvn clean package -DskipTests
java -jar -Dspring.profiles.active=prod target/smart-farm-system-1.0.0.jar
```

生产环境组件：

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL 主库 | 3306 | 写入（GTID 复制） |
| MySQL 从库 | 3307 | 只读 |
| EMQX | 1883 / 18083 | MQTT Broker + 管理界面 |
| Kafka | 9092 | 传感器数据消息队列 |
| InfluxDB | 8086 | 时序数据 |
| Prometheus | 9090 | 指标采集 |
| Grafana | 3000 | 监控看板 (admin/grafana123) |

### Kubernetes 部署

```bash
kubectl apply -f k8s/deployment.yaml
```

K8s 部署规格：

| 资源 | 配置 |
|------|------|
| 副本数 | 3（HPA 自动扩至 10） |
| CPU | 请求 500m，限制 2000m |
| 内存 | 请求 512Mi，限制 1536Mi |
| HPA 触发 | CPU > 70% 或 内存 > 80% |
| 健康检查 | Liveness `/actuator/health/liveness`，Readiness `/actuator/health/readiness` |
| Ingress | `farm.example.com`（Nginx Ingress） |

### Docker 镜像

```bash
# 多阶段构建
docker build -t yuanyunyang/smart-farm-system:latest .

# 运行
docker run -d -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=your-mysql-host \
  -e REDIS_HOST=your-redis-host \
  -e KAFKA_SERVERS=your-kafka:9092 \
  yuanyunyang/smart-farm-system:latest
```

---

## API 概览

所有接口统一前缀：`/api`，JWT 认证后自动注入租户上下文。

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 认证 | POST | `/api/auth/login` | 登录（返回 JWT，包含 tenantId） |
| | POST | `/api/user/register` | 注册用户 |
| 农场管理 | GET | `/api/farm/list` | 获取所有农场（自动过滤租户） |
| | GET | `/api/farm/{id}` | 农场详情 |
| | POST | `/api/farm` | 创建农场 |
| | PUT | `/api/farm` | 更新农场 |
| | DELETE | `/api/farm/{id}` | 删除农场 |
| 地块管理 | GET | `/api/farm/{farmId}/fields` | 地块列表 |
| | POST | `/api/farm/field` | 创建地块 |
| 传感器 | GET | `/api/sensor/list/{farmId}` | 传感器设备列表 |
| | GET | `/api/sensor/latest/{sensorId}` | 最新传感器数据 |
| | GET | `/api/sensor/latest/farm/{farmId}` | 农场所有传感器最新值 |
| | GET | `/api/sensor/history` | 历史趋势 (InfluxDB) |
| | GET | `/api/sensor/trend/24h/{farmId}` | 24小时趋势 |
| 灌溉控制 | POST | `/api/irrigation/trigger` | 手动触发灌溉 |
| | GET | `/api/irrigation/logs` | 灌溉日志 |
| | GET | `/api/irrigation/stats/weekly/{farmId}` | 本周灌溉统计 |
| | GET | `/api/irrigation/rules/{farmId}` | 灌溉规则列表 |
| | POST | `/api/irrigation/rules` | 创建灌溉规则 |
| | PUT | `/api/irrigation/rules` | 更新灌溉规则 |
| | DELETE | `/api/irrigation/rules/{id}` | 删除灌溉规则 |
| 病害识别 | POST | `/api/disease/detect` | AI 病虫害识别 |
| | POST | `/api/disease/batch-detect` | 批量识别 |
| | GET | `/api/disease/history` | 识别历史 |
| 看板 | POST | `/api/dashboard/overview` | 看板聚合数据 |
| | GET | `/api/dashboard/trend` | 传感器趋势 |
| MQTT认证 | POST | `/api/mqtt/auth` | EMQX 认证回调 |
| | POST | `/api/mqtt/acl` | EMQX ACL 回调 |
| OTA升级 | POST | `/api/ota/firmware` | 上传固件包 |
| | POST | `/api/ota/firmware/{id}/publish` | 发布固件 |
| | GET | `/api/ota/firmware/list` | 固件列表 |
| | POST | `/api/ota/task` | 创建升级任务 |
| | GET | `/api/ota/task/{id}` | 任务详情 |
| | GET | `/api/ota/task/list` | 任务列表 |
| | POST | `/api/ota/task/{id}/cancel` | 取消任务 |
| 规则引擎 | GET | `/api/rule/list` | 规则列表 |
| | POST | `/api/rule` | 新增规则 |
| | PUT | `/api/rule` | 更新规则 |
| | DELETE | `/api/rule/{id}` | 删除规则 |
| | POST | `/api/rule/test` | 规则试跑 |
| 告警 | GET | `/api/alarm/list` | 告警列表 |
| | GET | `/api/alarm/recent/{farmId}` | 最近告警 |
| | GET | `/api/alarm/count/today/{farmId}` | 今日告警数 |
| | PUT | `/api/alarm/handle/{id}` | 处理告警 |

---

## MQTT Topic 规划表

| Topic 模板 | 方向 | 说明 |
|------------|------|------|
| `farm/{farmId}/sensor/{sensorId}/data` | 上行 | 传感器数据上报 (ESP32→网关→平台) |
| `farm/{farmId}/control/req` | 下行 | 控制指令下发 (平台→网关→继电器) |
| `farm/{farmId}/control/resp` | 上行 | 设备控制回复 (网关→平台) |
| `farm/{farmId}/device/status` | 上行 | 设备心跳/状态 (网关→平台) |
| `farm/{farmId}/config/req` | 下行 | 设备配置更新 (平台→网关) |

### 传感器数据上报格式示例

```json
{
  "farmId": 1,
  "fieldId": 1,
  "sensorId": "ESP32-A01",
  "timestamp": 1716000000000,
  "data": {
    "temperature": 28.5,
    "humidity": 65.0,
    "soil_moisture": 35.2,
    "light": 12000,
    "co2": 450
  },
  "battery": 3.7
}
```

### 灌溉控制指令示例 (下行)

```json
{
  "commandId": "IRR-1716000000000-a1b2c3d4",
  "fieldId": 1,
  "valveDeviceId": "RELAY-01",
  "action": "OPEN",
  "duration": 600,
  "timestamp": 1716000000000
}
```

### 设备控制回复示例 (上行)

```json
{
  "commandId": "IRR-1716000000000-a1b2c3d4",
  "success": true,
  "actualDuration": 598,
  "waterUsage": 12.5,
  "message": "灌溉执行完成"
}
```

---

## 自动灌溉规则配置示例

```json
{
  "farmId": 1,
  "fieldId": 1,
  "name": "A区1号番茄灌溉规则",
  "soilMoistureThreshold": 25.0,
  "targetSoilMoisture": 60.0,
  "allowedStart": "06:00",
  "allowedEnd": "22:00",
  "maxDurationSeconds": 1800,
  "maxDailyCount": 4,
  "skipIfRain": 1,
  "enabled": 1
}
```

**灌溉触发条件（全部满足）：**
1. 当前时间在 `allowedStart` ~ `allowedEnd` 时段内
2. 当天灌溉次数 < `maxDailyCount`
3. 地块无正在进行的灌溉
4. 未下雨（`skipIfRain=1` 时检查天气）
5. 当前土壤湿度 < `soilMoistureThreshold`

**安全限制：**
- 单次灌溉时长不超过 `maxDurationSeconds`
- 每日灌溉次数不超过 `maxDailyCount`
- 同一地块不允许重复触发
- 雨天可跳过（可配置）

---

## 树莓派 GPIO 接线对照表

| GPIO 引脚 | 连接设备 | 引脚类型 | 说明 |
|-----------|---------|---------|------|
| GPIO 17 (Pin 11) | 继电器 IN | 输出 | 控制继电器开关（水阀） |
| GPIO 22 (Pin 15) | 蜂鸣器 (可选) | 输出 | 告警提示 |
| GPIO 4 (Pin 7) | DHT22 DATA | 输入 | 树莓派直连温湿度（备用） |
| 3.3V (Pin 1) | BH1750 VCC | 电源 | 光照传感器供电 |
| SDA (Pin 3) | BH1750 SDA | I2C | 光照传感器数据 |
| SCL (Pin 5) | BH1750 SCL | I2C | 光照传感器时钟 |
| 5V (Pin 2/4) | 继电器 VCC | 电源 | 继电器供电 |
| GND (Pin 6/9) | 继电器/DHT22 GND | 地线 | 公共地 |

> 详细接线说明请参考 [docs/hardware-setup.md](docs/hardware-setup.md)

---

## 监控告警

### Prometheus 指标

| 指标 | 类型 | 说明 |
|------|------|------|
| `http_server_requests_seconds` | Timer | HTTP 请求延迟（P50/P95/P99） |
| `jvm_memory_used_bytes` | Gauge | JVM 内存使用 |
| `jvm_threads_live_threads` | Gauge | JVM 线程数 |
| `process_cpu_usage` | Gauge | 进程 CPU 使用率 |
| `hikaricp_connections_active` | Gauge | 数据库连接池活跃连接 |

### Grafana 看板

启动后访问 `http://localhost:3000`（admin/grafana123），添加 Prometheus 数据源 `http://prometheus:9090`，导入看板。

### Actuator 端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康检查（含 DB/Redis 状态） |
| `/actuator/prometheus` | Prometheus 指标抓取 |
| `/actuator/metrics` | 指标列表 |
| `/actuator/env` | 环境变量（需授权） |
| `/actuator/loggers` | 动态日志级别调整（需授权） |

---

## 项目结构

```
smart-farm-system/
├── README.md                              项目说明
├── pom.xml                                Maven 依赖
├── Dockerfile                             多阶段 Docker 构建
├── docker-compose.yml                     开发环境基础设施
├── docker-compose-prod.yml                生产环境基础设施（主从+Kafka+监控）
├── .github/workflows/ci.yml               CI/CD 流水线
├── k8s/deployment.yaml                    K8s 部署清单（HPA+Ingress）
├── monitoring/prometheus.yml              Prometheus 采集配置
├── docs/
│   ├── architecture.md                    架构设计文档
│   └── hardware-setup.md                  硬件接线指南
├── scripts/
│   ├── esp32_sensor_node.ino              ESP32 采集节点代码
│   └── raspberry_pi_gateway.py            树莓派网关代码
├── src/main/java/com/farm/smart/
│   ├── SmartFarmApplication.java          启动类
│   ├── tenant/                            多租户组件
│   │   ├── TenantContext.java             租户上下文（ThreadLocal）
│   │   └── TenantLineHandlerImpl.java     SQL 租户拦截器
│   ├── config/                            配置类
│   │   ├── MqttConfig.java                MQTT 连接（EMQX 共享订阅）
│   │   ├── KafkaConfig.java               Kafka 生产者/消费者配置
│   │   ├── CorsConfig.java                CORS 跨域配置
│   │   ├── XssFilter.java                 XSS 防护过滤器
│   │   ├── MybatisPlusConfig.java         MyBatis-Plus + 租户拦截器
│   │   ├── RedisConfig.java               Redis 序列化
│   │   ├── InfluxDbConfig.java            InfluxDB 客户端
│   │   ├── WebSocketConfig.java           WebSocket 端点
│   │   ├── AuthInterceptor.java           JWT 认证 + 租户上下文注入
│   │   └── MyBatisMetaObjectHandler.java  自动填充时间
│   ├── controller/                        REST 控制器
│   │   ├── MqttAuthController.java         EMQX 认证/ACL 回调
│   │   ├── OtaController.java              OTA 固件升级管理
│   │   ├── RuleController.java             Drools 规则管理
│   │   ├── DashboardController.java        看板聚合数据
│   │   └── ...
│   ├── service/                           服务接口与实现
│   │   ├── MqttAuthService.java            设备认证鉴权
│   │   ├── OtaService.java                 OTA 升级服务
│   │   └── RuleEngineService.java          规则引擎服务
│   ├── config/                            配置类
│   │   ├── DroolsConfig.java               Drools KieContainer 配置
│   │   ├── MqttConfig.java                MQTT 连接（EMQX 共享订阅）
│   │   ├── KafkaConfig.java               Kafka 生产者/消费者配置
│   │   ├── CorsConfig.java                CORS 跨域配置
│   │   ├── XssFilter.java                 XSS 防护过滤器
│   │   ├── MybatisPlusConfig.java         MyBatis-Plus + 租户拦截器
│   │   ├── RedisConfig.java               Redis 序列化
│   │   ├── InfluxDbConfig.java            InfluxDB 客户端
│   │   ├── WebSocketConfig.java           WebSocket 端点
│   │   ├── AuthInterceptor.java           JWT 认证 + 租户上下文注入
│   │   └── MyBatisMetaObjectHandler.java  自动填充时间
│   ├── kafka/                             Kafka 消费者
│   │   └── SensorDataKafkaConsumer.java   传感器数据异步消费
│   ├── mqtt/                              MQTT 通信核心
│   ├── ai/                                AI 病虫害识别客户端
│   ├── schedule/                          定时任务（灌溉+数据聚合）
│   ├── websocket/                         WebSocket 实时推送
│   ├── common/                            公共组件
│   │   ├── RateLimit.java                 限流注解
│   │   ├── RateLimitAspect.java           Redis 限流切面
│   │   ├── JwtUtils.java                  JWT 工具
│   │   ├── Result.java / ResultCode.java  统一返回
│   │   └── GlobalExceptionHandler.java    全局异常处理
│   └── model/                             数据模型
│       ├── entity/                        实体（含 Firmware/OtaTask/AlarmRule/Tenant）
│       ├── dto/                           DTO（含 SensorDataFact/AlarmResult）
│       ├── vo/ / enums/
├── frontend/                              前端可视化看板
│   └── index.html                         独立 HTML 看板（Chart.js + WebSocket）
├── src/main/resources/
│   ├── application.yml                    SaaS 配置（读写分离+Kafka+监控）
│   ├── rules/                             Drools 规则文件
│   │   └── default-alarm-rules.drl        默认告警规则集
│   ├── mapper/                            MyBatis XML 映射
│   └── sql/init.sql                       建表（含 tenant/firmware/ota_task/alarm_rule 表）
└── src/test/java/                         单元测试
```

---

## CI/CD 流水线

GitHub Actions 自动化流水线（`.github/workflows/ci.yml`）：

```
Push to main/develop
    │
    ├── build: 编译 → 单元测试 → 打包
    │
    ├── docker: 构建镜像 → 推送 DockerHub
    │           yuanyunyang/smart-farm-system:latest
    │           yuanyunyang/smart-farm-system:{commit-sha}
    │
    └── deploy: kubectl apply -f k8s/deployment.yaml
```

### 所需 GitHub Secrets

| Secret | 说明 |
|--------|------|
| `DOCKER_USERNAME` | DockerHub 用户名 |
| `DOCKER_TOKEN` | DockerHub Access Token |
| `KUBE_CONFIG` | K8s 集群 kubeconfig |

---

## License

MIT License - 详见 [LICENSE](LICENSE)

---

> Smart Farm Team | SaaS IoT + AI for Smart Agriculture
