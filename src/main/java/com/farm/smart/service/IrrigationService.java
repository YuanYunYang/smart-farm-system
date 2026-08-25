package com.farm.smart.service;

import com.farm.smart.model.dto.IrrigationRuleDTO;
import com.farm.smart.model.entity.IrrigationLog;
import com.farm.smart.model.entity.IrrigationRule;

import java.util.List;
import java.util.Map;

/**
 * 灌溉服务接口 (自动灌溉策略引擎)
 *
 * @author Smart Farm Team
 */
public interface IrrigationService {

    /**
     * 定时任务: 检查所有启用的灌溉规则, 自动触发灌溉
     * 安全检查:
     * - 当前时间是否在允许灌溉时段
     * - 当天灌溉次数是否超限
     * - 是否有正在进行的灌溉
     * - 雨天跳过 (如启用)
     */
    void checkAndTriggerIrrigation();

    /**
     * 手动触发灌溉
     *
     * @param fieldId       地块ID
     * @param durationSeconds 灌溉时长 (秒)
     * @return 灌溉记录
     */
    IrrigationLog manualTrigger(Long fieldId, Integer durationSeconds);

    /**
     * 处理设备灌溉执行回复
     * 更新灌溉记录状态、实际时长、用水量
     *
     * @param commandId 指令ID
     * @param success   执行是否成功
     * @param actualDuration 实际执行时长 (秒)
     * @param waterUsage 用水量 (升)
     * @param message   回复消息
     */
    void handleIrrigationResponse(String commandId, Boolean success,
                                   Integer actualDuration,
                                   Double waterUsage, String message);

    /**
     * 创建灌溉规则
     */
    IrrigationRule createRule(IrrigationRuleDTO dto);

    /**
     * 更新灌溉规则
     */
    IrrigationRule updateRule(IrrigationRule rule);

    /**
     * 查询某农场所有灌溉规则
     */
    List<IrrigationRule> listRules(Long farmId);

    /**
     * 删除灌溉规则
     */
    boolean deleteRule(Long id);

    /**
     * 查询灌溉日志
     */
    List<IrrigationLog> listLogs(Long farmId, Long fieldId);

    /**
     * 查询本周灌溉统计
     */
    Map<String, Object> getWeeklyStats(Long farmId);
}
