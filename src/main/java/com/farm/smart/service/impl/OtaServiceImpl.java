package com.farm.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.smart.mqtt.MqttClientManager;
import com.farm.smart.model.dto.FirmwareUploadDTO;
import com.farm.smart.model.dto.OtaTaskCreateDTO;
import com.farm.smart.model.entity.Firmware;
import com.farm.smart.model.entity.OtaDeviceProgress;
import com.farm.smart.model.entity.OtaTask;
import com.farm.smart.model.entity.Sensor;
import com.farm.smart.repository.FirmwareMapper;
import com.farm.smart.repository.OtaDeviceProgressMapper;
import com.farm.smart.repository.OtaTaskMapper;
import com.farm.smart.repository.SensorMapper;
import com.farm.smart.service.OtaService;
import com.farm.smart.tenant.TenantContext;
import com.farm.smart.websocket.FarmWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OTA 固件升级服务实现
 * <p>
 * 功能:
 * - 固件包 CRUD + 发布管理
 * - 升级任务创建 + 批量生成设备进度
 * - MQTT 下发升级指令到 farm/{farmId}/ota/command
 * - 接收设备上报进度 farm/{farmId}/ota/progress, 更新进度 + WebSocket 推送
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaServiceImpl extends ServiceImpl<FirmwareMapper, Firmware> implements OtaService {

    private final OtaTaskMapper otaTaskMapper;
    private final OtaDeviceProgressMapper otaDeviceProgressMapper;
    private final SensorMapper sensorMapper;
    private final MqttClientManager mqttClientManager;
    private final FarmWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** OTA 进度上报 topic 解析模式 */
    private static final Pattern OTA_PROGRESS_TOPIC_PATTERN = Pattern.compile("farm/(\\d+)/ota/progress");

    @Override
    public Firmware uploadFirmware(FirmwareUploadDTO dto) {
        Firmware firmware = new Firmware();
        firmware.setTenantId(TenantContext.getTenantId());
        firmware.setDeviceType(dto.getDeviceType());
        firmware.setVersion(dto.getVersion());
        firmware.setFileUrl(dto.getFileUrl());
        firmware.setFileSize(dto.getFileSize());
        firmware.setChecksumMd5(dto.getChecksumMd5());
        firmware.setStatus("DRAFT");
        firmware.setDescription(dto.getDescription());
        firmware.setCreateTime(LocalDateTime.now());
        baseMapper.insert(firmware);
        log.info("上传固件: id={}, version={}, deviceType={}", firmware.getId(), firmware.getVersion(), firmware.getDeviceType());
        return firmware;
    }

    @Override
    public boolean publishFirmware(Long firmwareId) {
        Firmware firmware = baseMapper.selectById(firmwareId);
        if (firmware == null) {
            log.warn("发布固件失败: 固件不存在, id={}", firmwareId);
            return false;
        }
        firmware.setStatus("PUBLISHED");
        return baseMapper.updateById(firmware) > 0;
    }

    @Override
    public OtaTask createOtaTask(OtaTaskCreateDTO dto) {
        // 1. 校验固件是否已发布
        Firmware firmware = baseMapper.selectById(dto.getFirmwareId());
        if (firmware == null) {
            throw new RuntimeException("固件不存在: " + dto.getFirmwareId());
        }
        if (!"PUBLISHED".equals(firmware.getStatus())) {
            throw new RuntimeException("固件未发布, 无法创建升级任务: " + dto.getFirmwareId());
        }

        // 2. 确定租户ID
        Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : TenantContext.getTenantId();

        // 3. 查询目标设备
        List<Sensor> targetSensors = queryTargetSensors(dto.getTargetType(), dto.getTargetFarmId(), dto.getTargetDeviceIds());
        if (targetSensors.isEmpty()) {
            throw new RuntimeException("未找到目标设备, 无法创建升级任务");
        }

        // 4. 创建升级任务
        OtaTask task = new OtaTask();
        task.setTenantId(tenantId);
        task.setFirmwareId(firmware.getId());
        task.setTaskName(dto.getTaskName());
        task.setTargetType(dto.getTargetType());
        task.setTargetFarmId(dto.getTargetFarmId());
        task.setTargetDeviceIds(dto.getTargetDeviceIds());
        task.setStatus("PENDING");
        task.setTotalCount(targetSensors.size());
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setCreateTime(LocalDateTime.now());
        otaTaskMapper.insert(task);

        // 5. 批量生成设备进度 + 下发 MQTT 升级指令
        for (Sensor sensor : targetSensors) {
            // 创建设备进度记录
            OtaDeviceProgress progress = new OtaDeviceProgress();
            progress.setTenantId(tenantId);
            progress.setTaskId(task.getId());
            progress.setDeviceId(sensor.getSensorCode());
            progress.setDeviceType(firmware.getDeviceType());
            progress.setStatus("PENDING");
            progress.setTargetVersion(firmware.getVersion());
            otaDeviceProgressMapper.insert(progress);

            // 下发 MQTT 升级指令到设备所在农场
            sendOtaCommand(sensor.getFarmId(), task.getId(), sensor.getSensorCode(), firmware);
        }

        // 6. 更新任务状态为执行中
        task.setStatus("RUNNING");
        otaTaskMapper.updateById(task);

        log.info("创建 OTA 升级任务: taskId={}, taskName={}, deviceCount={}", task.getId(), task.getTaskName(), targetSensors.size());
        return task;
    }

    @Override
    public OtaTask getTaskDetail(Long taskId) {
        return otaTaskMapper.selectById(taskId);
    }

    @Override
    public List<OtaDeviceProgress> getDeviceProgress(Long taskId) {
        return otaDeviceProgressMapper.selectByTaskId(taskId);
    }

    @Override
    public List<OtaTask> listTasks() {
        return otaTaskMapper.selectList(
                new LambdaQueryWrapper<OtaTask>().orderByDesc(OtaTask::getCreateTime));
    }

    @Override
    public boolean cancelTask(Long taskId) {
        OtaTask task = otaTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        if ("COMPLETED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            log.warn("任务已完成或已取消, 无法再次取消: taskId={}", taskId);
            return false;
        }
        task.setStatus("CANCELLED");
        return otaTaskMapper.updateById(task) > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleOtaProgress(String topic, String payload) {
        // MQTT 消息处理无 HTTP 请求上下文, 需忽略租户隔离
        TenantContext.setIgnore(true);
        try {
            // 解析 topic 获取 farmId
            Matcher matcher = OTA_PROGRESS_TOPIC_PATTERN.matcher(topic);
            if (!matcher.matches()) {
                log.warn("OTA 进度 topic 格式不匹配: {}", topic);
                return;
            }
            Long farmId = Long.parseLong(matcher.group(1));

            // 解析消息内容
            Map<String, Object> message = objectMapper.readValue(payload, Map.class);
            Long taskId = message.get("taskId") != null ? Long.valueOf(message.get("taskId").toString()) : null;
            String deviceId = (String) message.get("deviceId");
            String status = (String) message.get("status");
            String currentVersion = message.get("currentVersion") != null ? message.get("currentVersion").toString() : null;
            String targetVersion = message.get("targetVersion") != null ? message.get("targetVersion").toString() : null;
            String errorMsg = (String) message.get("errorMsg");

            if (taskId == null || deviceId == null || status == null) {
                log.warn("OTA 进度消息缺少必要字段: taskId={}, deviceId={}, status={}", taskId, deviceId, status);
                return;
            }

            // 查找设备进度记录
            OtaDeviceProgress progress = otaDeviceProgressMapper.selectOne(
                    new LambdaQueryWrapper<OtaDeviceProgress>()
                            .eq(OtaDeviceProgress::getTaskId, taskId)
                            .eq(OtaDeviceProgress::getDeviceId, deviceId));

            if (progress == null) {
                log.warn("未找到设备进度记录: taskId={}, deviceId={}", taskId, deviceId);
                return;
            }

            // 更新进度
            progress.setStatus(status);
            if (currentVersion != null) {
                progress.setCurrentVersion(currentVersion);
            }
            if (targetVersion != null) {
                progress.setTargetVersion(targetVersion);
            }
            if (errorMsg != null) {
                progress.setErrorMsg(errorMsg);
            }
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                progress.setUpgradeTime(LocalDateTime.now());
            }
            otaDeviceProgressMapper.updateById(progress);

            // 更新任务统计
            updateTaskStats(taskId);

            // WebSocket 推送进度
            pushOtaProgressToWebSocket(farmId, taskId, deviceId, status, progress);

            log.info("OTA 进度更新: taskId={}, deviceId={}, status={}", taskId, deviceId, status);

        } catch (Exception e) {
            log.error("处理 OTA 进度消息异常: topic={}, payload={}", topic, payload, e);
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 根据目标类型查询目标传感器
     */
    private List<Sensor> queryTargetSensors(String targetType, Long targetFarmId, String targetDeviceIds) {
        switch (targetType) {
            case "ALL":
                return sensorMapper.selectList(new LambdaQueryWrapper<>());
            case "BY_FARM":
                return sensorMapper.selectList(
                        new LambdaQueryWrapper<Sensor>().eq(Sensor::getFarmId, targetFarmId));
            case "SELECTED":
                if (targetDeviceIds == null || targetDeviceIds.isEmpty()) {
                    return List.of();
                }
                List<String> sensorCodes = Arrays.stream(targetDeviceIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                return sensorMapper.selectList(
                        new LambdaQueryWrapper<Sensor>().in(Sensor::getSensorCode, sensorCodes));
            default:
                log.warn("未知的目标类型: {}", targetType);
                return List.of();
        }
    }

    /**
     * 下发 OTA 升级指令到 MQTT
     */
    private void sendOtaCommand(Long farmId, Long taskId, String deviceId, Firmware firmware) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskId", taskId);
            payload.put("deviceId", deviceId);
            payload.put("firmwareId", firmware.getId());
            payload.put("version", firmware.getVersion());
            payload.put("fileUrl", firmware.getFileUrl());
            payload.put("checksumMd5", firmware.getChecksumMd5());
            payload.put("timestamp", System.currentTimeMillis());

            String topic = String.format("farm/%d/ota/command", farmId);
            String json = objectMapper.writeValueAsString(payload);
            mqttClientManager.publish(topic, json);

            log.info("下发 OTA 升级指令: farmId={}, taskId={}, deviceId={}, version={}",
                    farmId, taskId, deviceId, firmware.getVersion());
        } catch (Exception e) {
            log.error("下发 OTA 升级指令失败: farmId={}, deviceId={}", farmId, deviceId, e);
        }
    }

    /**
     * 更新任务统计 (成功/失败计数)
     */
    private void updateTaskStats(Long taskId) {
        int successCount = otaDeviceProgressMapper.countSuccessByTaskId(taskId);
        int failCount = otaDeviceProgressMapper.countFailedByTaskId(taskId);

        OtaTask task = otaTaskMapper.selectById(taskId);
        if (task != null) {
            task.setSuccessCount(successCount);
            task.setFailCount(failCount);

            // 所有设备已完成 (成功+失败=总数), 标记任务完成
            if (successCount + failCount >= task.getTotalCount()) {
                task.setStatus("COMPLETED");
            }
            otaTaskMapper.updateById(task);
        }
    }

    /**
     * WebSocket 推送 OTA 升级进度
     */
    private void pushOtaProgressToWebSocket(Long farmId, Long taskId, String deviceId, String status, OtaDeviceProgress progress) {
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "OTA_PROGRESS");
            wsMessage.put("farmId", farmId);
            wsMessage.put("taskId", taskId);
            wsMessage.put("deviceId", deviceId);
            wsMessage.put("status", status);
            wsMessage.put("currentVersion", progress.getCurrentVersion());
            wsMessage.put("targetVersion", progress.getTargetVersion());

            String json = objectMapper.writeValueAsString(wsMessage);
            webSocketHandler.sendToFarm(farmId, json);
        } catch (Exception e) {
            log.error("WebSocket 推送 OTA 进度失败: {}", e.getMessage(), e);
        }
    }
}
