-- ============================================================
-- 智能农场监测系统 - 数据库初始化脚本
-- 包含: 建表语句 + 演示数据
-- 数据库: MySQL 8.0, 字符集 utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_farm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_farm;

-- ---------------- 农场表 ----------------
DROP TABLE IF EXISTS `farm`;
CREATE TABLE `farm` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '农场ID',
    `name`        VARCHAR(100) NOT NULL COMMENT '农场名称',
    `location`    VARCHAR(255)          DEFAULT NULL COMMENT '农场位置',
    `area`        DOUBLE                DEFAULT NULL COMMENT '农场面积(亩)',
    `manager`     VARCHAR(50)           DEFAULT NULL COMMENT '负责人',
    `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '联系电话',
    `status`      INT           NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `remark`      VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农场表';

-- ---------------- 地块表 ----------------
DROP TABLE IF EXISTS `field`;
CREATE TABLE `field` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地块ID',
    `farm_id`          BIGINT       NOT NULL COMMENT '所属农场ID',
    `name`             VARCHAR(100) NOT NULL COMMENT '地块名称',
    `crop`             VARCHAR(50)           DEFAULT NULL COMMENT '种植作物',
    `area`             DOUBLE                DEFAULT NULL COMMENT '地块面积(亩)',
    `soil_type`        VARCHAR(50)           DEFAULT NULL COMMENT '土壤类型',
    `valve_device_id`  VARCHAR(50)           DEFAULT NULL COMMENT '灌溉阀门设备ID',
    `status`           INT          NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_farm_id` (`farm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块表';

-- ---------------- 传感器表 ----------------
DROP TABLE IF EXISTS `sensor`;
CREATE TABLE `sensor` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '传感器ID',
    `farm_id`           BIGINT       NOT NULL COMMENT '所属农场ID',
    `field_id`          BIGINT                DEFAULT NULL COMMENT '所属地块ID',
    `sensor_code`       VARCHAR(50)  NOT NULL COMMENT '传感器编号(硬件上报用)',
    `name`              VARCHAR(100) NOT NULL COMMENT '传感器名称',
    `type`              VARCHAR(30)  NOT NULL COMMENT '传感器类型(temperature/humidity/soil_moisture/light/co2)',
    `model`             VARCHAR(50)           DEFAULT NULL COMMENT '硬件型号',
    `interval_seconds`  INT                   DEFAULT 60 COMMENT '采集间隔(秒)',
    `threshold_max`     DOUBLE                DEFAULT NULL COMMENT '预警上限',
    `threshold_min`     DOUBLE                DEFAULT NULL COMMENT '预警下限',
    `online_status`     INT          NOT NULL DEFAULT 1 COMMENT '在线状态: 0-离线, 1-在线',
    `last_heartbeat`    DATETIME              DEFAULT NULL COMMENT '最后心跳时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_sensor_code` (`sensor_code`),
    INDEX `idx_farm_field` (`farm_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器设备表';

-- ---------------- 灌溉记录表 ----------------
DROP TABLE IF EXISTS `irrigation_log`;
CREATE TABLE `irrigation_log` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `rule_id`                BIGINT                DEFAULT NULL COMMENT '关联灌溉规则ID',
    `farm_id`                BIGINT       NOT NULL COMMENT '农场ID',
    `field_id`               BIGINT       NOT NULL COMMENT '地块ID',
    `valve_device_id`        VARCHAR(50)           DEFAULT NULL COMMENT '阀门设备ID',
    `trigger_type`           INT          NOT NULL DEFAULT 0 COMMENT '触发类型: 0-自动, 1-手动',
    `status`                 INT          NOT NULL DEFAULT 0 COMMENT '状态: 0-待执行, 1-执行中, 2-已完成, 3-失败, 4-已取消',
    `planned_duration`       INT                   DEFAULT NULL COMMENT '计划灌溉时长(秒)',
    `actual_duration`        INT          NOT NULL DEFAULT 0 COMMENT '实际灌溉时长(秒)',
    `trigger_soil_moisture`  DOUBLE                DEFAULT NULL COMMENT '触发时土壤湿度(%)',
    `water_usage`            DOUBLE                DEFAULT NULL COMMENT '用水量(升)',
    `command_id`             VARCHAR(80)           DEFAULT NULL COMMENT 'MQTT指令流水号',
    `start_time`             DATETIME              DEFAULT NULL COMMENT '开始时间',
    `end_time`               DATETIME              DEFAULT NULL COMMENT '结束时间',
    `result_message`         VARCHAR(500)          DEFAULT NULL COMMENT '执行结果消息',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_farm_field` (`farm_id`, `field_id`),
    INDEX `idx_command_id` (`command_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灌溉记录表';

-- ---------------- 灌溉规则表 ----------------
DROP TABLE IF EXISTS `irrigation_rule`;
CREATE TABLE `irrigation_rule` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    `farm_id`                 BIGINT       NOT NULL COMMENT '农场ID',
    `field_id`                BIGINT       NOT NULL COMMENT '地块ID',
    `name`                    VARCHAR(100) NOT NULL COMMENT '规则名称',
    `soil_moisture_threshold` DOUBLE       NOT NULL COMMENT '土壤湿度阈值(%), 低于此值触发',
    `target_soil_moisture`    DOUBLE                DEFAULT NULL COMMENT '目标土壤湿度(%)',
    `allowed_start`           TIME                  DEFAULT NULL COMMENT '允许灌溉开始时间',
    `allowed_end`             TIME                  DEFAULT NULL COMMENT '允许灌溉结束时间',
    `max_duration_seconds`    INT                   DEFAULT 1800 COMMENT '单次灌溉最大时长(秒)',
    `max_daily_count`         INT                   DEFAULT 4 COMMENT '每日最大灌溉次数',
    `skip_if_rain`            INT          NOT NULL DEFAULT 1 COMMENT '雨天跳过: 0-否, 1-是',
    `enabled`                 INT          NOT NULL DEFAULT 1 COMMENT '是否启用: 0-停用, 1-启用',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_farm_field` (`farm_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灌溉规则表';

-- ---------------- 病害识别记录表 ----------------
DROP TABLE IF EXISTS `disease_record`;
CREATE TABLE `disease_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `farm_id`       BIGINT       NOT NULL COMMENT '农场ID',
    `field_id`      BIGINT                DEFAULT NULL COMMENT '地块ID',
    `disease_name`  VARCHAR(100)          DEFAULT NULL COMMENT '病害名称',
    `confidence`    DOUBLE                DEFAULT NULL COMMENT '置信度(0-1)',
    `model_version` VARCHAR(50)           DEFAULT NULL COMMENT 'AI模型版本',
    `image_path`    VARCHAR(255)          DEFAULT NULL COMMENT '上传图片路径',
    `suggestion`    VARCHAR(500)          DEFAULT NULL COMMENT '建议处理方案',
    `source`        VARCHAR(20)           DEFAULT NULL COMMENT '识别来源: local/cloud',
    `detect_time`   DATETIME              DEFAULT NULL COMMENT '识别时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_farm_field` (`farm_id`, `field_id`),
    INDEX `idx_detect_time` (`detect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害识别记录表';

-- ---------------- 告警表 ----------------
DROP TABLE IF EXISTS `alarm`;
CREATE TABLE `alarm` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '告警ID',
    `farm_id`       BIGINT       NOT NULL COMMENT '农场ID',
    `field_id`      BIGINT                DEFAULT NULL COMMENT '地块ID',
    `sensor_id`     BIGINT                DEFAULT NULL COMMENT '传感器ID',
    `level`         INT          NOT NULL COMMENT '告警级别: 1-提示, 2-警告, 3-严重, 4-紧急',
    `type`          VARCHAR(50)  NOT NULL COMMENT '告警类型',
    `title`         VARCHAR(200)          DEFAULT NULL COMMENT '告警标题',
    `content`       VARCHAR(500)          DEFAULT NULL COMMENT '告警内容',
    `handle_status` INT          NOT NULL DEFAULT 0 COMMENT '处理状态: 0-未处理, 1-已处理, 2-已忽略',
    `handler`       VARCHAR(50)           DEFAULT NULL COMMENT '处理人',
    `handle_time`   DATETIME              DEFAULT NULL COMMENT '处理时间',
    `handle_remark` VARCHAR(500)          DEFAULT NULL COMMENT '处理备注',
    `alarm_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_farm_id` (`farm_id`),
    INDEX `idx_alarm_time` (`alarm_time`),
    INDEX `idx_handle_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

-- ============================================================
-- 演示数据
-- ============================================================

-- 农场
INSERT INTO `farm` (`name`, `location`, `area`, `manager`, `phone`, `status`, `remark`) VALUES
('绿源智慧农场', '山东省潍坊市寿光市', 120.5, '张三', '13800138000', 1, '主要种植番茄、黄瓜, 配备IoT监测系统'),
('丰禾示范基地', '山东省聊城市莘县', 85.0, '李四', '13900139000', 1, '蔬菜大棚种植示范基地');

-- 地块
INSERT INTO `field` (`farm_id`, `name`, `crop`, `area`, `soil_type`, `valve_device_id`, `status`) VALUES
(1, 'A区温室1号', '番茄', 2.5, '壤土', 'RELAY-01', 1),
(1, 'A区温室2号', '黄瓜', 2.0, '黏土', 'RELAY-02', 1),
(1, 'B区露天菜地', '白菜', 5.0, '砂壤土', 'RELAY-03', 1),
(2, '1号大棚', '番茄', 1.8, '壤土', 'RELAY-04', 1);

-- 传感器
INSERT INTO `sensor` (`farm_id`, `field_id`, `sensor_code`, `name`, `type`, `model`, `interval_seconds`, `threshold_max`, `threshold_min`, `online_status`) VALUES
(1, 1, 'ESP32-A01-TEMP', 'A区1号温度传感器', 'temperature', 'DHT22', 60, 35.0, 5.0, 1),
(1, 1, 'ESP32-A01-HUMI', 'A区1号湿度传感器', 'humidity', 'DHT22', 60, 90.0, 30.0, 1),
(1, 1, 'ESP32-A01-SOIL', 'A区1号土壤湿度', 'soil_moisture', 'FC-28', 120, 80.0, 20.0, 1),
(1, 1, 'ESP32-A01-LIGHT', 'A区1号光照', 'light', 'BH1750', 60, 100000.0, 1000.0, 1),
(1, 1, 'ESP32-A01-CO2', 'A区1号CO2', 'co2', 'MH-Z19B', 300, 1500.0, 350.0, 1),
(1, 2, 'ESP32-A02-SOIL', 'A区2号土壤湿度', 'soil_moisture', 'FC-28', 120, 80.0, 20.0, 1),
(1, 3, 'ESP32-B01-TEMP', 'B区温度传感器', 'temperature', 'DHT22', 60, 38.0, 0.0, 1),
(1, 3, 'ESP32-B01-SOIL', 'B区土壤湿度', 'soil_moisture', 'FC-28', 120, 80.0, 15.0, 1),
(2, 4, 'ESP32-C01-SOIL', '1号大棚土壤湿度', 'soil_moisture', 'FC-28', 120, 80.0, 20.0, 1);

-- 灌溉规则
INSERT INTO `irrigation_rule` (`farm_id`, `field_id`, `name`, `soil_moisture_threshold`, `target_soil_moisture`, `allowed_start`, `allowed_end`, `max_duration_seconds`, `max_daily_count`, `skip_if_rain`, `enabled`) VALUES
(1, 1, 'A区1号番茄灌溉规则', 25.0, 60.0, '06:00', '22:00', 1800, 4, 1, 1),
(1, 2, 'A区2号黄瓜灌溉规则', 30.0, 65.0, '06:00', '21:00', 1200, 4, 1, 1),
(1, 3, 'B区露天灌溉规则', 20.0, 55.0, '18:00', '23:59', 3600, 2, 1, 1);

-- 病害识别记录
INSERT INTO `disease_record` (`farm_id`, `field_id`, `disease_name`, `confidence`, `model_version`, `image_path`, `suggestion`, `source`, `detect_time`) VALUES
(1, 1, '番茄早疫病', 0.92, 'yolov8n-agri-disease-v1', 'uploaded/20240520_001.jpg', '喷施代森锰锌或苯醚甲环唑, 清除病叶, 加强通风', 'local', '2024-05-20 10:30:00'),
(1, 2, '黄瓜白粉病', 0.87, 'yolov8n-agri-disease-v1', 'uploaded/20240521_001.jpg', '喷施醚菌酯或吡唑醚菌酯, 降低棚内湿度', 'local', '2024-05-21 14:15:00');

-- 告警记录
INSERT INTO `alarm` (`farm_id`, `field_id`, `sensor_id`, `level`, `type`, `title`, `content`, `handle_status`, `alarm_time`) VALUES
(1, 1, 1, 2, 'SENSOR_THRESHOLD_MAX', '温度超上限告警', 'A区1号温度传感器 当前值 36.5 超过上限阈值 35.0', 1, '2024-05-20 13:00:00'),
(1, 1, 3, 3, 'SENSOR_THRESHOLD_MIN', '土壤湿度低于下限告警', 'A区1号土壤湿度 当前值 18.0 低于下限阈值 20.0', 0, '2024-05-20 14:00:00'),
(1, 3, 7, 4, 'DEVICE_OFFLINE', '设备离线告警', '传感器 ESP32-B01-TEMP 已超过10分钟未上报数据, 疑似离线', 0, '2024-05-20 15:30:00');
