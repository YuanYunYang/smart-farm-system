#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================
 智能农场 - 树莓派边缘网关
============================================================
功能:
  - HTTP Server: 接收 ESP32 节点上报的传感器数据
  - MQTT Publisher: 聚合数据后通过 MQTT 上报到云端平台
  - MQTT Subscriber: 接收云端下发的灌溉控制指令
  - GPIO 控制: 驱动继电器开关水阀
  - 控制回复: 灌溉完成后通过 MQTT 回复执行结果

依赖安装:
  pip3 install paho-mqtt RPi.GPIO

运行方式:
  python3 raspberry_pi_gateway.py

或设为 systemd 服务自启 (见 docs/hardware-setup.md)

作者: Smart Farm Team
============================================================
"""

import json
import time
import threading
import socket
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime, timezone

# MQTT 客户端
import paho.mqtt.client as mqtt

# 树莓派 GPIO (在非树莓派环境下用 mock 避免 import 错误)
try:
    import RPi.GPIO as GPIO
    GPIO_AVAILABLE = True
except ImportError:
    GPIO_AVAILABLE = False
    print("[WARN] RPi.GPIO 不可用 (非树莓派环境), GPIO 功能将使用模拟模式")
    print("[WARN] 请在树莓派上运行此脚本以启用 GPIO 控制")


# ==================== 配置区 (按需修改) ====================

# MQTT Broker 配置
MQTT_BROKER = "localhost"          # 云端 EMQX 地址
MQTT_PORT = 1883
MQTT_USER = ""
MQTT_PASS = ""
MQTT_CLIENT_ID = f"raspberry-gateway-{socket.gethostname()}"

# 网关配置
GATEWAY_ID = "RPI-GW-01"
GATEWAY_HTTP_PORT = 8080            # ESP32 上报数据的 HTTP 端口

# GPIO 配置
RELAY_PIN = 17                      # 继电器控制引脚 (BCM 编号)

# 农场ID (本网关管理的农场)
FARM_ID = 1

# ==================== GPIO 控制 ====================

class RelayController:
    """继电器控制器 - 控制水阀开关"""

    def __init__(self, pin):
        self.pin = pin
        self.is_on = False
        self.irrigation_timer = None
        self.current_command_id = None

        if GPIO_AVAILABLE:
            GPIO.setmode(GPIO.BCM)
            GPIO.setup(self.pin, GPIO.OUT)
            GPIO.output(self.pin, GPIO.HIGH)  # 继电器默认关闭 (低电平触发, HIGH=关)
            print(f"[GPIO] 继电器初始化: PIN={pin}, 状态=关闭")

    def open_valve(self, duration_seconds, command_id):
        """开启水阀 (灌溉)"""
        self.current_command_id = command_id
        self.is_on = True
        self.irrigation_start = time.time()

        if GPIO_AVAILABLE:
            GPIO.output(self.pin, GPIO.LOW)  # 低电平触发继电器闭合
        print(f"[GPIO] 水阀开启 → 灌溉中, 计划时长={duration_seconds}s, commandId={command_id}")

        # 启动定时器, 到时自动关闭
        self.irrigation_timer = threading.Timer(
            duration_seconds,
            self.close_valve,
            args=[duration_seconds, True]
        )
        self.irrigation_timer.start()

    def close_valve(self, planned_duration, success=True):
        """关闭水阀"""
        actual_duration = int(time.time() - self.irrigation_start) if hasattr(self, 'irrigation_start') else planned_duration

        self.is_on = False
        if GPIO_AVAILABLE:
            GPIO.output(self.pin, GPIO.HIGH)  # 高电平, 继电器断开

        # 计算用水量 (经验: 流量约 1.5L/min, 水压相关)
        water_usage = round(actual_duration / 60 * 1.5, 2)

        print(f"[GPIO] 水阀关闭 ← 灌溉完成, 实际时长={actual_duration}s, 用水量={water_usage}L")

        # 回复云端平台
        mqtt_manager.send_control_response(
            command_id=self.current_command_id,
            success=success,
            actual_duration=actual_duration,
            water_usage=water_usage,
            message="灌溉执行完成" if success else "灌溉执行异常"
        )

        self.current_command_id = None
        if self.irrigation_timer:
            self.irrigation_timer.cancel()
            self.irrigation_timer = None

    def cleanup(self):
        """清理 GPIO 资源"""
        if GPIO_AVAILABLE:
            GPIO.output(self.pin, GPIO.HIGH)
            GPIO.cleanup()
            print("[GPIO] 资源已清理")


# ==================== MQTT 通信管理 ====================

class MqttManager:
    """MQTT 通信管理器 - 数据上报 + 指令接收"""

    def __init__(self):
        self.client = mqtt.Client(client_id=MQTT_CLIENT_ID)
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect

        if MQTT_USER:
            self.client.username_pw_set(MQTT_USER, MQTT_PASS)

        # 自动重连
        self.client.reconnect_delay_set(min_delay=5, max_delay=60)

        self.relay = None  # 由外部设置

    def set_relay_controller(self, relay):
        self.relay = relay

    def connect(self):
        """连接 MQTT Broker"""
        try:
            print(f"[MQTT] 连接 Broker: {MQTT_BROKER}:{MQTT_PORT}")
            self.client.connect(MQTT_BROKER, MQTT_PORT, 60)
            # 启动后台循环
            self.client.loop_start()
        except Exception as e:
            print(f"[MQTT] 连接失败: {e}")
            print("[MQTT] 5秒后重试...")
            time.sleep(5)
            self.connect()

    def _on_connect(self, client, userdata, flags, rc):
        """连接成功回调"""
        if rc == 0:
            print("[MQTT] 连接成功!")
            # 订阅控制指令 topic
            control_topic = f"farm/{FARM_ID}/control/req"
            client.subscribe(control_topic, qos=1)
            print(f"[MQTT] 订阅 topic: {control_topic}")
        else:
            print(f"[MQTT] 连接失败, 返回码: {rc}")

    def _on_disconnect(self, client, userdata, rc):
        """断开连接回调"""
        print(f"[MQTT] 连接断开, rc={rc}, 将自动重连...")

    def _on_message(self, client, userdata, msg):
        """消息到达回调 - 处理控制指令"""
        topic = msg.topic
        payload = msg.payload.decode('utf-8')
        print(f"\n[MQTT] 收到消息: topic={topic}")
        print(f"[MQTT] 消息内容: {payload}")

        try:
            command = json.loads(payload)
            action = command.get('action', '')

            if action == 'OPEN':
                # 开启灌溉
                field_id = command.get('fieldId')
                duration = command.get('duration', 300)
                command_id = command.get('commandId', f"IRR-{int(time.time())}")

                print(f"[CMD] 灌溉指令: fieldId={field_id}, duration={duration}s, commandId={command_id}")

                if self.relay and self.relay.is_on:
                    print("[CMD] 水阀已开启, 忽略重复指令")
                    self.send_control_response(command_id, False, 0, 0, "水阀已开启, 忽略重复指令")
                elif self.relay:
                    self.relay.open_valve(duration, command_id)
                else:
                    print("[CMD] 继电器未初始化, 无法执行")

            elif action == 'CLOSE':
                # 紧急关闭
                command_id = command.get('commandId', 'CLOSE')
                print(f"[CMD] 紧急关闭指令: commandId={command_id}")
                if self.relay:
                    self.relay.close_valve(0, False)

            else:
                print(f"[CMD] 未知动作: {action}")

        except json.JSONDecodeError as e:
            print(f"[MQTT] JSON 解析失败: {e}")
        except Exception as e:
            print(f"[MQTT] 消息处理异常: {e}")

    def publish_sensor_data(self, sensor_id, farm_id, field_id, data, battery=None):
        """上报传感器数据到云端"""
        topic = f"farm/{farm_id}/sensor/{sensor_id}/data"
        payload = {
            "farmId": farm_id,
            "fieldId": field_id,
            "sensorId": sensor_id,
            "timestamp": int(time.time() * 1000),
            "data": data,
        }
        if battery is not None:
            payload["battery"] = battery

        payload_str = json.dumps(payload)
        self.client.publish(topic, payload_str, qos=1)
        print(f"[MQTT] 上报数据 → topic={topic}")
        print(f"[MQTT] 数据内容: {payload_str}")

    def send_control_response(self, command_id, success, actual_duration, water_usage, message):
        """发送控制回复到云端"""
        topic = f"farm/{FARM_ID}/control/resp"
        payload = {
            "commandId": command_id,
            "success": success,
            "actualDuration": actual_duration,
            "waterUsage": water_usage,
            "message": message,
            "timestamp": int(time.time() * 1000),
        }
        payload_str = json.dumps(payload)
        self.client.publish(topic, payload_str, qos=1)
        print(f"[MQTT] 控制回复 → topic={topic}")
        print(f"[MQTT] 回复内容: {payload_str}")

    def send_heartbeat(self):
        """发送设备心跳"""
        topic = f"farm/{FARM_ID}/device/status"
        payload = {
            "gatewayId": GATEWAY_ID,
            "status": 1,
            "timestamp": int(time.time() * 1000),
        }
        self.client.publish(topic, json.dumps(payload), qos=0)


# ==================== HTTP Server (接收 ESP32 数据) ====================

class SensorDataHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器 - 接收 ESP32 上报的传感器数据"""

    def do_POST(self):
        """处理 POST 请求"""
        if self.path == '/sensor/upload':
            content_length = int(self.headers['Content-Length'])
            body = self.rfile.read(content_length).decode('utf-8')

            try:
                data = json.loads(body)
                print(f"\n[HTTP] 收到 ESP32 数据: {body}")

                # 提取数据
                sensor_id = data.get('sensorId', 'unknown')
                farm_id = data.get('farmId', FARM_ID)
                field_id = data.get('fieldId')
                sensor_data = data.get('data', {})
                battery = data.get('battery')

                # 通过 MQTT 上报到云端
                mqtt_manager.publish_sensor_data(
                    sensor_id, farm_id, field_id, sensor_data, battery
                )

                # 返回成功响应
                response = {"code": 200, "msg": "数据已接收并上报"}
                self._send_json(200, response)

            except json.JSONDecodeError:
                self._send_json(400, {"code": 400, "msg": "JSON 格式错误"})
            except Exception as e:
                print(f"[HTTP] 处理异常: {e}")
                self._send_json(500, {"code": 500, "msg": str(e)})
        else:
            self._send_json(404, {"code": 404, "msg": "路径不存在"})

    def do_GET(self):
        """健康检查"""
        if self.path == '/health':
            self._send_json(200, {
                "gateway": GATEWAY_ID,
                "status": "running",
                "mqtt_connected": mqtt_manager.client.is_connected(),
                "relay_on": relay_controller.is_on if relay_controller else False,
                "timestamp": datetime.now(timezone.utc).isoformat()
            })
        else:
            self._send_json(404, {"msg": "not found"})

    def _send_json(self, code, data):
        """发送 JSON 响应"""
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode('utf-8'))

    def log_message(self, format, *args):
        """抑制默认日志输出"""
        pass


