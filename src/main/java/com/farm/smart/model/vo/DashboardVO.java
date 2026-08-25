package com.farm.smart.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板聚合数据 VO
 * 返回给前端看板的综合数据
 *
 * @author Smart Farm Team
 */
@Data
public class DashboardVO implements Serializable {

    /** 农场ID */
    private Long farmId;

    /** 在线传感器数 */
    private Integer onlineSensorCount;

    /** 离线传感器数 */
    private Integer offlineSensorCount;

    /** 今日告警数 */
    private Integer todayAlarmCount;

    /** 本周灌溉次数 */
    private Integer weekIrrigationCount;

    /** 本周总用水量 (升) */
    private Double weekWaterUsage;

    /** 今日病害识别次数 */
    private Integer todayDiseaseCount;

    /** 最新传感器数据列表 */
    private List<SensorDataVO> latestSensorData;

    /** 24小时趋势数据 */
    private List<TrendPoint> trend24h;

    /** 最近告警列表 */
    private List<AlarmSummary> recentAlarms;

    /** 生成时间 */
    private LocalDateTime generateTime;

    /**
     * 趋势数据点
     */
    @Data
    public static class TrendPoint implements Serializable {
        /** 时间点 (yyyy-MM-dd HH:mm) */
        private String time;
        /** 温度 */
        private Double temperature;
        /** 湿度 */
        private Double humidity;
        /** 土壤湿度 */
        private Double soilMoisture;
        /** CO2 */
        private Double co2;
    }

    /**
     * 告警摘要
     */
    @Data
    public static class AlarmSummary implements Serializable {
        private String level;
        private String title;
        private String content;
        private String alarmTime;
    }
}
