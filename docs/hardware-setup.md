# 硬件接线与部署指南

本文档详细说明树莓派 + ESP32 + 传感器的接线方式和部署步骤。

## 1. 硬件清单

参考 README.md 中的 BOM 表，核心组件：
- 树莓派 4B (4GB) × 1 — 边缘网关
- ESP32 DevKit V1 × 2 — 采集节点
- DHT22 温湿度传感器 × 2
- FC-28 土壤湿度传感器 × 2
- BH1750 光照传感器 × 2
- MH-Z19B CO2 传感器 × 1
- 5V 单路继电器 × 1
- 12V 电磁水阀 × 1

## 2. ESP32 传感器采集节点接线

### 2.1 ESP32 节点 A (温室1号) 接线表

| 传感器 | 传感器引脚 | ESP32 引脚 | 说明 |
|--------|-----------|-----------|------|
| DHT22 | VCC | 3.3V | 电源 |
| DHT22 | DATA | GPIO 4 | 单总线数据 |
| DHT22 | GND | GND | 地线 |
| FC-28 | VCC | 3.3V | 电源 |
| FC-28 | A0 | GPIO 36 (ADC1_CH0) | 模拟输出 |
| FC-28 | GND | GND | 地线 |
| BH1750 | VCC | 3.3V | 电源 |
| BH1750 | SDA | GPIO 21 (SDA) | I2C 数据 |
| BH1750 | SCL | GPIO 22 (SCL) | I2C 时钟 |
| BH1750 | GND | GND | 地线 |
| MH-Z19B | VCC | 5V (VIN) | 电源 |
| MH-Z19B | TX | GPIO 16 (RX2) | UART 接收 |
| MH-Z19B | RX | GPIO 17 (TX2) | UART 发送 |
| MH-Z19B | GND | GND | 地线 |

### 2.2 ESP32 节点 B (温室2号) 接线表

节点 B 只需 DHT22 + FC-28 + BH1750（无 CO2），接线方式相同，引脚可复用。

## 3. 树莓派网关接线表

### 3.1 树莓派 GPIO 引脚分配

```
树莓派 4B GPIO 引脚图 (40-pin):

  3V3  (1)  (2)  5V
GPIO2 (3)  (4)  5V
GPIO3 (5)  (6)  GND
GPIO4 (7)  (8)  GPIO14 (TXD)
  GND (9)  (10) GPIO15 (RXD)
GPIO17(11) (12) GPIO18
GPIO27(13) (14) GND
GPIO22(15) (16) GPIO23
  3V3 (17) (18) GPIO24
GPIO10(19) (20) GND
GPIO9 (21) (22) GPIO25
GPIO11(23) (24) GPIO8  (CE0)
  GND (25) (26) GPIO7  (CE1)
...
```

### 3.2 树莓派连接表

| 树莓派引脚 | 连接设备 | 设备引脚 | 说明 |
|-----------|---------|---------|------|
| GPIO 17 (Pin 11) | 继电器模块 | IN | 控制继电器（水阀开关） |
| 5V (Pin 2 或 4) | 继电器模块 | VCC | 继电器供电 |
| GND (Pin 6) | 继电器模块 | GND | 公共地 |
| 3.3V (Pin 1) | BH1750 (树莓派直连) | VCC | 光照传感器供电 |
| GPIO 2/SDA (Pin 3) | BH1750 | SDA | I2C 数据 |
| GPIO 3/SCL (Pin 5) | BH1750 | SCL | I2C 时钟 |
| GND (Pin 9) | BH1750 | GND | 地线 |

### 3.3 继电器与水阀接线

```
12V 电源 (+) ──────────────┐
                             │
                     ┌───────┴───────┐
                     │  继电器常开端   │
                     │  (COM → NO)   │
                     └───────┬───────┘
                             │
12V 水阀 (+) ◄───────────────┘
12V 水阀 (-) ─────────────── 12V 电源 (-)

继电器控制：
  IN  → 树莓派 GPIO 17
  VCC → 树莓派 5V
  GND → 树莓派 GND
```

