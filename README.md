# DemoApp - 智能健康助手 RAG 应用

基于 LangChain4j 和 Milvus 构建的智能健康助手应用，支持个性化健康日志记录与智能问答功能。

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [核心模块详解](#核心模块详解)
- [API 接口说明](#api-接口说明)
- [配置说明](#配置说明)
- [快速开始](#快速开始)
- [开发指南](#开发指南)
- [常见问题](#常见问题)

---

## 项目简介

DemoApp 是一款专注于个人健康管理的智能助手应用。用户可以通过自然语言与系统交互，完成健康日志记录、健康数据查询、健康趋势总结以及智能问答等操作。系统采用 RAG（检索增强生成）架构，结合向量数据库和全文搜索引擎，为用户提供准确、个性化的健康建议。

---

## 功能特性

### 健康日志记录

支持多种健康数据的结构化记录：

- **饮食记录**：记录每日饮食内容、摄入热量、营养成分等
- **睡眠记录**：记录睡眠时间、睡眠质量、醒来状态等
- **运动记录**：记录运动类型、运动时长、运动强度等
- **情绪记录**：记录每日情绪状态、压力指数等

用户以自然语言描述健康状况，系统自动提取关键信息并进行结构化存储。

### 健康数据查询

用户可以通过自然语言查询历史健康数据：

- 按时间范围查询特定类别的健康记录
- 支持模糊查询和语义检索
- 自动关联相关健康指标，提供上下文分析

### 健康趋势总结

系统能够对用户的健康数据进行多维度分析：

- 生成周期性的健康报告
- 识别健康指标的变化趋势
- 发现潜在的健康风险和改善机会
- 提供个性化的健康建议

### 智能问答

基于医学知识库的 RAG 问答系统：

- 整合 MedlinePlus 权威医学知识库
- 结合用户个人健康档案进行个性化回答
- 支持多轮对话，上下文理解
- 多路召回融合，确保答案准确全面

### 个性化健康计划

基于用户健康档案的智能计划生成：

- 饮食计划、运动计划、睡眠计划等多种类型
- 支持多轮对话，智能收集用户信息
- 自动生成可执行的健康计划
- 可导出至 Google Sheets 进行追踪管理

---

## 技术架构

### 技术栈

| 组件 | 技术选型 | 版本 | 用途 |
|------|----------|------|------|
| 后端框架 | Spring Boot | 3.3.6 | Web 服务框架 |
| AI 框架 | LangChain4j | 1.0.1-beta6 | LLM 集成框架 |
| 大语言模型 | 阿里云 Qwen | 通义千问-plus | 文本生成与理解 |
| 向量数据库 | Milvus | 2.6.7 | 语义检索 |
| 全文搜索 | Elasticsearch | 8.17.0 | 关键词检索 |
| 关系数据库 | MySQL | 8.0.41 | 结构化数据存储 |
| 缓存与会话 | Redis | 7.2.1 | 会话管理与限流 |
| Java 版本 | JDK | 17 | 运行环境 |

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户端（前端）                             │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP/WebSocket
┌─────────────────────────────▼───────────────────────────────────┐
│                      ChatController                             │
│                      聊天主控制器                                  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    ChatDispatchService                          │
│                    聊天分发服务（意图识别）                         │
└──────┬──────────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │          │
       ▼          ▼          ▼          ▼          ▼
   RECORD     QUERY     SUMMARY       QA        PLAN
   日志记录    数据查询    趋势总结     智能问答   健康计划

┌─────────────────────────────────────────────────────────────────┐
│                      意图识别服务                                  │
│                  IntentService（LLM 驱动）                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    核心服务层                                     │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│ LogRecord   │ LogQuery    │ LogSummary  │ QAService             │
│ Service     │ Service     │ Service     │ （RAG核心）           │
│ 日志记录     │ 日志查询     │ 日志总结     │ 多路召回 + Rerank     │
└──────┬──────┴──────┬──────┴──────┬──────┴───────────┬───────────┘
       │             │             │                   │
       ▼             ▼             ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      数据存储层                                   │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│   MySQL     │  Milvus     │ Elasticsearch│      Redis           │
│  结构化存储   │  向量检索    │  全文检索     │   会话/限流/并发      │
└─────────────┴─────────────┴─────────────┴───────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   PlannerAgent（计划 Agent）                      │
│              LangChain4j @AiService 实现                          │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│ getUser      │ search      │ search      │ savePlan/            │
│ Health       │ User        │ Health       │ updatePlan           │
│ Profile      │ Logs        │ Knowledge    │                      │
└─────────────┴─────────────┴─────────────┴───────────────────────┘
```

### RAG 问答架构

```
用户问题
    │
    ▼
┌──────────────────┐
│  Query 重写服务   │
│ QueryRewrite     │
└────────┬─────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────────┐
│                    多路并行召回（4 线程并发）                      │
├────────────┬────────────┬────────────┬────────────────────────┤
│ Milvus     │ BM25       │ 用户日志    │ 用户日志                │
│ 知识库召回   │ 知识库召回   │ Milvus召回  │ BM25召回              │
│ topK=15    │ topK=5     │ topK=10    │ topK=5                 │
└─────┬──────┴─────┬──────┴─────┬──────┴────────┬───────────────┘
      │            │            │                │
      └────────────┴─────┬──────┴────────────────┘
                         ▼
              ┌───────────────────────┐
              │   RRF 融合排序         │
              │   (k=60, 倒数排名融合)  │
              └───────────┬───────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   BGE Rerank 精排      │
              │   (批处理并发)         │
              └───────────┬───────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   回复生成服务          │
              │ ReplyGeneration       │
              └───────────┬───────────┘
                          │
                          ▼
                       最终回答
```

---

## 项目结构

```
demoapp/
├── src/
│   ├── main/
│   │   ├── java/org/bupt/demoapp/
│   │   │   │
│   │   │   ├── controller/          # REST API 控制器
│   │   │   │   └── ChatController.java
│   │   │   │       聊天主控制器，处理所有聊天相关请求
│   │   │   │
│   │   │   ├── service/              # 业务逻辑接口
│   │   │   │   ├── ChatDispatchService.java       # 聊天分发服务
│   │   │   │   ├── ChatTaskService.java            # 异步任务服务
│   │   │   │   ├── ChatRateLimitService.java       # 速率限制服务
│   │   │   │   ├── ChatGlobalConcurrencyService.java    # 全局并发控制
│   │   │   │   ├── ChatFunctionConcurrencyService.java  # 功能级并发控制
│   │   │   │   ├── PlanService.java             # 健康计划服务
│   │   │   │   ├── LogRecordService.java         # 日志记录服务
│   │   │   │   ├── LogQueryService.java          # 日志查询服务
│   │   │   │   ├── LogSummaryService.java        # 日志总结服务
│   │   │   │   ├── QAService.java                # 智能问答服务
│   │   │   │   ├── GetChatHistoryService.java     # 获取聊天历史
│   │   │   │   └── SaveChatHistoryService.java    # 保存聊天历史
│   │   │   │
│   │   │   ├── serviceImp/            # 业务逻辑实现
│   │   │   │   ├── ChatDispatchServiceImp.java
│   │   │   │   │       聊天分发实现，根据意图类型路由到对应服务
│   │   │   │   ├── ChatTaskServiceImp.java
│   │   │   │   │       异步任务实现，支持 PLAN/QA/SUMMARY 异步处理
│   │   │   │   ├── ChatRateLimitServiceImp.java
│   │   │   │   │       令牌桶限流实现，使用 Redis Lua 脚本
│   │   │   │   ├── ChatGlobalConcurrencyServiceImp.java
│   │   │   │   │       全局并发控制实现
│   │   │   │   ├── ChatFunctionConcurrencyServiceImp.java
│   │   │   │   │       功能级并发控制，按意图类型限流
│   │   │   │   ├── PlanServiceImp.java
│   │   │   │   │       健康计划服务实现，调用 PlannerAgent
│   │   │   │   ├── LogRecordServiceImp.java
│   │   │   │   │       日志记录实现，存储到 MySQL/Milvus/ES
│   │   │   │   ├── LogQueryServiceImp.java
│   │   │   │   │       日志查询实现，使用 Milvus + ES 检索
│   │   │   │   ├── LogSummaryServiceImp.java
│   │   │   │   │       日志总结实现，支持单批/多批生成
│   │   │   │   ├── QAServiceImp.java
│   │   │   │   │       核心 RAG 实现，多路召回→RRF融合→Rerank
│   │   │   │   ├── GetChatHistoryServiceImp.java
│   │   │   │   │       从 Redis 获取聊天历史
│   │   │   │   └── SaveChatHistoryServiceImp.java
│   │   │   │           保存聊天历史到 Redis
│   │   │   │
│   │   │   ├── entity/                # 数据实体
│   │   │   │   ├── ChatLog.java       # 用户聊天日志实体
│   │   │   │   │       - logId: 日志唯一标识（雪花算法生成）
│   │   │   │   │       - userId: 用户标识
│   │   │   │   │       - memoryId: 会话标识
│   │   │   │   │       - rawText: 原始日志内容
│   │   │   │   │       - msg: 处理后日志内容
│   │   │   │   │       - intent: 意图类型
│   │   │   │   │       - createTime: 创建时间
│   │   │   │   │       - eventDate: 事件发生日期
│   │   │   │   ├── Plan.java          # 健康计划实体
│   │   │   │   │       - planId: 计划唯一标识
│   │   │   │   │       - userId: 用户标识
│   │   │   │   │       - planType: 计划类型（饮食/运动/睡眠等）
│   │   │   │   │       - planContent: 计划内容
│   │   │   │   │       - planStatus: 状态（DRAFT/ACTIVE/COMPLETED）
│   │   │   │   │       - createTime/updateTime: 时间戳
│   │   │   │   └── Intent.java        # 意图枚举
│   │   │   │           RECORD, QUERY, SUMMARY, QA, PLAN, UNKNOWN
│   │   │   │
│   │   │   ├── dto/                   # 数据传输对象
│   │   │   │   ├── ChatRequest.java   # 聊天请求（memoryId, msg）
│   │   │   │   ├── ChatResponse.java  # 聊天响应（logId, intent, reply）
│   │   │   │   ├── ChatTaskResponse.java  # 异步任务响应（taskId, status）
│   │   │   │   ├── ChatHistoryResponse.java   # 聊天历史响应
│   │   │   │   └── ConversationResponse.java   # 会话列表响应
│   │   │   │
│   │   │   ├── mapper/                # MyBatis 数据映射
│   │   │   │   ├── LogMapper.java     # 用户日志数据访问接口
│   │   │   │   ├── LogMapper.xml      # 用户日志 SQL 映射
│   │   │   │   ├── PlanMapper.java    # 健康计划数据访问接口
│   │   │   │   └── PlanMapper.xml     # 健康计划 SQL 映射
│   │   │   │
│   │   │   ├── aiservice/            # AI 服务接口层
│   │   │   │   ├── IntentService.java              # 意图识别服务
│   │   │   │   ├── QueryRewriteService.java         # 查询重写服务
│   │   │   │   ├── ReplyGenerationService.java       # 回复生成服务
│   │   │   │   ├── TimeRangeExtractionService.java   # 时间范围提取服务
│   │   │   │   ├── EventDateExtractionService.java   # 事件日期提取服务
│   │   │   │   ├── SummaryQueryAnalysisService.java   # 总结查询分析服务
│   │   │   │   └── SummaryGenerationService.java     # 总结生成服务
│   │   │   │
│   │   │   ├── agent/                 # AI Agent 实现
│   │   │   │   ├── PlannerAgent.java           # 计划规划 Agent（LangChain4j @AiService）
│   │   │   │   ├── PlannerAgentTools.java      # Agent 工具集
│   │   │   │   │       - getUserHealthProfile  获取用户健康档案
│   │   │   │   │       - searchUserLogs        检索用户历史日志
│   │   │   │   │       - searchHealthKnowledge 检索医学知识库
│   │   │   │   │       - savePlan              保存计划到数据库
│   │   │   │   │       - updatePlan            更新计划状态
│   │   │   │   │       - createGoogleSheet     创建 Google Sheets
│   │   │   │   │       - readGoogleSheet       读取表格内容
│   │   │   │   └── PlannerContext.java         # Agent 线程上下文管理
│   │   │   │
│   │   │   ├── config/                # Spring 配置类
│   │   │   │   ├── MilvusConfig.java           # Milvus 和 Elasticsearch 配置
│   │   │   │   ├── AsyncConfig.java            # 异步线程池配置
│   │   │   │   ├── KnowledgeBaseInitializer.java   # 知识库初始化（启动时加载）
│   │   │   │   └── commonConfig.java            # 通用配置（ChatMemoryProvider）
│   │   │   │
│   │   │   ├── common/                # 公共工具类
│   │   │   │   ├── Messages.java              # 前端显示消息常量
│   │   │   │   ├── MemoryIds.java             # memoryId 解析工具
│   │   │   │   ├── SnowflakeIdGenerator.java   # 雪花 ID 生成器
│   │   │   │   └── DistributedSessionStateService.java   # 分布式会话锁
│   │   │   │
│   │   │   ├── repository/            # 数据仓库
│   │   │   │   └── RedisChatMemory.java       # Redis 实现的 ChatMemoryStore
│   │   │   │
│   │   │   └── util/                   # 工具类
│   │   │       ├── MedlinePlusXmlParser.java  # MedlinePlus XML 解析器
│   │   │       └── MedlinePlusTopicSplitter.java   # 文档切割器（三级策略）
│   │   │
│   │   └── resources/
│   │       ├── application.yaml       # 主配置文件
│   │       ├── application.properties  # 附加配置
│   │       ├── prompts/                # AI 提示词模板
│   │       │   ├── intent_classification.txt   # 意图分类
│   │       │   ├── plan_agent_system.txt       # 计划 Agent 系统提示词
│   │       │   ├── query_rewrite.txt           # 查询重写
│   │       │   ├── qa_reply_generation.txt     # QA 回复生成
│   │       │   ├── record_reply.txt            # 记录确认回复
│   │       │   ├── query_reply_generation.txt  # 查询回复生成
│   │       │   ├── time_range_extraction.txt   # 时间范围提取
│   │       │   ├── event_date_extraction.txt   # 事件日期提取
│   │       │   ├── summary_query_analysis.txt  # 总结查询分析
│   │       │   ├── summary_generation.txt      # 总结生成
│   │       │   └── summary_merge.txt           # 总结合并
│   │       ├── scripts/                # Lua 脚本
│   │       │   └── rate_limit_token_bucket.lua   # 令牌桶限流
│   │       └── content/                # 知识库内容
│   │           └── MedlinePlus.txt     # MedlinePlus 医学知识库
│   │
│   └── test/java/                      # 测试代码
│       └── org/bupt/demoapp/rag/
│           ├── RetrievalRelevanceTest.java      # RAG 检索相关性测试
│           ├── OptimizedRetrievalTest.java      # 优化 RAG 检索测试
│           └── KnowledgeCoverageTest.java      # 知识库覆盖测试
│
├── mcp_server/                         # MCP 服务器（Python）
│   └── server.py                       # Google Sheets MCP 服务器
│
├── data/                               # 数据文件目录
├── logs/                               # 日志文件目录
├── pom.xml                             # Maven 项目配置
└── README.md                           # 项目说明文档
```

---

## 核心模块详解

### 1. 聊天控制器（ChatController）

聊天主入口，负责处理所有用户聊天请求。

**支持的接口：**

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| `/chat` | POST | 发送聊天消息 |
| `/chat/tasks/{taskId}` | GET | 查询异步任务状态 |
| `/chat/conversations` | GET | 获取会话列表 |
| `/chat/history` | GET | 获取聊天历史 |

### 2. 意图识别与分发

系统采用 LLM 驱动的意图识别机制，将用户输入分为六类：

| 意图类型 | 说明 | 处理服务 |
|----------|------|----------|
| `RECORD` | 健康日志记录 | LogRecordServiceImp |
| `QUERY` | 健康数据查询 | LogQueryServiceImp |
| `SUMMARY` | 健康趋势总结 | LogSummaryServiceImp |
| `QA` | 智能问答 | QAServiceImp |
| `PLAN` | 健康计划生成 | PlanServiceImp |
| `UNKNOWN` | 未知意图 | 默认回复 |

**识别流程：**

```
用户输入 → IntentService（LLM 分析） → Intent 枚举 → 对应 Service 处理
```

### 3. 日志记录服务（LogRecordService）

将用户自然语言描述的健康数据转换为结构化记录。

**处理流程：**

1. 生成雪花算法唯一 ID
2. LLM 提取事件发生日期
3. 存储到 MySQL（ChatLog 表）
4. 生成文本向量 embedding
5. 存储到 Milvus（向量检索用）
6. 同步到 Elasticsearch（全文检索用）
7. LLM 生成确认回复
8. 保存聊天历史到 Redis

### 4. 日志查询服务（LogQueryService）

支持语义化的健康数据查询。

**处理流程：**

1. LLM 解析查询条件（时间范围、类别等）
2. Milvus 向量检索（带 user_id 和 event_date 过滤）
3. MySQL 拉取匹配日志的完整内容
4. ReplyGenerationService 生成自然语言回复
5. 保存聊天历史

### 5. 日志总结服务（LogSummaryService）

生成周期性健康报告。

**处理流程：**

1. LLM 分析查询意图（时间范围 + 健康类别）
2. 数据获取策略：
   - 无类别指定：从 MySQL 直接查询
   - 有类别指定：Milvus 多批次召回
3. 数据量检查（上限 30000 条）
4. 总结生成策略：
   - 少于等于 10000 条：单批生成
   - 超过 10000 条：分批生成后合并

### 6. RAG 问答服务（QAService）—— 核心模块

基于多路召回 RAG 的智能问答系统。

**完整处理流程：**

```
用户问题
    │
    ▼
┌──────────────────┐
│  Query 重写       │   使用 LLM 将用户问题转化为更适合检索的形式
│  QueryRewrite    │
└────────┬─────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────────┐
│                    四路并行召回（线程池并发）                       │
├────────────────┬────────────────┬────────────────┬─────────────┤
│  知识库向量召回  │   知识库 BM25   │  用户日志向量    │ 用户日志 BM25 │
│   Milvus       │  Elasticsearch  │    Milvus     │ Elasticsearch│
│   topK=15      │   topK=5       │   topK=10     │  topK=5      │
└───────┬────────┴───────┬────────┴──────┬────────┴──────┬───────┘
        │                │               │               │
        └────────────────┴───────┬───────┴───────────────┘
                                 ▼
                      ┌─────────────────────┐
                      │   RRF 融合排序       │
                      │ Reciprocal Rank     │
                      │ Fusion (k=60)        │
                      └──────────┬──────────┘
                                 │
                                 ▼
                      ┌─────────────────────┐
                      │   BGE Rerank 精排    │
                      │  Cross-Encoder      │
                      │  (批处理并发)        │
                      └──────────┬──────────┘
                                 │
                                 ▼
                      ┌─────────────────────┐
                      │   回复生成            │
                      │ ReplyGeneration     │
                      │ (整合上下文)          │
                      └─────────────────────┘
```

**关键技术点：**

- **多路召回**：结合向量检索和全文检索的优点
- **RRF 融合**：使用倒数排名融合算法合并多路结果
- **Rerank 精排**：使用 BGE Cross-Encoder 进行二次排序

### 7. 健康计划 Agent（PlannerAgent）

基于 LangChain4j @AiService 实现的对话式计划生成 Agent。

**Agent 架构：**

```
┌─────────────────────────────────────────────────────────────────┐
│                     PlannerAgent（三阶段流程）                     │
├─────────────────────────────────────────────────────────────────┤
│  阶段一：信息收集                                                 │
│  ├── 步骤1：基础信息校验（计划类型/目标/时间范围）                     │
│  └── 步骤2：个性化追问（最多5轮）                                   │
│      ├── 调用 getUserHealthProfile（获取健康档案）                  │
│      ├── 调用 searchUserLogs（检索历史日志）                        │
│      └── 调用 searchHealthKnowledge（检索医学知识）                 │
├─────────────────────────────────────────────────────────────────┤
│  阶段二：计划生成与迭代                                            │
│  ├── 调用 savePlan（保存到数据库 + 创建 Google Sheet）              │
│  └── 调用 updatePlan（根据反馈迭代优化）                            │
├─────────────────────────────────────────────────────────────────┤
│  阶段三：确认生效                                                  │
│  └── updatePlan(status=ACTIVE)                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Agent 工具集：**

| 工具名称 | 功能说明 | 使用阶段 |
|----------|----------|----------|
| `getUserHealthProfile` | 获取用户健康档案摘要 | 信息收集 |
| `searchUserLogs` | 检索用户历史健康日志 | 信息收集 |
| `searchHealthKnowledge` | 检索权威医学健康知识 | 信息收集 |
| `savePlan` | 保存计划到 MySQL 并创建 Google Sheet | 计划生成 |
| `updatePlan` | 更新计划内容或状态 | 计划生成/确认 |
| `createGoogleSheet` | 创建 Google Sheets 在线表格 | 计划生成 |
| `readGoogleSheet` | 读取用户编辑的表格内容 | 计划迭代 |

### 8. 速率限制与并发控制

系统实现三级限流机制，保护服务稳定性：

| 控制层级 | 实现方式 | 配置参数 |
|----------|----------|----------|
| **全局并发控制** | AtomicInteger + Redis | 最大并发数 |
| **功能级并发控制** | Semaphore + Redis | PLAN=3, QA=8, SUMMARY=6 |
| **速率限制** | Redis 令牌桶 | capacity=50, refill=30/s |

---

## API 接口说明

### 1. 发送聊天消息

**请求：**

```
POST /chat
Content-Type: application/json

{
    "memoryId": "user123_session456",
    "msg": "今天跑了5公里，感觉很好"
}
```

**响应（同步模式）：**

```json
{
    "logId": 7234567890123456789,
    "intent": "RECORD",
    "reply": "已记录您今天跑了5公里。继续保持这个运动习惯对健康很有益处。",
    "mysqlStored": true,
    "milvusStored": true
}
```

**响应（异步模式 - PLAN/QA/SUMMARY）：**

```json
{
    "taskId": "task_abc123",
    "status": "PROCESSING",
    "intent": "PLAN",
    "reply": null
}
```

### 2. 查询异步任务

```
GET /chat/tasks/{taskId}
```

**响应：**

```json
{
    "taskId": "task_abc123",
    "status": "COMPLETED",
    "intent": "PLAN",
    "reply": "已为您生成运动计划..."
}
```

### 3. 获取会话列表

```
GET /chat/conversations
```

**响应：**

```json
{
    "conversations": [
        {
            "conversationId": "user123_session456",
            "title": "运动记录",
            "lastMessage": "已记录您今天跑了5公里...",
            "timestamp": "2024-01-15T10:30:00"
        }
    ]
}
```

### 4. 获取聊天历史

```
GET /chat/history?memoryId=user123_session456&page=1&size=20
```

**响应：**

```json
{
    "memoryId": "user123_session456",
    "page": 1,
    "size": 20,
    "total": 45,
    "messages": [
        {
            "role": "user",
            "content": "今天跑了5公里"
        },
        {
            "role": "assistant",
            "content": "已记录..."
        }
    ]
}
```

---

## 配置说明

### application.yaml 主要配置

```yaml
# 阿里云 DashScope（Qwen 大模型）
langchain4j:
  open-ai:
    api-key: ${DASHSCOPE_API_KEY}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model-name: qwen-plus

# Milvus 向量数据库
langchain4j:
  milvus:
    service-name: ${MILVUS_SERVICE_NAME}
    collection-name: knowledge_base_vectors

# Elasticsearch
elasticsearch:
  host: ${ES_HOST}
  port: ${ES_PORT}
  knowledge-index: knowledge_index
  user-log-index: user-log-index

# Redis
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379

# MySQL
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:3306/health_app
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
```

### application.properties 配置项

```properties
# 并发控制配置
chat.concurrency.enabled=true
chat.concurrency.global.max-concurrency=40

# 功能级并发配置
chat.intent-concurrency.enabled=true
chat.intent-concurrency.plan.max-concurrency=3
chat.intent-concurrency.qa.max-concurrency=8
chat.intent-concurrency.summary.max-concurrency=6

# 速率限制配置
chat.ratelimit.enabled=true
chat.ratelimit.token-bucket.capacity=50
chat.ratelimit.token-bucket.refill-rate=30
```

---

## 数据库表结构

### ChatLog 表（用户日志）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| log_id | BIGINT | 日志唯一标识（雪花算法生成） |
| user_id | VARCHAR(255) | 用户标识 |
| memory_id | VARCHAR(255) | 会话标识 |
| raw_text | TEXT | 用户原始输入 |
| msg | TEXT | 处理后的日志内容 |
| intent | VARCHAR(50) | 意图类型 |
| create_time | DATETIME | 创建时间 |
| event_date | DATE | 事件发生日期 |

### Plan 表（健康计划）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| plan_id | BIGINT | 计划唯一标识 |
| user_id | VARCHAR(255) | 用户标识 |
| plan_type | VARCHAR(50) | 计划类型 |
| plan_content | TEXT | 计划内容（JSON 格式） |
| plan_status | VARCHAR(50) | 状态：草案/生效/完成 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

---

## Redis 数据结构

| Key 模式 | 数据类型 | 说明 |
|----------|----------|------|
| `chat:memory:{memoryId}` | String | AI 会话记忆（ChatMemoryStore） |
| `chat:display:{memoryId}` | List | 用户可见聊天历史 |
| `chat:task:{taskId}` | String | 异步任务状态和结果 |
| `chat:task:memory:{memoryId}` | String | 会话激活的任务 ID |
| `chat:lock:{memoryId}` | String | 分布式会话锁 |
| `chat:plan:{memoryId}` | String | 计划流程状态 |
| `chat:concurrency:global` | String | 全局并发计数 |
| `chat:concurrency:intent:{intent}` | String | 功能级并发计数 |
| `chat:ratelimit:global` | Hash | 令牌桶限流状态 |

---

## 快速开始

### 环境要求

- Java JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.2+
- Milvus 2.6+
- Elasticsearch 8.x

### 安装步骤

1. **克隆项目**

```bash
git clone https://github.com/obr310/demoapp.git
cd demoapp
```

2. **配置环境变量**

```bash
export DASHSCOPE_API_KEY=your_api_key
export MILVUS_SERVICE_NAME=your_milvus_endpoint
export ES_HOST=localhost
export ES_PORT=9200
export REDIS_HOST=localhost
export MYSQL_HOST=localhost
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password
```

3. **初始化数据库**

```sql
CREATE DATABASE health_app;
```

4. **编译运行**

```bash
mvn clean install
mvn spring-boot:run
```

5. **验证服务**

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"memoryId": "test123", "msg": "今天跑了5公里"}'
```

---

## 开发指南

### 添加新的意图类型

1. 在 `Intent.java` 枚举中添加新类型
2. 实现对应的 Service 接口和实现类
3. 在 `ChatDispatchServiceImp` 中添加路由逻辑
4. 添加对应的提示词模板（可选）

### 自定义 Agent

项目使用 LangChain4j 的 `@AiService` 注解实现 Agent。要创建新的 Agent：

1. 定义 Agent 接口，使用 `@AiService` 注解
2. 实现工具类，添加 `@Tool` 注解
3. 在 `application.yaml` 中配置系统提示词

### 扩展知识库

MedlinePlus 知识库在应用启动时自动加载。如需添加自定义知识：

1. 修改 `resources/content/MedlinePlus.txt`
2. 或实现 `KnowledgeBaseInitializer` 的扩展方法
3. 重新启动应用以触发加载

---

## 常见问题

### Q: 异步任务超时了怎么办？

异步任务有默认超时设置。可通过 `application.properties` 调整：

```properties
chat.task.timeout-seconds=300
```

### Q: 如何查看 RAG 检索效果？

运行测试类 `RetrievalRelevanceTest` 可验证检索相关性。

### Q: 限流触发后如何处理？

系统返回 429 状态码。建议前端实现指数退避重试机制。

### Q: 知识库更新后需要重启吗？

默认需要重启。如需热更新，可扩展 `KnowledgeBaseInitializer` 实现增量更新。

