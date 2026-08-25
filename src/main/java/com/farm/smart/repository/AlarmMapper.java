package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.Alarm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {

    /**
     * 查询今日告警数量
     */
    int countToday(@Param("farmId") Long farmId,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查询最近告警列表
     */
    List<Alarm> selectRecent(@Param("farmId") Long farmId,
                              @Param("limit") int limit);

    /**
     * 告警级别统计 (用于看板)
     */
    List<Map<String, Object>> countByLevel(@Param("farmId") Long farmId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 更新告警处理状态
     */
    int updateHandleStatus(@Param("id") Long id,
                           @Param("handler") String handler,
                           @Param("remark") String remark,
                           @Param("handleTime") LocalDateTime handleTime);
}
