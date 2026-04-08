package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.agent.PlannerAgent;
import org.bupt.demoapp.common.SnowflakeIdGenerator;
import org.bupt.demoapp.entity.Plan;
import org.bupt.demoapp.mapper.PlanMapper;
import org.bupt.demoapp.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PlanServiceImp implements PlanService {

    private static final Logger logger = LoggerFactory.getLogger(PlanServiceImp.class);

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private PlannerAgent plannerAgent;

    @Override
    public String plan(String memoryId, String userMessage) {
        logger.info(">>> PlannerAgent 开始生成健康计划 - memoryId: {}", memoryId);
        String reply = plannerAgent.plan(memoryId, userMessage);
        logger.info(">>> PlannerAgent 计划生成完成 - memoryId: {}, 回复长度: {}",
                memoryId, reply.length());
        return reply;
    }

    @Override
    public Long savePlan(Plan plan) {
        long planId = snowflakeIdGenerator.nextId();
        plan.setPlanId(planId);
        plan.setCreateTime(LocalDateTime.now());

        logger.info(">>> 保存计划 - planId: {}, userId: {}, planType: {}, status: {}",
                planId, plan.getUserId(), plan.getPlanType(), plan.getPlanStatus());

        planMapper.insert(plan);
        return planId;
    }

    @Override
    public Plan getPlanById(Long planId) {
        logger.info(">>> 查询计划 - planId: {}", planId);
        return planMapper.findById(planId);
    }

    @Override
    public void updatePlan(Plan plan) {
        plan.setUpdateTime(LocalDateTime.now());

        logger.info(">>> 更新计划 - planId: {}, status: {}",
                plan.getPlanId(), plan.getPlanStatus());

        planMapper.update(plan);
    }
}
