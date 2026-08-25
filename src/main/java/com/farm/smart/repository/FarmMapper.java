package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.Farm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 农场 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper, 自动提供 CRUD
 *
 * @author Smart Farm Team
 */
@Mapper
public interface FarmMapper extends BaseMapper<Farm> {
}
