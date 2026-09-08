package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.OtaDeviceProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备 OTA 升级进度 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface OtaDeviceProgressMapper extends BaseMapper<OtaDeviceProgress> {

    /**
     * 根据任务ID查询所有设备进度
     *
     * @param taskId 任务ID
     * @return 设备进度列表
     */
    List<OtaDeviceProgress> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID统计成功数量
     *
     * @param taskId 任务ID
     * @return 成功数量
     */
    int countSuccessByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID统计失败数量
     *
     * @param taskId 任务ID
     * @return 失败数量
     */
    int countFailedByTaskId(@Param("taskId") Long taskId);
}
