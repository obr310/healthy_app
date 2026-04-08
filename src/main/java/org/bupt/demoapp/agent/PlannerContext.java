package org.bupt.demoapp.agent;

/**
 * 保存当前请求的 Planner 线程上下文。
 *
 * 这些状态只在单次请求内有效，因此仍然适合放在线程上下文中；
 * 跨请求的会话状态由 Redis 管理。
 */
public class PlannerContext {

    public enum PlanStage {
        INFO_GATHERING,
        READY_TO_GENERATE
    }

    private static final ThreadLocal<String> MEMORY_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLAN_ENDED = new ThreadLocal<>();
    private static final ThreadLocal<PlanStage> PLAN_STAGE = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_SAVE_PLAN_RESULT = new ThreadLocal<>();

    public static void setMemoryId(String memoryId) {
        MEMORY_ID.set(memoryId);
        if (PLAN_STAGE.get() == null) {
            PLAN_STAGE.set(PlanStage.INFO_GATHERING);
        }
    }

    public static String getMemoryId() {
        return MEMORY_ID.get();
    }

    public static void setPlanStage(PlanStage planStage) {
        PLAN_STAGE.set(planStage);
    }

    public static PlanStage getPlanStage() {
        PlanStage stage = PLAN_STAGE.get();
        return stage != null ? stage : PlanStage.INFO_GATHERING;
    }

    public static void setLastSavePlanResult(String result) {
        LAST_SAVE_PLAN_RESULT.set(result);
    }

    public static String getLastSavePlanResult() {
        return LAST_SAVE_PLAN_RESULT.get();
    }

    /** 由 updatePlan Tool 在计划状态变为 ACTIVE 时调用，标记 PLAN 流程已结束。 */
    public static void markPlanEnded() {
        PLAN_ENDED.set(Boolean.TRUE);
    }

    /** ChatDispatchServiceImp 在 plan() 返回后调用，判断本轮是否结束了 PLAN 流程。 */
    public static boolean isPlanEnded() {
        return Boolean.TRUE.equals(PLAN_ENDED.get());
    }

    public static void clear() {
        MEMORY_ID.remove();
        PLAN_ENDED.remove();
        PLAN_STAGE.remove();
        LAST_SAVE_PLAN_RESULT.remove();
    }
}
