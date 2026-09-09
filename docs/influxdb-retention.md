# InfluxDB 数据保留策略

## 策略说明

| Bucket | 保留时长 | 用途 |
|--------|----------|------|
| sensor_data | 30 天 | 原始传感器数据（10s 粒度） |
| sensor_data_1h | 365 天 | 1 小时聚合数据 |
| sensor_data_1d | 3 年 | 1 天聚合数据 |

## 初始化命令

```bash
# 创建原始数据 bucket（30 天保留）
influx bucket create --name sensor_data --retention 30d

# 创建 1 小时聚合 bucket（1 年保留）
influx bucket create --name sensor_data_1h --retention 365d

# 创建 1 天聚合 bucket（3 年保留）
influx bucket create --name sensor_data_1d --retention 1095d
```

## Continuous Query 配置

```flux
// 1 小时聚合
from(bucket: "sensor_data")
  |> range(start: -1h)
  |> aggregateWindow(every: 1h, fn: mean)
  |> to(bucket: "sensor_data_1h")

// 1 天聚合
from(bucket: "sensor_data_1h")
  |> range(start: -1d)
  |> aggregateWindow(every: 1d, fn: mean)
  |> to(bucket: "sensor_data_1d")
```
