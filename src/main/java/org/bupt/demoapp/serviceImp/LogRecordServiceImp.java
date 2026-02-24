package org.bupt.demoapp.serviceImp;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.bupt.demoapp.aiservice.EventDateExtractionService;
import org.bupt.demoapp.aiservice.ReplyGenerationService;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.common.SnowflakeIdGenerator;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.mapper.LogMapper;
import org.bupt.demoapp.service.LogRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Service
public class LogRecordServiceImp implements LogRecordService {
    private static final Logger logger = LoggerFactory.getLogger(LogRecordServiceImp.class);
    
    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Autowired
    private LogMapper logMapper;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private MilvusEmbeddingStore userLogEmbeddingStore;
    @Autowired
    private MemoryIds memoryIds;
    @Autowired
    private ReplyGenerationService replyGenerationService;
    @Autowired
    private EventDateExtractionService eventDateExtractionService;

    @Override
    public ChatResponse record(String memoryId, String msg) {
        logger.info(">>> 开始记录日志 - memoryId: {}, msg: {}", memoryId, msg);
        
        Long logId = snowflakeIdGenerator.nextId();
        logger.info(">>> 生成 logId: {}", logId);
        
        // 提取事件发生日期
        LocalDate eventDate = extractEventDate(msg);
        logger.info(">>> 提取事件日期: {}", eventDate);
        
        // 存储到 MySQL
        ChatLog chatLog = new ChatLog();
        chatLog.setLogId(logId);
        chatLog.setMemoryId(memoryId);
        chatLog.setUserId(String.valueOf(memoryIds.extractUserId(memoryId)));
        chatLog.setRawText(msg);
        chatLog.setMsg(msg);
        chatLog.setIntent(Intent.RECORD.name());
        chatLog.setCreateTime(LocalDateTime.now());
        chatLog.setEventDate(eventDate);
        
        logger.info(">>> 开始存储到 MySQL - logId: {}, userId: {}, eventDate: {}", logId, chatLog.getUserId(), eventDate);
        logMapper.insert(chatLog);
        boolean mysqlStored = true;
        logger.info(">>> MySQL 存储成功 - logId: {}", logId);
        
        // 存储到 Milvus（向量数据库）
        boolean milvusStored = false;
        try {
            logger.info(">>> 开始调用大模型生成 Embedding - msg: {}", msg);
            long embeddingStartTime = System.currentTimeMillis();
            Embedding embedding = embeddingModel.embed(msg).content();
            long embeddingDuration = System.currentTimeMillis() - embeddingStartTime;
            logger.info(">>> Embedding 生成完成 - 耗时: {}ms, 维度: {}", embeddingDuration, embedding.dimension());
            
            Metadata metadata = new Metadata();
            metadata.put("log_id", String.valueOf(logId));
            metadata.put("user_id", String.valueOf(memoryIds.extractUserId(memoryId)));
            metadata.put("memory_id", memoryId);
            // 事件日期存为毫秒时间戳（当天 00:00:00），用于 Milvus 时间范围过滤
            long eventDateTimestamp = dateToTimestamp(eventDate);
            metadata.put("event_date", eventDateTimestamp);
            logger.info(">>> Milvus metadata - event_date: {} -> {}ms", eventDate, eventDateTimestamp);
            TextSegment segment = new TextSegment(msg, metadata);
            
            logger.info(">>> 开始存储到 Milvus - logId: {}, segment: {}", logId, segment.text());
            long milvusStartTime = System.currentTimeMillis();
         userLogEmbeddingStore.add(embedding, segment);
            long milvusDuration = System.currentTimeMillis() - milvusStartTime;
            milvusStored = true;
            logger.info(">>> Milvus 存储成功 - logId: {}, 耗时: {}ms", logId, milvusDuration);
        } catch (Exception e) {
            logger.error(">>> Milvus 存储失败 - logId: {}, msg: {}", logId, msg, e);
            milvusStored = false;
     }

        // 使用LLM生成友好的回复消息
        logger.info(">>> 开始调用LLM生成回复消息 - memoryId: {}, msg: {}", memoryId, msg);
        long replyStartTime = System.currentTimeMillis();
        String reply;
        try {
            // 存储LLM生成的信息到redis
            reply = replyGenerationService.generateRecordReply(memoryId, msg);
            long replyDuration = System.currentTimeMillis() - replyStartTime;
            logger.info(">>> 回复生成完成 - memoryId: {}, reply: {}, 耗时: {}ms", memoryId, reply, replyDuration);
        } catch (Exception e) {
            long replyDuration = System.currentTimeMillis() - replyStartTime;
            logger.error(">>> 回复生成失败，使用默认回复 - memoryId: {}, 耗时: {}ms", memoryId, replyDuration, e);
            reply = Messages.RECORD_SAVED;
        }
        
        ChatResponse response = new ChatResponse(
               String.valueOf(logId),
               Intent.RECORD.name(),
               reply,
               mysqlStored,
               milvusStored
       );

        logger.info(">>> 日志记录完成 - logId: {}, reply: {}, mysqlStored: {}, milvusStored: {}", 
                logId, reply, mysqlStored, milvusStored);
        return response;
    }

    /**
     * 从日志内容中提取事件发生日期
     * 使用LLM分析日志中的时间表达（如"昨天"、"上周三"等），计算出实际日期
     * 
     * @param msg 日志内容
     * @return 事件发生日期，如果无法提取则返回当前日期
     */
    private LocalDate extractEventDate(String msg) {
        LocalDate today = LocalDate.now();
        try {
            String result = eventDateExtractionService.extractEventDate(today.toString(), msg);
            logger.info(">>> LLM返回事件日期: {}", result);
            
            // 清理结果：去除多余空白和换行
            String cleanedResult = result.trim().split("\\s+")[0];
            return LocalDate.parse(cleanedResult);
        } catch (DateTimeParseException e) {
            logger.warn(">>> 日期解析失败，使用当前日期 - result: {}", e.getMessage());
            return today;
        } catch (Exception e) {
            logger.error(">>> 事件日期提取失败，使用当前日期", e);
            return today;
        }
    }

    /**
     * 将 LocalDate 转换为毫秒时间戳（当天 00:00:00）
     */
    private long dateToTimestamp(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
