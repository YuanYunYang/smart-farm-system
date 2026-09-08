package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper
 *
 * @author Smart Farm Team
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
