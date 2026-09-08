package com.farm.smart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farm.smart.model.dto.FirmwareUploadDTO;
import com.farm.smart.model.dto.OtaTaskCreateDTO;
import com.farm.smart.model.entity.Firmware;
import com.farm.smart.model.entity.OtaDeviceProgress;
import com.farm.smart.model.entity.OtaTask;

import java.util.List;

/**
 * OTA 固件升级服务接口
 * 管理固件包 CRUD + 升级任务 + 设备进度追踪
 *
 * @author Smart Farm Team
 */
public interface OtaService extends IService<Firmware> {

    /**
     * 上传固件包 (创建为 DRAFT 状态)
     *
     * @param dto 固件上传参数
     * @return 创建的固件实体
     */
    Firmware uploadFirmware(FirmwareUploadDTO dto);

    /**
     * 发布固件 (DRAFT -> PUBLISHED)
     *
     * @param firmwareId 固件ID
     * @return 是否成功
     */
    boolean publishFirmware(Long firmwareId);

    /**
     * 创建升级任务
     * 1. 保存任务记录 2. 批量生成设备进度 3. MQTT 下发升级指令
     *
     * @param dto 任务创建参数
     * @return 创建的任务实体
     */
    OtaTask createOtaTask(OtaTaskCreateDTO dto);

    /**
     * 查询任务详情
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    OtaTask getTaskDetail(Long taskId);

    /**
     * 查询任务下所有设备的升级进度
     *
     * @param taskId 任务ID
     * @return 设备进度列表
     */
    List<OtaDeviceProgress> getDeviceProgress(Long taskId);

    /**
     * 查询升级任务列表
     *
     * @return 任务列表
     */
    List<OtaTask> listTasks();

    /**
     * 取消升级任务
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean cancelTask(Long taskId);

    /**
     * 处理设备上报的 OTA 升级进度消息
     * Topic: farm/{farmId}/ota/progress
     *
     * @param topic   MQTT topic
     * @param payload 消息内容 (JSON 字符串)
     */
    void handleOtaProgress(String topic, String payload);
}
