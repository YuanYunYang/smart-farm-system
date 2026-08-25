package com.farm.smart.schedule;

import com.farm.smart.service.IrrigationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 灌溉定时任务
 * 每5分钟检查所有启用的灌溉规则, 满足条件自动触发灌溉
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IrrigationScheduleTask {

    private final IrrigationService irrigationService;

    /**
     * 定时检查灌溉规则 (每5分钟)
     * cron: 秒 分 时 日 月 周
     * 表达式 "0 0/5 * * * ?" 表示每5分钟执行一次
     */
    @Scheduled(cron = "${irrigation.schedule.cron:0 */5 * * * ?}")
    public void checkIrrigationRules() {
        log.info("定时任务: 开始检查灌溉规则");
        try {
            irrigationService.checkAndTriggerIrrigation();
        } catch (Exception e) {
            log.error("定时灌溉检查任务异常: {}", e.getMessage(), e);
        }
    }
}
