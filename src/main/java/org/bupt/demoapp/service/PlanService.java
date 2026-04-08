package org.bupt.demoapp.service;

import org.bupt.demoapp.entity.Plan;

/**
 * 健康计划的 CRUD 服务
 */
public interface PlanService {

    /**
     * 执行健康计划规划（由 PlannerAgent 实现）
     * @param memoryId 会话ID
     * @param userMessage 用户当前输入
     * @return Agent 回复
     */
    String plan(String memoryId, String userMessage);

    /**
     * 保存一条新计划，planId 由内部生成
     * @param plan 计划对象（无需设置 planId 和 createTime）
     * @return 生成的 planId
     */
    Long savePlan(Plan plan);

    /**
     * 根据计划ID查询计划
     * @param planId 计划ID
     * @return 计划对象，不存在则返回 null
     */
    Plan getPlanById(Long planId);

    /**
     * 更新计划内容和状态
     * @param plan 包含 planId、planContent、planStatus 的计划对象
     */
    void updatePlan(Plan plan);
}