> **注意：** 继电器控制 12V 水阀回路，与树莓派 3.3V 逻辑完全隔离。继电器模块自带的光耦隔离可保护树莓派。

## 4. 部署步骤

### 4.1 ESP32 节点部署

1. 安装 [Arduino IDE](https://www.arduino.cc/en/software)
2. 在 Arduino IDE 中安装 ESP32 开发板支持包
3. 安装所需库：
   - `DHT sensor library` (by Adafruit)
   - `BH1750` (by Christopher Laws)
   - `ArduinoJson`
4. 打开 `scripts/esp32_sensor_node.ino`
5. 修改 WiFi 配置和网关地址：
   ```cpp
   #define WIFI_SSID "YourWiFi"
   #define WIFI_PASS "YourPassword"
   #define GATEWAY_IP "192.168.1.100"  // 树莓派IP
   #define NODE_ID "ESP32-A01"
   ```
6. 选择开发板: `ESP32 Dev Module`
7. 上传代码到 ESP32

### 4.2 树莓派网关部署

1. 烧录 Raspberry Pi OS (推荐 Lite 版，无桌面环境更轻量)
2. 启用 I2C 和 GPIO：
   ```bash
   sudo raspi-config
   # Interface Options → I2C → Enable
   # Interface Options → SPI → Enable
   ```
3. 安装 Python 依赖：
   ```bash
   pip3 install paho-mqtt RPi.GPIO smbus2
   ```
4. 复制 `scripts/raspberry_pi_gateway.py` 到树莓派
5. 修改配置（MQTT Broker 地址等）
6. 设置开机自启（systemd 服务）：
   ```bash
   sudo tee /etc/systemd/system/farm-gateway.service > /dev/null <<EOF
   [Unit]
   Description=Smart Farm Gateway
   After=network.target

   [Service]
   Type=simple
   User=pi
   ExecStart=/usr/bin/python3 /home/pi/raspberry_pi_gateway.py
   Restart=always
   RestartSec=10

   [Install]
   WantedBy=multi-user.target
   EOF

   sudo systemctl enable farm-gateway
   sudo systemctl start farm-gateway
   ```

### 4.3 云端平台部署

参考 README.md 中的快速启动步骤。

## 5. 网络拓扑

```
WiFi 路由器 (192.168.1.1)
  ├── 树莓派 (192.168.1.100) ← 运行网关 Python 脚本
  │     ├── ESP32-A01 (192.168.1.101) [WiFi STA模式]
  │     └── ESP32-A02 (192.168.1.102) [WiFi STA模式]
  │
  └── 云服务器 / 本机 (192.168.1.200) ← 运行 Spring Boot + EMQX + MySQL + InfluxDB
```

> 在本地开发环境中，树莓派和云端平台可在同一局域网内通信。
> 生产环境中，树莓派通过公网连接云服务器的 MQTT Broker。

## 6. 调试与验证

### 6.1 验证 ESP32 采集

串口监视器（波特率 115200）应看到：
```
[ESP32-A01] DHT22: T=28.50°C H=65.0%
[ESP32-A01] FC-28: soil=35.2%
[ESP32-A01] BH1750: light=12000 lux
[ESP32-A01] MH-Z19B: co2=450 ppm
[ESP32-A01] Data sent to gateway OK
```

### 6.2 验证树莓派 MQTT 上报

在云端服务器订阅 MQTT topic 验证：
```bash
mosquitto_sub -h localhost -t "farm/+/sensor/+/data" -v
```

### 6.3 验证控制闭环

手动触发灌溉 API，观察：
1. 树莓派终端收到 MQTT 指令
2. 继电器 LED 亮起（水阀开启）
3. 灌溉完成后树莓派上报回复
4. 平台 IrrigationLog 状态变为 COMPLETED
