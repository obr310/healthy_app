package org.bupt.demoapp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.bupt.demoapp.entity.ChatLog;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LogMapper {
    /**
     * 插入一条聊天日志
     * @param chatLog 用户日志对象
     * @return 影响的行数 成功为1
     */
    int insert(ChatLog chatLog);

    /**
     * 查询某个用户的所有不同的 memoryId
     * @param userId 用户ID
     * @return memoryId 列表
     */
    List<String> findDistinctMemoryIdsByUserId(@Param("userId") String userId);

    /**
     * 查询某个 memoryId 的最后一条消息
     * @param memoryId 会话标识
     * @return 最后一条聊天日志
     */
    ChatLog findLastMessageByMemoryId(@Param("memoryId") String memoryId);

    /**
     * 根据 logId 查询日志详情
     * @param logId 日志ID
     * @return 聊天日志
     */
    ChatLog findByLogId(@Param("logId") String logId);

    /**
     * 根据多个 logId 批量查询日志
     * @param logIds 日志ID列表
     * @return 聊天日志列表
     */
    List<ChatLog> findByLogIds(@Param("logIds") List<String> logIds);

    /**
     * 根据多个 logId 和时间范围查询日志
     * @param logIds 日志ID列表
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 聊天日志列表
     */
    List<ChatLog> findByLogIdsAndTimeRange(
            @Param("logIds") List<String> logIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 根据用户ID和时间范围查询所有日志
     * @param userId 用户ID
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 聊天日志列表
     */
    List<ChatLog> findByUserIdAndTimeRange(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 插入一条聊天日志（用于测试）
     * @param chatLog 聊天日志对象
     * @return 影响的行数
     */
    int insertChatLog(ChatLog chatLog);

    /**
     * 根据多个 logId 批量删除日志（用于测试清理）
     * @param logIds 日志ID列表
     * @return 影响的行数
     */
    int deleteByLogIds(@Param("logIds") List<Long> logIds);
}
