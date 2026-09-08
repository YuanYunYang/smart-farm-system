package com.farm.smart.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警规则实体
 * 存储 Drools DRL 规则内容, 支持动态加载与热更新
 *
 * @author Smart Farm Team
 */
@Data
@TableName("alarm_rule")
public class AlarmRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（多租户隔离） */
    private Long tenantId;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型: THRESHOLD-阈值 / COMPOSITE-复合 / CUSTOM-自定义 */
    private String ruleType;

    /** DRL 规则内容 */
    private String drlContent;

    /** 是否启用: 0-禁用, 1-启用 */
    private Integer enabled;

    /** 规则描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;
}
