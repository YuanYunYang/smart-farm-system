package com.farm.smart.model.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板数据查询 DTO
 * 指定农场和时间范围查询聚合看板数据
 *
 * @author Smart Farm Team
 */
@Data
public class DashboardQueryDTO implements Serializable {

    /** 农场ID (必填) */
    private Long farmId;

    /** 查询开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 查询结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
