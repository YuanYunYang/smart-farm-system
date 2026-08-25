package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.IrrigationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 灌溉记录 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface IrrigationLogMapper extends BaseMapper<IrrigationLog> {

    /**
     * 查询某地块当天的灌溉次数
     */
    int countTodayByFieldId(@Param("fieldId") Long fieldId,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询本周灌溉统计 (次数 + 总用水量)
     */
    Map<String, Object> weeklyStats(@Param("farmId") Long farmId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 查询正在进行的灌溉 (防止重复触发)
     */
    List<IrrigationLog> selectRunningByFieldId(@Param("fieldId") Long fieldId);

    /**
     * 根据指令ID查询灌溉记录 (设备回复时关联)
     */
    IrrigationLog selectByCommandId(@Param("commandId") String commandId);

    /**
     * 查询本周灌溉日志列表
     */
    List<IrrigationLog> selectWeeklyLogs(@Param("farmId") Long farmId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);
}
