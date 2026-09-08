package com.farm.smart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farm.smart.model.dto.AlarmResult;
import com.farm.smart.model.dto.SensorDataFact;
import com.farm.smart.model.entity.AlarmRule;

import java.util.List;

/**
 * Drools 规则引擎服务接口
 * 管理告警规则 CRUD + 规则评估 + 热加载
 *
 * @author Smart Farm Team
 */
public interface RuleEngineService extends IService<AlarmRule> {

    /**
     * 规则评估 - 将传感器数据事实注入规则引擎, 返回匹配的告警结果
     *
     * @param fact 传感器数据事实
     * @return 告警结果列表 (空列表表示无告警)
     */
    List<AlarmResult> evaluateRule(SensorDataFact fact);

    /**
     * 重新加载规则 (从数据库读取启用的 DRL 规则 + 默认规则文件)
     */
    void reloadRules();

    /**
     * 启用/禁用规则
     *
     * @param id      规则ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean toggleRule(Long id, boolean enabled);
}
