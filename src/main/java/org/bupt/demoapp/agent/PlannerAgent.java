package org.bupt.demoapp.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Planner Agent 接口
 *
 * 基于 LangChain4j @AiService 实现的健康计划规划代理。
 *
 * 设计要点：
 * - chatMemoryProvider：维持多轮对话记忆，LLM 记住历史追问和用户回答，
 *                       修改计划时无需重新检索背景数据（渐进式披露的关键）
 * - tools：注册 5 个 Tool，LLM 自主决定何时调用，实现按需数据获取
 * - SystemMessage：精简主提示，只定义流程骨架，不预装数据
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = {"plannerAgentTools"}
)
public interface PlannerAgent {

    /**
     * 执行健康计划规划
     *
     * @param memoryId    会话ID（格式：userId:conversationId），用于多轮对话记忆
     * @param userMessage 用户当前输入
     * @return Agent 回复（追问 / 计划展示 / 确认信息）
     */
    @SystemMessage(fromResource = "prompts/plan_agent_system.txt")
    String plan(@MemoryId String memoryId, @UserMessage String userMessage);
}

