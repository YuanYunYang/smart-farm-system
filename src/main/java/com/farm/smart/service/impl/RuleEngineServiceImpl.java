package com.farm.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farm.smart.model.dto.AlarmResult;
import com.farm.smart.model.dto.SensorDataFact;
import com.farm.smart.model.entity.AlarmRule;
import com.farm.smart.repository.AlarmRuleMapper;
import com.farm.smart.service.RuleEngineService;
import com.farm.smart.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drools 规则引擎服务实现
 * <p>
 * 功能:
 * - 使用 KieServices 动态加载 DRL 规则 (默认规则文件 + 数据库自定义规则)
 * - 规则评估: 将传感器数据事实注入规则引擎, 返回告警结果
 * - 规则热加载: 支持运行时重新加载规则
 * - 规则 CRUD + 启用/禁用
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
public class RuleEngineServiceImpl extends ServiceImpl<AlarmRuleMapper, AlarmRule> implements RuleEngineService {

    /** Drools KieContainer (volatile 保证多线程可见性) */
    private volatile KieContainer kieContainer;

    /** DRL 包名 */
    private static final String DRL_PACKAGE = "farm.alarm.rules";

    /** 默认规则文件路径 */
    private static final String DEFAULT_RULES_PATH = "rules/default-alarm-rules.drl";

    /**
     * 初始化: 加载默认规则 + 数据库启用的规则
     */
    @PostConstruct
    public void init() {
        try {
            reloadRules();
        } catch (Exception e) {
            log.error("初始化 Drools 规则引擎失败", e);
        }
    }

    @Override
    public List<AlarmResult> evaluateRule(SensorDataFact fact) {
        KieContainer container = this.kieContainer;
        if (container == null) {
            log.warn("KieContainer 未初始化, 跳过规则评估");
            return new ArrayList<>();
        }

        KieSession session = container.newKieSession();
        try {
            session.insert(fact);
            session.fireAllRules();
            return fact.getResults();
        } catch (Exception e) {
            log.error("规则评估异常: fact={}", fact, e);
            return new ArrayList<>();
        } finally {
            session.dispose();
        }
    }

    @Override
    public void reloadRules() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

            // 生成 POM (每次重载使用唯一版本号, 避免冲突)
            ReleaseId releaseId = kieServices.newReleaseId(
                    "com.farm.smart", "alarm-rules", String.valueOf(System.currentTimeMillis()));
            kieFileSystem.generateAndWritePomXML(releaseId);

            // 加载默认规则文件
            try {
                Resource defaultRules = kieServices.getResources().newClassPathResource(DEFAULT_RULES_PATH);
                kieFileSystem.write(defaultRules);
            } catch (Exception e) {
                log.warn("加载默认规则文件失败 (将仅使用数据库规则): {}", e.getMessage());
            }

            // 加载数据库中启用的规则
            List<AlarmRule> enabledRules = getEnabledRules();
            for (int i = 0; i < enabledRules.size(); i++) {
                String drlContent = wrapDrlContent(enabledRules.get(i).getDrlContent());
                kieFileSystem.write("src/main/resources/rules/dynamic-rule-" + i + ".drl", drlContent);
            }

            // 构建规则
            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();

            Results results = kieBuilder.getResults();
            if (results.hasMessages(Message.Level.ERROR)) {
                log.error("Drools 规则编译错误: {}", results);
            }

            // 创建 KieContainer
            this.kieContainer = kieServices.newKieContainer(releaseId);

            log.info("Drools 规则重新加载完成: 默认规则 + {} 条数据库规则", enabledRules.size());
        } catch (Exception e) {
            log.error("重新加载 Drools 规则失败", e);
        }
    }

    @Override
    public boolean toggleRule(Long id, boolean enabled) {
        AlarmRule rule = baseMapper.selectById(id);
        if (rule == null) {
            log.warn("规则不存在: id={}", id);
            return false;
        }
        rule.setEnabled(enabled ? 1 : 0);
        boolean result = baseMapper.updateById(rule) > 0;
        if (result) {
            // 规则状态变更后重新加载
            reloadRules();
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 查询数据库中所有启用的规则
     * (忽略租户隔离, 规则引擎加载所有租户的规则)
     */
    private List<AlarmRule> getEnabledRules() {
        try {
            TenantContext.setIgnore(true);
            return baseMapper.selectList(
                    new LambdaQueryWrapper<AlarmRule>().eq(AlarmRule::getEnabled, 1));
        } catch (Exception e) {
            log.warn("查询启用规则失败 (表可能未创建): {}", e.getMessage());
            return new ArrayList<>();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 包装数据库中的 DRL 内容, 添加包声明和导入语句
     *
     * @param drlContent 数据库中存储的 DRL 规则内容 (仅包含 rule 定义)
     * @return 完整的 DRL 文件内容
     */
    private String wrapDrlContent(String drlContent) {
        return DRL_PACKAGE + ";\n\n" +
                "import com.farm.smart.model.dto.SensorDataFact;\n" +
                "import com.farm.smart.model.dto.AlarmResult;\n\n" +
                drlContent;
    }
}
