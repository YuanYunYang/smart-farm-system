package com.farm.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.smart.model.dto.IrrigationRuleDTO;
import com.farm.smart.model.entity.*;
import com.farm.smart.model.enums.IrrigationStatus;
import com.farm.smart.repository.*;
import com.farm.smart.service.AlarmService;
import com.farm.smart.service.IrrigationService;
import com.farm.smart.service.MqttMessageService;
import com.farm.smart.service.SensorDataService;
import com.farm.smart.model.enums.SensorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灌溉服务实现 - 自动灌溉策略引擎
 * <p>
 * 核心逻辑:
 * 1. 定时检查所有启用的灌溉规则 (每5分钟)
 * 2. 条件判断: 土壤湿度 < 阈值 && 当前时间在允许时段 && 未超日次数 && 无正在进行的灌溉
 * 3. 安全限制: 单次最大时长限制、雨天跳过
 * 4. 通过 MQTT 下发指令到树莓派 -> 控制继电器 -> 开启水阀
 * 5. 设备回复后更新灌溉记录
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrrigationServiceImpl implements IrrigationService {

    private final IrrigationRuleMapper ruleMapper;
    private final IrrigationLogMapper logMapper;
    private final FieldMapper fieldMapper;
    private final MqttMessageService mqttMessageService;
    @Lazy
    private final SensorDataService sensorDataService;
    private final AlarmService alarmService;

    @Override
    public void checkAndTriggerIrrigation() {
        log.info("=== 开始检查灌溉规则 ===");
        List<IrrigationRule> rules = ruleMapper.selectEnabledRules();
        log.info("查询到 {} 条启用的灌溉规则", rules.size());

        int triggeredCount = 0;
        int skippedCount = 0;

        for (IrrigationRule rule : rules) {
            try {
                IrrigationCheckResult result = evaluateRule(rule);
                if (result.shouldTrigger()) {
                    triggerIrrigation(rule, result.getSoilMoisture(), 0);
                    triggeredCount++;
                } else if (result.getSkipReason() != null) {
                    log.debug("规则 {} 跳过: {}", rule.getName(), result.getSkipReason());
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("检查灌溉规则异常: ruleId={}, error={}", rule.getId(), e.getMessage(), e);
            }
        }

        log.info("=== 灌溉规则检查完成: 触发={}, 跳过={}, 总计={} ===",
                triggeredCount, skippedCount, rules.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IrrigationLog manualTrigger(Long fieldId, Integer durationSeconds) {
        Field field = fieldMapper.selectById(fieldId);
        if (field == null) {
            throw new IllegalArgumentException("地块不存在: id=" + fieldId);
        }

        // 手动触发不检查规则限制, 但仍需检查是否有正在进行的灌溉
        List<IrrigationLog> running = logMapper.selectRunningByFieldId(fieldId);
        if (!running.isEmpty()) {
            throw new IllegalStateException("地块已有正在进行的灌溉, 无法重复触发");
        }

        return triggerIrrigation(null, null, 1, fieldId, durationSeconds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleIrrigationResponse(String commandId, Boolean success,
                                          Integer actualDuration, Double waterUsage,
                                          String message) {
        log.info("收到灌溉执行回复: commandId={}, success={}, duration={}s, water={}L",
                commandId, success, actualDuration, waterUsage);

        IrrigationLog logRecord = logMapper.selectByCommandId(commandId);
        if (logRecord == null) {
            log.warn("未找到灌溉记录: commandId={}", commandId);
            return;
        }

        // 更新灌溉记录
        logRecord.setStatus(success ? IrrigationStatus.COMPLETED : IrrigationStatus.FAILED);
        logRecord.setActualDuration(actualDuration);
        logRecord.setWaterUsage(waterUsage);
        logRecord.setEndTime(LocalDateTime.now());
        logRecord.setResultMessage(message);
        logMapper.updateById(logRecord);

        // 如果灌溉失败, 触发告警
        if (Boolean.FALSE.equals(success)) {
            alarmService.irrigationErrorAlarm(logRecord.getFarmId(), logRecord.getFieldId(),
                    "灌溉执行失败: " + message);
        }

        log.info("灌溉记录更新完成: logId={}, status={}", logRecord.getId(), logRecord.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IrrigationRule createRule(IrrigationRuleDTO dto) {
        IrrigationRule rule = new IrrigationRule();
        rule.setFarmId(dto.getFarmId());
        rule.setFieldId(dto.getFieldId());
        rule.setName(dto.getName());
        rule.setSoilMoistureThreshold(dto.getSoilMoistureThreshold());
        rule.setTargetSoilMoisture(dto.getTargetSoilMoisture());
        rule.setAllowedStart(dto.getAllowedStart());
        rule.setAllowedEnd(dto.getAllowedEnd());
        rule.setMaxDurationSeconds(dto.getMaxDurationSeconds() != null ? dto.getMaxDurationSeconds() : 1800);
        rule.setMaxDailyCount(dto.getMaxDailyCount() != null ? dto.getMaxDailyCount() : 4);
        rule.setSkipIfRain(dto.getSkipIfRain() != null ? dto.getSkipIfRain() : 1);
        rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : 1);
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());

        ruleMapper.insert(rule);
        log.info("创建灌溉规则: id={}, name={}, threshold={}%", rule.getId(), rule.getName(),
                rule.getSoilMoistureThreshold());
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IrrigationRule updateRule(IrrigationRule rule) {
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("更新灌溉规则: id={}", rule.getId());
        return rule;
    }

    @Override
    public List<IrrigationRule> listRules(Long farmId) {
        return ruleMapper.selectByFarmId(farmId);
    }

    @Override
    public boolean deleteRule(Long id) {
        int result = ruleMapper.deleteById(id);
        return result > 0;
    }

    @Override
    public List<IrrigationLog> listLogs(Long farmId, Long fieldId) {
        LambdaQueryWrapper<IrrigationLog> wrapper = new LambdaQueryWrapper<>();
        if (farmId != null) {
            wrapper.eq(IrrigationLog::getFarmId, farmId);
        }
        if (fieldId != null) {
            wrapper.eq(IrrigationLog::getFieldId, fieldId);
        }
        wrapper.orderByDesc(IrrigationLog::getCreateTime);
        return logMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getWeeklyStats(Long farmId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7);

        Map<String, Object> stats = logMapper.weeklyStats(farmId, weekStart, now);
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("count", 0);
            stats.put("totalWater", 0.0);
        }

        log.info("本周灌溉统计: farmId={}, stats={}", farmId, stats);
        return stats;
    }

    // ==================== 核心私有方法 ====================

    /**
     * 评估灌溉规则是否满足触发条件
     * 完整的条件判断逻辑
     */
    private IrrigationCheckResult evaluateRule(IrrigationRule rule) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();

        // 1. 检查允许灌溉时段
        if (rule.getAllowedStart() != null && rule.getAllowedEnd() != null) {
            if (currentTime.isBefore(rule.getAllowedStart()) || currentTime.isAfter(rule.getAllowedEnd())) {
                return IrrigationCheckResult.skip(String.format(
                        "当前时间 %s 不在允许灌溉时段 [%s - %s]",
                        currentTime, rule.getAllowedStart(), rule.getAllowedEnd()));
            }
        }

        // 2. 检查当天灌溉次数
        LocalDate today = now.toLocalDate();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(23, 59, 59);
        int todayCount = logMapper.countTodayByFieldId(rule.getFieldId(), dayStart, dayEnd);
        if (rule.getMaxDailyCount() != null && todayCount >= rule.getMaxDailyCount()) {
            return IrrigationCheckResult.skip(String.format(
                    "地块 %d 今日已灌溉 %d 次, 超出每日最大限制 %d 次",
                    rule.getFieldId(), todayCount, rule.getMaxDailyCount()));
        }

        // 3. 检查是否有正在进行的灌溉
        List<IrrigationLog> running = logMapper.selectRunningByFieldId(rule.getFieldId());
        if (!running.isEmpty()) {
            return IrrigationCheckResult.skip(String.format(
                    "地块 %d 已有正在进行的灌溉 (logId=%d)", rule.getFieldId(), running.get(0).getId()));
        }

        // 4. 雨天跳过检查 (模拟: 通过天气API或雨量传感器判断)
        if (rule.getSkipIfRain() != null && rule.getSkipIfRain() == 1) {
            boolean isRaining = checkWeatherRain(rule.getFarmId());
            if (isRaining) {
                return IrrigationCheckResult.skip("当前正在下雨, 跳过灌溉 (规则配置雨天跳过)");
            }
        }

        // 5. 获取当前土壤湿度
        Double soilMoisture = getSoilMoisture(rule.getFarmId(), rule.getFieldId());
        if (soilMoisture == null) {
            return IrrigationCheckResult.skip("无法获取当前土壤湿度数据, 传感器可能离线");
        }

        // 6. 核心判断: 土壤湿度是否低于阈值
        if (soilMoisture >= rule.getSoilMoistureThreshold()) {
            return IrrigationCheckResult.skip(String.format(
                    "土壤湿度 %.1f%% 高于阈值 %.1f%%, 无需灌溉",
                    soilMoisture, rule.getSoilMoistureThreshold()));
        }

        // 条件满足, 返回触发
        return IrrigationCheckResult.trigger(soilMoisture);
    }

    /**
     * 触发灌溉 (自动)
     */
    private IrrigationLog triggerIrrigation(IrrigationRule rule, Double soilMoisture,
                                             Integer triggerType) {
        return triggerIrrigation(rule, soilMoisture, triggerType, rule.getFieldId(), null);
    }

    /**
     * 触发灌溉 (核心方法, 自动/手动共用)
     */
    private IrrigationLog triggerIrrigation(IrrigationRule rule, Double soilMoisture,
                                             Integer triggerType, Long fieldId,
                                             Integer manualDuration) {
        Field field = fieldMapper.selectById(fieldId);
        if (field == null) {
            throw new IllegalArgumentException("地块不存在: id=" + fieldId);
        }

        // 计算灌溉时长
        int duration;
        if (manualDuration != null) {
            // 手动触发: 使用指定时长
            duration = manualDuration;
        } else if (rule != null) {
            // 自动触发: 根据土壤湿度缺口计算
            duration = calculateIrrigationDuration(soilMoisture, rule.getTargetSoilMoisture(),
                    rule.getMaxDurationSeconds());
        } else {
            duration = 300; // 默认5分钟
        }

        // 安全限制: 不超过最大时长
        if (rule != null && rule.getMaxDurationSeconds() != null && duration > rule.getMaxDurationSeconds()) {
            log.info("灌溉时长 {} 超出最大限制 {}, 自动截断", duration, rule.getMaxDurationSeconds());
            duration = rule.getMaxDurationSeconds();
        }

        // 生成指令流水号
        String commandId = mqttMessageService.sendIrrigationCommand(
                field.getFarmId(), field.getId(), field.getValveDeviceId(), "OPEN", duration);

        // 创建灌溉记录
        IrrigationLog logRecord = new IrrigationLog();
        logRecord.setRuleId(rule != null ? rule.getId() : null);
        logRecord.setFarmId(field.getFarmId());
        logRecord.setFieldId(field.getId());
        logRecord.setValveDeviceId(field.getValveDeviceId());
        logRecord.setTriggerType(triggerType);
        logRecord.setStatus(IrrigationStatus.PENDING);
        logRecord.setPlannedDuration(duration);
        logRecord.setActualDuration(0);
        logRecord.setTriggerSoilMoisture(soilMoisture);
        logRecord.setCommandId(commandId);
        logRecord.setStartTime(LocalDateTime.now());
        logRecord.setCreateTime(LocalDateTime.now());
        logMapper.insert(logRecord);

        log.info("灌溉已触发: fieldId={}, duration={}s, soilMoisture={}%, triggerType={}, commandId={}",
                fieldId, duration, soilMoisture, triggerType == 0 ? "自动" : "手动", commandId);

        return logRecord;
    }

    /**
     * 计算灌溉时长
     * 基于土壤湿度缺口估算:
     * - 缺口越大, 灌溉时间越长
     * - 经验公式: duration = (target - current) / rate * 60
     *   其中 rate 为每分钟土壤湿度提升率 (默认 1.5%/分钟)
     */
    private int calculateIrrigationDuration(Double currentMoisture, Double targetMoisture,
                                            Integer maxDuration) {
        if (currentMoisture == null || targetMoisture == null) {
            return 300; // 默认5分钟
        }

        double gap = targetMoisture - currentMoisture;
        if (gap <= 0) {
            return 60; // 最小1分钟
        }

        // 经验参数: 每分钟提升 1.5% 土壤湿度
        double rate = 1.5;
        int duration = (int) (gap / rate * 60);

        // 最少 60 秒
        duration = Math.max(duration, 60);

        // 不超过最大时长
        if (maxDuration != null) {
            duration = Math.min(duration, maxDuration);
        }

        return duration;
    }

    /**
     * 获取地块当前土壤湿度
     * 从传感器数据服务获取最新值
     */
    private Double getSoilMoisture(Long farmId, Long fieldId) {
        try {
            List<?> sensors = sensorDataService.listSensors(farmId);
            for (Object obj : sensors) {
                Sensor sensor = (Sensor) obj;
                if (sensor.getFieldId() != null && sensor.getFieldId().equals(fieldId)
                        && sensor.getType() == SensorType.SOIL_MOISTURE) {
                    var vo = sensorDataService.getLatestData(sensor.getId());
                    return vo.getValue();
                }
            }
        } catch (Exception e) {
            log.error("获取土壤湿度失败: farmId={}, fieldId={}", farmId, fieldId, e);
        }
        return null;
    }

    /**
     * 检查天气是否下雨 (模拟实现)
     * 实际项目中可接入天气 API (如和风天气、心知天气)
     */
    private boolean checkWeatherRain(Long farmId) {
        // TODO: 接入天气 API 查询当前位置是否下雨
        // 此处模拟: 10% 概率下雨 (便于演示跳过逻辑)
        // 生产环境应对接天气API或雨量传感器数据
        double rainProbability = 0.0; // 设为0, 演示时不跳过
        return Math.random() < rainProbability;
    }

    /**
     * 灌溉检查结果封装
     */
    private static class IrrigationCheckResult {
        private final boolean shouldTrigger;
        private final Double soilMoisture;
        private final String skipReason;

        private IrrigationCheckResult(boolean shouldTrigger, Double soilMoisture, String skipReason) {
            this.shouldTrigger = shouldTrigger;
            this.soilMoisture = soilMoisture;
            this.skipReason = skipReason;
        }

        static IrrigationCheckResult trigger(Double soilMoisture) {
            return new IrrigationCheckResult(true, soilMoisture, null);
        }

        static IrrigationCheckResult skip(String reason) {
            return new IrrigationCheckResult(false, null, reason);
        }

        boolean shouldTrigger() { return shouldTrigger; }
        Double getSoilMoisture() { return soilMoisture; }
        String getSkipReason() { return skipReason; }
    }
}
