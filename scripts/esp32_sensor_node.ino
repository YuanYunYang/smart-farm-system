/*
 * ============================================================
 * ESP32 智能农场传感器采集节点
 * ============================================================
 * 功能:
 *   - 读取 DHT22 温湿度传感器
 *   - 读取 FC-28 土壤湿度传感器 (模拟量)
 *   - 读取 BH1750 光照强度传感器 (I2C)
 *   - 读取 MH-Z19B CO2 传感器 (UART)
 *   - 通过 HTTP POST 将数据发送到树莓派网关
 *   - 支持 Deep Sleep 低功耗模式
 *
 * 硬件接线 (ESP32 DevKit V1):
 *   DHT22  DATA → GPIO 4
 *   FC-28  A0   → GPIO 36 (ADC1_CH0)
 *   BH1750 SDA  → GPIO 21 (I2C SDA)
 *   BH1750 SCL  → GPIO 22 (I2C SCL)
 *   MH-Z19B TX  → GPIO 16 (RX2)
 *   MH-Z19B RX  → GPIO 17 (TX2)
 *
 * 依赖库 (Arduino IDE 库管理器安装):
 *   - DHT sensor library (by Adafruit)
 *   - BH1750 (by Christopher Laws)
 *   - ArduinoJson (by Benoit Blanchon)
 *
 * 作者: Smart Farm Team
 * ============================================================
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <BH1750.h>
#include <Wire.h>

// ==================== 配置区 (按需修改) ====================

// WiFi 配置
#define WIFI_SSID     "YourWiFi"
#define WIFI_PASS     "YourPassword"

// 树莓派网关地址 (HTTP)
#define GATEWAY_IP    "192.168.1.100"
#define GATEWAY_PORT  8080
#define GATEWAY_PATH  "/sensor/upload"

// 节点配置
#define NODE_ID       "ESP32-A01"
#define FARM_ID       1
#define FIELD_ID      1

// 传感器引脚
#define DHT_PIN       4         // DHT22 数据引脚
#define DHT_TYPE      DHT22     // 传感器型号
#define SOIL_ADC_PIN  36        // FC-28 模拟输出 (ADC1_CH0)
#define CO2_RX_PIN    16        // MH-Z19B TX → ESP32 RX2
#define CO2_TX_PIN    17        // MH-Z19B RX → ESP32 TX2

// 采集间隔 (毫秒)
#define INTERVAL_MS   60000     // 60秒采集一次

// 是否启用 Deep Sleep (低功耗, 电池供电时开启)
#define ENABLE_DEEP_SLEEP false

// ==================== 全局对象 ====================

DHT dht(DHT_PIN, DHT_TYPE);
BH1750 lightMeter;

// MH-Z19B CO2 传感器 (使用 Serial2)
HardwareSerial co2Serial(2);

// ==================== 初始化 ====================

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println("====================================");
    Serial.println("  Smart Farm ESP32 Sensor Node");
    Serial.println("  Node ID: " NODE_ID);
    Serial.println("====================================");

    // 初始化 I2C (BH1750)
    Wire.begin(21, 22);  // SDA=21, SCL=22

    // 初始化传感器
    dht.begin();

    if (lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE)) {
        Serial.println("[BH1750] 初始化成功");
    } else {
        Serial.println("[BH1750] 初始化失败, 请检查接线!");
    }

    // 初始化 MH-Z19B (UART, 波特率 9600)
    co2Serial.begin(9600, SERIAL_8N1, CO2_RX_PIN, CO2_TX_PIN);
    Serial.println("[MH-Z19B] 串口初始化完成");

    // 连接 WiFi
    connectWiFi();

    Serial.println("初始化完成, 开始采集...");
    Serial.println("------------------------------------");
}

// ==================== 主循环 ====================

void loop() {
    // 采集传感器数据
    SensorData data = readAllSensors();

    // 打印数据
    printSensorData(data);

    // 发送到树莓派网关
    if (WiFi.status() == WL_CONNECTED) {
        sendDataToGateway(data);
    } else {
        Serial.println("[WARN] WiFi 未连接, 尝试重连...");
        connectWiFi();
    }

    Serial.println("------------------------------------");

    // 等待下一次采集
#if ENABLE_DEEP_SLEEP
    Serial.println("进入 Deep Sleep...");
    esp_deep_sleep(INTERVAL_MS * 1000);  // 微秒
#else
    delay(INTERVAL_MS);
#endif
}

// ==================== 传感器读取函数 ====================

// 传感器数据结构
struct SensorData {
    float temperature;
    float humidity;
    float soilMoisture;
    float light;
    int co2;
    float battery;
};

/**
 * 读取所有传感器
 */
