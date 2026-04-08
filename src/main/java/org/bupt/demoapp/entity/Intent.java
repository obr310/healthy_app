package org.bupt.demoapp.entity;

public enum Intent {
    RECORD,  // 用户记录日志
    QUERY,   // 用户查询历史记录
    SUMMARY, // 用户请求总结/统计
    QA,      // 健康知识问答
    PLAN,    // 用户请求制定健康计划
    UNKNOWN  // 无法识别
}
