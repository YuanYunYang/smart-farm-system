package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.Firmware;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固件包 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface FirmwareMapper extends BaseMapper<Firmware> {
}
