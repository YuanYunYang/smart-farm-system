package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.AlarmRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警规则 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface AlarmRuleMapper extends BaseMapper<AlarmRule> {
}
