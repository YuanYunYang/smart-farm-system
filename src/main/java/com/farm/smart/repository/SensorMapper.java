package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.Sensor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 传感器 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface SensorMapper extends BaseMapper<Sensor> {

    /**
     * 根据农场ID查询传感器列表 (关联地块名称)
     * 使用 XML 映射文件定义 SQL
     */
    List<Sensor> selectByFarmId(@Param("farmId") Long farmId);

    /**
     * 更新传感器在线状态和最后心跳时间
     */
    int updateOnlineStatus(@Param("id") Long id,
                          @Param("status") Integer status);
}
