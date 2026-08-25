package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.Field;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地块 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface FieldMapper extends BaseMapper<Field> {

    /**
     * 根据农场ID查询地块列表
     */
    List<Field> selectByFarmId(@Param("farmId") Long farmId);
}
