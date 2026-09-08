package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.OtaTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * OTA 升级任务 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface OtaTaskMapper extends BaseMapper<OtaTask> {
}