SensorData readAllSensors() {
    SensorData data;

    // 1. DHT22 温湿度
    float h = dht.readHumidity();
    float t = dht.readTemperature();
    if (isnan(h) || isnan(t)) {
        Serial.println("[DHT22] 读取失败!");
        data.temperature = -999;
        data.humidity = -999;
    } else {
        data.temperature = t;
        data.humidity = h;
    }

    // 2. FC-28 土壤湿度 (模拟量 → 百分比)
    int soilRaw = analogRead(SOIL_ADC_PIN);
    // 12位ADC: 0-4095
    // 干燥: ~4095, 水中: ~1200 (校准值需根据实际情况调整)
    // 映射到 0-100%
    int dryValue = 4095;
    int wetValue = 1200;
    int soilPercent = map(soilRaw, dryValue, wetValue, 0, 100);
    soilPercent = constrain(soilPercent, 0, 100);
    data.soilMoisture = (float)soilPercent;

    // 3. BH1750 光照
    float lux = lightMeter.readLightLevel();
    if (lux < 0) {
        Serial.println("[BH1750] 读取失败!");
        data.light = -1;
    } else {
        data.light = lux;
    }

    // 4. MH-Z19B CO2
    data.co2 = readCO2();

    // 5. 电池电压 (ESP32 ADC, 可选)
    // 如有电池供电, 读取分压后的电压值
    data.battery = 0.0;

    return data;
}

/**
 * 读取 MH-Z19B CO2 浓度
 * 使用标准 9 字节读取命令
 */
int readCO2() {
    // 发送读取命令
    byte cmd[9] = {0xFF, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79};
    co2Serial.write(cmd, 9);

    // 等待响应
    delay(100);

    // 读取返回数据 (9字节)
    byte response[9];
    memset(response, 0, sizeof(response));

    if (co2Serial.available() >= 9) {
        co2Serial.readBytes(response, 9);

        // 校验和验证
        byte checksum = 0;
        for (int i = 1; i < 8; i++) {
            checksum += response[i];
        }
        checksum = 0xFF - checksum + 1;

        if (response[0] == 0xFF && response[1] == 0x86 && response[8] == checksum) {
            // CO2 浓度 = 高字节 * 256 + 低字节
            int co2 = (int)response[2] * 256 + (int)response[3];
            return co2;
        } else {
            Serial.println("[MH-Z19B] 校验失败!");
            return -1;
        }
    } else {
        Serial.println("[MH-Z19B] 响应超时!");
        return -1;
    }
}

// ==================== 数据发送函数 ====================

/**
 * 发送传感器数据到树莓派网关 (HTTP POST)
 */
void sendDataToGateway(SensorData data) {
    HTTPClient http;

    String url = "http://" + String(GATEWAY_IP) + ":" + String(GATEWAY_PORT) + GATEWAY_PATH;
    Serial.println("[HTTP] POST → " + url);

    http.begin(url);
    http.addHeader("Content-Type", "application/json");

    // 构建 JSON (使用 ArduinoJson)
    // 格式与平台 SensorDataDTO 一致
    JsonDocument doc;
    doc["sensorId"] = NODE_ID;
    doc["farmId"] = FARM_ID;
    doc["fieldId"] = FIELD_ID;
    doc["timestamp"] = millis();

    JsonObject dataObj = doc["data"].to<JsonObject>();
    dataObj["temperature"] = data.temperature;
    dataObj["humidity"] = data.humidity;
    dataObj["soil_moisture"] = data.soilMoisture;
    dataObj["light"] = data.light;
    dataObj["co2"] = (double)data.co2;

    doc["battery"] = data.battery;

    String jsonStr;
    serializeJson(doc, jsonStr);
    Serial.println("[HTTP] Payload: " + jsonStr);

    int httpCode = http.POST(jsonStr);

    if (httpCode > 0) {
        String response = http.getString();
        Serial.printf("[HTTP] 响应码: %d\n", httpCode);
        Serial.println("[HTTP] 响应体: " + response);
    } else {
        Serial.printf("[HTTP] 请求失败: %s\n", http.errorToString(httpCode).c_str());
    }

    http.end();
}

// ==================== 辅助函数 ====================

/**
 * 连接 WiFi
 */
void connectWiFi() {
    Serial.printf("[WiFi] 连接 %s", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    int retry = 0;
    while (WiFi.status() != WL_CONNECTED && retry < 20) {
        delay(500);
        Serial.print(".");
        retry++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println();
        Serial.printf("[WiFi] 连接成功! IP: %s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.println();
        Serial.println("[WiFi] 连接失败!");
    }
}

/**
 * 打印传感器数据到串口
 */
void printSensorData(SensorData data) {
    Serial.println("┌─────────────────────────────────┐");
    Serial.printf ("│ [%s] 传感器数据\n", NODE_ID);
    Serial.println("├─────────────────────────────────┤");
    Serial.printf ("│ 温度:     %.2f °C\n", data.temperature);
    Serial.printf ("│ 湿度:     %.1f %%\n", data.humidity);
    Serial.printf ("│ 土壤湿度: %.1f %%\n", data.soilMoisture);
    Serial.printf ("│ 光照:     %.0f lux\n", data.light);
    Serial.printf ("│ CO2:      %d ppm\n", data.co2);
    Serial.printf ("│ 电池:     %.2f V\n", data.battery);
    Serial.println("└─────────────────────────────────┘");
}