# ==================== 全局实例 ====================

mqtt_manager = MqttManager()
relay_controller = RelayController(RELAY_PIN) if True else None


# ==================== 心跳线程 ====================

def heartbeat_thread():
    """后台线程: 每60秒发送设备心跳"""
    while True:
        try:
            if mqtt_manager.client.is_connected():
                mqtt_manager.send_heartbeat()
                print(f"[Heartbeat] 心跳已发送 ({datetime.now().strftime('%H:%M:%S')})")
        except Exception as e:
            print(f"[Heartbeat] 心跳发送失败: {e}")
        time.sleep(60)


# ==================== 主入口 ====================

def main():
    print("=" * 60)
    print("  Smart Farm - Raspberry Pi Gateway")
    print(f"  Gateway ID: {GATEWAY_ID}")
    print(f"  MQTT Broker: {MQTT_BROKER}:{MQTT_PORT}")
    print(f"  HTTP Server Port: {GATEWAY_HTTP_PORT}")
    print(f"  Relay PIN: {RELAY_PIN} (BCM)")
    print(f"  GPIO Available: {GPIO_AVAILABLE}")
    print("=" * 60)

    # 设置继电器控制器
    mqtt_manager.set_relay_controller(relay_controller)

    # 连接 MQTT
    mqtt_manager.connect()
    time.sleep(2)  # 等待连接稳定

    # 启动心跳线程
    hb_thread = threading.Thread(target=heartbeat_thread, daemon=True)
    hb_thread.start()
    print("[Main] 心跳线程已启动")

    # 启动 HTTP Server 接收 ESP32 数据
    server = HTTPServer(('0.0.0.0', GATEWAY_HTTP_PORT), SensorDataHandler)
    print(f"[Main] HTTP Server 启动: 0.0.0.0:{GATEWAY_HTTP_PORT}")
    print(f"[Main] ESP32 数据上报地址: http://<树莓派IP>:{GATEWAY_HTTP_PORT}/sensor/upload")
    print("[Main] 等待 ESP32 节点数据...\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[Main] 收到退出信号, 正在关闭...")
    finally:
        server.server_close()
        mqtt_manager.client.loop_stop()
        mqtt_manager.client.disconnect()
        if relay_controller:
            relay_controller.cleanup()
        print("[Main] 网关已关闭")


if __name__ == '__main__':
    main()
