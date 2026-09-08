package com.farm.smart.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 告警评估结果 (Drools 规则引擎输出)
 *
 * @author Smart Farm Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlarmResult implements Serializable {

    /** 告警级别: INFO / WARN / CRITICAL / URGENT */
    private String level;

    /** 告警类型 */
    private String type;

    /** 告警标题 */
    private String title;

    /** 告警内容 */
    private String content;
}
