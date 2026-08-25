package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.IrrigationRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 灌溉规则 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface IrrigationRuleMapper extends BaseMapper<IrrigationRule> {

    /**
     * 查询所有启用的灌溉规则
     */
    List<IrrigationRule> selectEnabledRules();

    /**
     * 根据农场ID查询灌溉规则
     */
    List<IrrigationRule> selectByFarmId(@Param("farmId") Long farmId);

    /**
     * 根据地块ID查询灌溉规则
     */
    IrrigationRule selectByFieldId(@Param("fieldId") Long fieldId);
}
