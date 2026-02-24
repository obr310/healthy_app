package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.aiservice.IntentService;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.service.ChatDispatchService;
import org.bupt.demoapp.service.LogQueryService;
import org.bupt.demoapp.service.LogSummaryService;
import org.bupt.demoapp.service.QAService;
import org.bupt.demoapp.service.SaveChatHistoryService;
import org.bupt.demoapp.service.LogRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatDispatchServiceImp implements ChatDispatchService {
    private static final Logger logger = LoggerFactory.getLogger(ChatDispatchServiceImp.class);

    @Autowired
    private IntentService intentService;
    @Autowired
    private LogRecordService logRecordService;
    @Autowired
    private SaveChatHistoryService saveChatHistoryService;
    @Autowired
    private LogQueryService logQueryService;
    @Autowired
    private LogSummaryService logSummaryService;
    @Autowired
    private QAService qaService;

    @Override
    public ChatResponse handle(String memoryId, String msg) {
        logger.info(">>> 开始处理聊天消息 - memoryId: {}, msg: {}", memoryId, msg);

        try {
            // 保存用户输入到展示历史
            saveChatHistoryService.saveUserMessage(memoryId, msg);
            logger.info(">>> 用户消息已保存到展示历史 - memoryId: {}", memoryId);

            // 调用大模型进行意图识别
            logger.info(">>> 调用大模型进行意图识别 - memoryId: {}, msg: {}", memoryId, msg);
            long intentStartTime = System.currentTimeMillis();
            Intent intent;
            try {
                intent = intentService.classify(memoryId, msg);
                long intentDuration = System.currentTimeMillis() - intentStartTime;
                logger.info(">>> 意图识别完成 - intent: {}, 耗时: {}ms", intent, intentDuration);
            } catch (Exception e) {
                long intentDuration = System.currentTimeMillis() - intentStartTime;
                logger.error(">>> 意图识别失败 - memoryId: {}, msg: {}, 耗时: {}ms", memoryId, msg, intentDuration, e);
                // 返回友好的错误消息，而不是抛出异常
                ChatResponse errorResponse = new ChatResponse(null, Intent.UNKNOWN.name(),
                        Messages.CHAT_INTENT_FAILED, false, false);
                saveChatHistoryService.saveAiMessage(memoryId, errorResponse.getReply());
                return errorResponse;
            }

            ChatResponse response;
            //用户意图为记录,存储信息->生成回复
            if (intent == Intent.RECORD) {
                logger.info(">>> 意图为 RECORD，开始记录日志 - memoryId: {}", memoryId);
                response = logRecordService.record(memoryId, msg);
                logger.info(">>> 日志记录完成 - logId: {}", response.getLogId());
            } else if (intent == Intent.QUERY) {
                logger.info(">>> 意图为 QUERY，开始查询日志 - memoryId: {}", memoryId);
                response = logQueryService.queryChat(memoryId, msg);
                logger.info(">>> 查询完成");
            } else if (intent == Intent.SUMMARY) {
                logger.info(">>> 意图为 SUMMARY，开始生成总结 - memoryId: {}", memoryId);
                response = logSummaryService.summarize(memoryId, msg);
                logger.info(">>> 总结生成完成");
            } else if (intent == Intent.QA) {
                logger.info(">>> 意图为 QA，开始健康知识问答 - memoryId: {}", memoryId);
                response = qaService.heathQA(memoryId, msg);
                logger.info(">>> QA回答完成");
            } else if (intent == Intent.UNKNOWN) {
                logger.warn(">>> 意图为 UNKNOWN，用户输入无法识别 - memoryId: {}, msg: {}", memoryId, msg);
                // 提示用户规范输入
                String reply = Messages.CHAT_UNKNOWN_INTENT;
                response = new ChatResponse(null, Intent.UNKNOWN.name(), reply, false, false);
            } else {
                // 其他未处理的意图类型
                logger.info(">>> 意图为 {}，使用默认回复", intent);
                String reply = Messages.CHAT_UNKNOWN_INTENT;
                response = new ChatResponse(null, intent.name(), reply, false, false);
            }

            saveChatHistoryService.saveAiMessage(memoryId, response.getReply());
            logger.info(">>> AI回复已保存到展示历史 - memoryId: {}, reply: {}", memoryId, response.getReply());

            logger.info(">>> 聊天消息处理完成 - intent: {}, logId: {}", intent, response.getLogId());
            return response;

        } catch (Exception e) {
            // 捕获所有未处理的异常，返回友好的错误消息
            logger.error(">>> 聊天消息处理失败 - memoryId: {}, msg: {}", memoryId, msg, e);
            ChatResponse errorResponse = new ChatResponse(null, Intent.UNKNOWN.name(),
                    Messages.CHAT_PROCESSING_FAILED, false, false);
            try {
                saveChatHistoryService.saveAiMessage(memoryId, errorResponse.getReply());
            } catch (Exception saveError) {
                logger.error(">>> 保存错误回复失败 - memoryId: {}", memoryId, saveError);
            }
            return errorResponse;
        }
    }
}
    


