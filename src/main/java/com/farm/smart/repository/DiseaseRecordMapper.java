package com.farm.smart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.smart.model.entity.DiseaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 病害识别记录 Mapper 接口
 *
 * @author Smart Farm Team
 */
@Mapper
public interface DiseaseRecordMapper extends BaseMapper<DiseaseRecord> {

    /**
     * 查询今日病害识别次数
     */
    int countToday(@Param("farmId") Long farmId,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查询病害识别历史
     */
    List<DiseaseRecord> selectHistory(@Param("farmId") Long farmId,
                                       @Param("fieldId") Long fieldId,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);
}
