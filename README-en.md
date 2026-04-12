# DemoApp - Intelligent Health Assistant RAG Application

An intelligent health assistant application built on LangChain4j and Milvus, supporting personalized health log recording and intelligent Q&A.

## Front code now moved to https://github.com/obr310/healthy-front.git

## Table of Contents

- [Project Overview](#project-overview)
- [Features](#features)
- [Technical Architecture](#technical-architecture)
- [Project Structure](#project-structure)
- [Core Modules](#core-modules)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Quick Start](#quick-start)
- [Development Guide](#development-guide)
- [FAQ](#faq)

---

## Project Overview

DemoApp is an intelligent assistant focused on personal health management. Users can interact with the system using natural language to complete health log recording, health data querying, health trend summarization, and intelligent Q&A. The system adopts a RAG (Retrieval-Augmented Generation) architecture, combining vector databases and full-text search engines to provide accurate, personalized health recommendations.

---

## Features

### Health Log Recording

Supports structured recording of various health data:

- **Diet Recording**: Daily food intake, calories, nutrients, etc.
- **Sleep Recording**: Sleep duration, sleep quality, wake-up state, etc.
- **Exercise Recording**: Exercise type, duration, intensity, etc.
- **Mood Recording**: Daily emotional state, stress level, etc.

Users describe their health status in natural language, and the system automatically extracts key information for structured storage.

### Health Data Query

Users can query historical health data through natural language:

- Query specific categories of health records by time range
- Supports fuzzy matching and semantic retrieval
- Automatically correlates related health indicators with contextual analysis

### Health Trend Summarization

The system performs multi-dimensional analysis on user health data:

- Generates periodic health reports
- Identifies changes in health indicators
- Discovers potential health risks and improvement opportunities
- Provides personalized health recommendations

### Intelligent Q&A

A RAG-based Q&A system powered by medical knowledge base:

- Integrates MedlinePlus authoritative medical knowledge base
- Provides personalized answers based on individual health profiles
- Supports multi-turn conversations with context understanding
- Multi-channel retrieval fusion ensures accurate and comprehensive answers

### Personalized Health Plans

Intelligent plan generation based on user health profiles:

- Multiple plan types: diet plans, exercise plans, sleep plans, etc.
- Supports multi-turn dialogue for intelligent information collection
- Automatically generates executable health plans
- Exportable to Google Sheets for tracking and management

---

## Technical Architecture

### Tech Stack

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Backend Framework | Spring Boot | 3.3.6 | Web service framework |
| AI Framework | LangChain4j | 1.0.1-beta6 | LLM integration framework |
| LLM | Alibaba Qwen | Qwen-plus | Text generation and understanding |
| Vector Database | Milvus | 2.6.7 | Semantic retrieval |
| Full-text Search | Elasticsearch | 8.17.0 | Keyword search |
| RDBMS | MySQL | 8.0.41 | Structured data storage |
| Cache & Session | Redis | 7.2.1 | Session management and rate limiting |
| Java Version | JDK | 17 | Runtime environment |

### System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client (Frontend)                      │
└───────────────��─────────────┬───────────────────────────────────┘
                              │ HTTP/WebSocket
┌─────────────────────────────▼───────────────────────────────────┐
│                      ChatController                             │
│                      Main Chat Controller                        │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    ChatDispatchService                          │
│                    Chat Dispatch (Intent Recognition)           │
└──────┬──────────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │          │
       ▼          ▼          ▼          ▼          ▼
   RECORD     QUERY     SUMMARY       QA        PLAN
   Log Rec   Data Query  Summary    Q&A      Health Plan

┌─────────────────────────────────────────────────────────────────┐
│                      Intent Recognition Service                 │
│                  IntentService (LLM-driven)                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      Core Service Layer                         │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│ LogRecord   │ LogQuery    │ LogSummary  │ QAService             │
│ Service     │ Service     │ Service     │ (RAG Core)            │
└──────┬──────┴──────┬──────┴──────┬──────┴───────────┬───────────┘
       │             │             │                   │
       ▼             ▼             ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data Storage Layer                          │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│   MySQL     │  Milvus     │Elasticsearch│      Redis            │
│  Structure  │  Vector     │ Full-text   │ Session/Rate Limit   │
└─────────────┴─────────────┴─────────────┴───────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   PlannerAgent (Health Plan Agent)              │
│              LangChain4j @AiService Implementation               │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│ getUser     │ search      │ search      │ savePlan/            │
│ Health      │ User        │ Health      │ updatePlan           │
│ Profile     │ Logs        │ Knowledge   │                      │
└─────────────┴─────────────┴─────────────┴───────────────────────┘
```

### RAG Q&A Architecture

```
User Question
    │
    ▼
┌──────────────────┐
│  Query Rewrite   │
│ QueryRewrite     │
└────────┬─────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────────┐
│                  Multi-channel Parallel Recall (4 threads)     │
├────────────┬────────────┬────────────┬────────────────────────┤
│ Knowledge  │ Knowledge  │ User Log   │ User Log             │
│ Vector     │ BM25       │ Vector     │ BM25                 │
│ Milvus     │ Elastic    │ Milvus     │ Elastic              │
│ topK=15    │ topK=5     │ topK=10   │ topK=5               │
└─────┬──────┴─────┬──────┴─────┬──────┴────────┬───────────────┘
      │            │            │                │
      └────────────┴─────┬──────┴────────────────┘
                         ▼
              ┌───────────────────────┐
              │   RRF Fusion          │
              │ (k=60, Reciprocal     │
              │  Rank Fusion)         │
              └───────────┬───────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   BGE Rerank          │
              │  Cross-Encoder        │
              │  (Batch Processing)   │
              └───────────┬───────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   Reply Generation     │
              │ ReplyGeneration       │
              └───────────┬───────────┘
                          │
                          ▼
                      Final Answer
```

---

## Project Structure

```
demoapp/
├── src/
│   ├── main/
│   │   ├── java/org/bupt/demoapp/
│   │   │   │
│   │   │   ├── controller/          # REST API Controllers
│   │   │   │   └── ChatController.java
│   │   │   │       Main chat controller handling all chat requests
│   │   │   │
│   │   │   ├── service/              # Business Logic Interfaces
│   │   │   │   ├── ChatDispatchService.java       # Chat dispatch service
│   │   │   │   ├── ChatTaskService.java            # Async task service
│   │   │   │   ├── ChatRateLimitService.java       # Rate limit service
│   │   │   │   ├── ChatGlobalConcurrencyService.java    # Global concurrency control
│   │   │   │   ├── ChatFunctionConcurrencyService.java  # Function-level concurrency
│   │   │   │   ├── PlanService.java             # Health plan service
│   │   │   │   ├── LogRecordService.java         # Log recording service
│   │   │   │   ├── LogQueryService.java          # Log query service
│   │   │   │   ├── LogSummaryService.java        # Log summary service
│   │   │   │   ├── QAService.java                # Intelligent Q&A service
│   │   │   │   ├── GetChatHistoryService.java     # Get chat history
│   │   │   │   └── SaveChatHistoryService.java    # Save chat history
│   │   │   │
│   │   │   ├── serviceImp/            # Business Logic Implementations
│   │   │   │   ├── ChatDispatchServiceImp.java
│   │   │   │   │       Chat dispatch implementation, routes by intent type
│   │   │   │   ├── ChatTaskServiceImp.java
│   │   │   │   │       Async task implementation, supports PLAN/QA/SUMMARY
│   │   │   │   ├── ChatRateLimitServiceImp.java
│   │   │   │   │       Token bucket rate limiting using Redis Lua script
│   │   │   │   ├── ChatGlobalConcurrencyServiceImp.java
│   │   │   │   │       Global concurrency control implementation
│   │   │   │   ├── ChatFunctionConcurrencyServiceImp.java
│   │   │   │   │       Function-level concurrency by intent type
│   │   │   │   ├── PlanServiceImp.java
│   │   │   │   │       Health plan service, invokes PlannerAgent
│   │   │   │   ├── LogRecordServiceImp.java
│   │   │   │   │       Log recording, stores to MySQL/Milvus/ES
│   │   │   │   ├── LogQueryServiceImp.java
│   │   │   │   │       Log query using Milvus + ES retrieval
│   │   │   │   ├── LogSummaryServiceImp.java
│   │   │   │   │       Log summary, single/multi-batch generation
│   │   │   │   ├── QAServiceImp.java
│   │   │   │   │       Core RAG: multi-channel recall → RRF → Rerank
│   │   │   │   ├── GetChatHistoryServiceImp.java
│   │   │   │   │       Get chat history from Redis
│   │   │   │   └── SaveChatHistoryServiceImp.java
│   │   │   │           Save chat history to Redis
│   │   │   │
│   │   │   ├── entity/                # Data Entities
│   │   │   │   ├── ChatLog.java       # User chat log entity
│   │   │   │   │       - logId: Unique log ID (Snowflake)
│   │   │   │   │       - userId: User identifier
│   │   │   │   │       - memoryId: Session identifier
│   │   │   │   │       - rawText: Original log content
│   │   │   │   │       - msg: Processed log content
│   │   │   │   │       - intent: Intent type
│   │   │   │   │       - createTime: Creation time
│   │   │   │   │       - eventDate: Event date
│   │   │   │   ├── Plan.java          # Health plan entity
│   │   │   │   │       - planId: Plan unique ID
│   │   │   │   │       - userId: User identifier
│   │   │   │   │       - planType: Plan type (diet/exercise/sleep)
│   │   │   │   │       - planContent: Plan content
│   │   │   │   │       - planStatus: Status (DRAFT/ACTIVE/COMPLETED)
│   │   │   │   │       - createTime/updateTime: Timestamps
│   │   │   │   └── Intent.java        # Intent enum
│   │   │   │           RECORD, QUERY, SUMMARY, QA, PLAN, UNKNOWN
│   │   │   │
│   │   │   ├── dto/                   # Data Transfer Objects
│   │   │   │   ├── ChatRequest.java   # Chat request (memoryId, msg)
│   │   │   │   ├── ChatResponse.java  # Chat response (logId, intent, reply)
│   │   │   │   ├── ChatTaskResponse.java  # Async task response (taskId, status)
│   │   │   │   ├── ChatHistoryResponse.java   # Chat history response
│   │   │   │   └── ConversationResponse.java   # Conversation list response
│   │   │   │
│   │   │   ├── mapper/                # MyBatis Data Mappers
│   │   │   │   ├── LogMapper.java     # User log data access interface
│   │   │   │   ├── LogMapper.xml      # User log SQL mapping
│   │   │   │   ├── PlanMapper.java    # Health plan data access interface
│   │   │   │   └── PlanMapper.xml     # Health plan SQL mapping
│   │   │   │
│   │   │   ├── aiservice/            # AI Service Interfaces
│   │   │   │   ├── IntentService.java              # Intent recognition
│   │   │   │   ├── QueryRewriteService.java         # Query rewriting
│   │   │   │   ├── ReplyGenerationService.java       # Reply generation
│   │   │   │   ├── TimeRangeExtractionService.java   # Time range extraction
│   │   │   │   ├── EventDateExtractionService.java   # Event date extraction
│   │   │   │   ├── SummaryQueryAnalysisService.java  # Summary query analysis
│   │   │   │   └── SummaryGenerationService.java     # Summary generation
│   │   │   │
│   │   │   ├── agent/                 # AI Agent Implementation
│   │   │   │   ├── PlannerAgent.java           # Plan agent (LangChain4j @AiService)
│   │   │   │   ├── PlannerAgentTools.java      # Agent toolset
│   │   │   │   │       - getUserHealthProfile  Get user health profile
│   │   │   │   │       - searchUserLogs        Search user logs
│   │   │   │   │       - searchHealthKnowledge Search medical knowledge
│   │   │   │   │       - savePlan              Save plan to database
│   │   │   │   │       - updatePlan            Update plan status
│   │   │   │   │       - createGoogleSheet     Create Google Sheets
│   │   │   │   │       - readGoogleSheet       Read sheet content
│   │   │   │   └── PlannerContext.java         # Agent thread context
│   │   │   │
│   │   │   ├── config/                # Spring Configuration Classes
│   │   │   │   ├── MilvusConfig.java           # Milvus and ES config
│   │   │   │   ├── AsyncConfig.java            # Async thread pool config
│   │   │   │   ├── KnowledgeBaseInitializer.java   # Knowledge base init
│   │   │   │   └── commonConfig.java            # Common config (ChatMemory)
│   │   │   │
│   │   │   ├── common/                # Common Utilities
│   │   │   │   ├── Messages.java              # UI display messages
│   │   │   │   ├── MemoryIds.java             # memoryId parser
│   │   │   │   ├── SnowflakeIdGenerator.java  # Snowflake ID generator
│   │   │   │   └── DistributedSessionStateService.java   # Distributed lock
│   │   │   │
│   │   │   ├── repository/            # Data Repository
│   │   │   │   └── RedisChatMemory.java       # Redis ChatMemoryStore
│   │   │   │
│   │   │   └── util/                   # Utilities
│   │   │       ├── MedlinePlusXmlParser.java  # MedlinePlus XML parser
│   │   │       └── MedlinePlusTopicSplitter.java   # Document splitter
│   │   │
│   │   └── resources/
│   │       ├── application.yaml       # Main configuration
│   │       ├── application.properties  # Additional config
│   │       ├── prompts/                # AI Prompt Templates
│   │       │   ├── intent_classification.txt   # Intent classification
│   │       │   ├── plan_agent_system.txt       # Plan agent system prompt
│   │       │   ├── query_rewrite.txt           # Query rewrite
│   │       │   ├── qa_reply_generation.txt    # QA reply generation
│   │       │   ├── record_reply.txt            # Record confirmation
│   │       │   ├── query_reply_generation.txt  # Query reply
│   │       │   ├── time_range_extraction.txt   # Time range extraction
│   │       │   ├── event_date_extraction.txt   # Event date extraction
│   │       │   ├── summary_query_analysis.txt  # Summary query analysis
│   │       │   ├── summary_generation.txt      # Summary generation
│   │       │   └── summary_merge.txt           # Summary merge
│   │       ├── scripts/                # Lua Scripts
│   │       │   └── rate_limit_token_bucket.lua   # Token bucket rate limit
│   │       └── content/                # Knowledge Base Content
│   │           └── MedlinePlus.txt     # MedlinePlus medical knowledge
│   │
│   └── test/java/                      # Test Code
│       └── org/bupt/demoapp/rag/
│           ├── RetrievalRelevanceTest.java      # RAG retrieval test
│           ├── OptimizedRetrievalTest.java      # Optimized retrieval test
│           └── KnowledgeCoverageTest.java      # Knowledge coverage test
│
├── mcp_server/                         # MCP Server (Python)
│   └── server.py                       # Google Sheets MCP server
│
├── data/                               # Data files
├── logs/                               # Log files
├── pom.xml                             # Maven project config
└── README.md                            # This file
```

---

## Core Modules

### 1. Chat Controller (ChatController)

Main chat entry point handling all user chat requests.

**Supported Endpoints:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/chat` | POST | Send chat message |
| `/chat/tasks/{taskId}` | GET | Query async task status |
| `/chat/conversations` | GET | Get conversation list |
| `/chat/history` | GET | Get chat history |

### 2. Intent Recognition and Dispatch

The system uses LLM-driven intent recognition to classify user input into six types:

| Intent Type | Description | Handler Service |
|-------------|-------------|-----------------|
| `RECORD` | Health log recording | LogRecordServiceImp |
| `QUERY` | Health data query | LogQueryServiceImp |
| `SUMMARY` | Health trend summary | LogSummaryServiceImp |
| `QA` | Intelligent Q&A | QAServiceImp |
| `PLAN` | Health plan generation | PlanServiceImp |
| `UNKNOWN` | Unknown intent | Default response |

### 3. Log Recording Service (LogRecordService)

Converts user natural language health data into structured records.

**Process Flow:**

1. Generate Snowflake unique ID
2. LLM extracts event date
3. Store to MySQL (ChatLog table)
4. Generate text vector embedding
5. Store to Milvus (vector retrieval)
6. Sync to Elasticsearch (full-text search)
7. LLM generates confirmation reply
8. Save chat history to Redis

### 4. Log Query Service (LogQueryService)

Supports semantic health data queries.

**Process Flow:**

1. LLM parses query conditions (time range, category, etc.)
2. Milvus vector retrieval (with user_id and event_date filtering)
3. MySQL fetches matching log content
4. ReplyGenerationService generates natural language response
5. Save chat history

### 5. Log Summary Service (LogSummaryService)

Generates periodic health reports.

**Process Flow:**

1. LLM analyzes query intent (time range + category)
2. Data retrieval strategy:
   - No category specified: Query from MySQL directly
   - Category specified: Milvus multi-batch recall
3. Data volume check (max 30,000 records)
4. Summary generation strategy:
   - Less than or equal to 10,000 records: Single batch
   - More than 10,000 records: Multi-batch generation + merge

### 6. RAG Q&A Service (QAService) - Core Module

Multi-channel retrieval RAG-based intelligent Q&A system.

**Complete Process Flow:**

```
User Question
    │
    ▼
┌──────────────────┐
│  Query Rewrite   │   Use LLM to transform question for better retrieval
└────────┬─────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────────┐
│                  Four-channel Parallel Recall (Thread Pool)     │
├────────────────┬────────────────┬────────────────┬─────────────┤
│ Knowledge Vec   │ Knowledge BM25│ User Log Vec   │ User Log BM │
│ Milvus          │ Elasticsearch │ Milvus         │ Elasticsearch│
│ topK=15         │ topK=5        │ topK=10        │ topK=5      │
└───────┬────────┴───────┬────────┴──────┬────────┴──────┬───────┘
        │                │               │               │
        └────────────────┴───────┬───────┴───────────────┘
                                 ▼
                      ┌─────────────────────┐
                      │   RRF Fusion        │
                      │ Reciprocal Rank     │
                      │ Fusion (k=60)       │
                      └──────────┬──────────┘
                                 │
                                 ▼
                      ┌─────────────────────┐
                      │   BGE Rerank        │
                      │  Cross-Encoder      │
                      │  (Batch Processing) │
                      └──────────┬──────────┘
                                 │
                                 ▼
                      ┌─────────────────────┐
                      │   Reply Generation  │
                      │ ReplyGeneration     │
                      │ (Context Integrated)│
                      └─────────────────────┘
```

**Key Technical Points:**

- **Multi-channel Recall**: Combines vector retrieval and full-text search advantages
- **RRF Fusion**: Uses reciprocal rank fusion to merge multi-channel results
- **Rerank**: BGE Cross-Encoder for secondary sorting

### 7. Health Plan Agent (PlannerAgent)

Dialog-based plan generation agent implemented via LangChain4j @AiService.

**Agent Architecture:**

```
┌─────────────────────────────────────────────────────────────────┐
│                   PlannerAgent (Three-Phase Flow)              │
├─────────────────────────────────────────────────────────────────┤
│  Phase 1: Information Collection                               │
│  ├── Step 1: Basic info validation (type/goal/time range)      │
│  └── Step 2: Personalized follow-up (max 5 rounds)              │
│      ├── call getUserHealthProfile (health profile)             │
│      ├── call searchUserLogs (historical logs)                   │
│      └── call searchHealthKnowledge (medical knowledge)         │
├─────────────────────────────────────────────────────────────────┤
│  Phase 2: Plan Generation & Iteration                          │
│  ├── call savePlan (save to DB + create Google Sheet)           │
│  └── call updatePlan (iterate based on feedback)                │
├─────────────────────────────────────────────────────────────────┤
│  Phase 3: Confirmation                                         │
│  └── updatePlan(status=ACTIVE)                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Agent Toolset:**

| Tool Name | Function | Phase |
|-----------|----------|-------|
| `getUserHealthProfile` | Get user health profile summary | Collection |
| `searchUserLogs` | Search user historical health logs | Collection |
| `searchHealthKnowledge` | Search authoritative medical knowledge | Collection |
| `savePlan` | Save plan to MySQL, create Google Sheet | Generation |
| `updatePlan` | Update plan content or status | Gen/Confirm |
| `createGoogleSheet` | Create Google Sheets | Generation |
| `readGoogleSheet` | Read user-edited sheet content | Iteration |

### 8. Rate Limiting and Concurrency Control

The system implements three-level rate limiting to protect service stability:

| Level | Implementation | Config |
|-------|-----------------|--------|
| **Global Concurrency** | AtomicInteger + Redis | Max concurrency |
| **Function-level Concurrency** | Semaphore + Redis | PLAN=3, QA=8, SUMMARY=6 |
| **Rate Limiting** | Redis Token Bucket | capacity=50, refill=30/s |

---

## API Reference

### 1. Send Chat Message

**Request:**

```
POST /chat
Content-Type: application/json

{
    "memoryId": "user123_session456",
    "msg": "Ran 5km today, feeling great"
}
```

**Response (Sync Mode):**

```json
{
    "logId": 7234567890123456789,
    "intent": "RECORD",
    "reply": "Recorded your 5km run today. Keep up this exercise habit!",
    "mysqlStored": true,
    "milvusStored": true
}
```

**Response (Async Mode - PLAN/QA/SUMMARY):**

```json
{
    "taskId": "task_abc123",
    "status": "PROCESSING",
    "intent": "PLAN",
    "reply": null
}
```

### 2. Query Async Task

```
GET /chat/tasks/{taskId}
```

**Response:**

```json
{
    "taskId": "task_abc123",
    "status": "COMPLETED",
    "intent": "PLAN",
    "reply": "Your exercise plan has been generated..."
}
```

### 3. Get Conversation List

```
GET /chat/conversations
```

**Response:**

```json
{
    "conversations": [
        {
            "conversationId": "user123_session456",
            "title": "Exercise Log",
            "lastMessage": "Recorded your 5km run...",
            "timestamp": "2024-01-15T10:30:00"
        }
    ]
}
```

### 4. Get Chat History

```
GET /chat/history?memoryId=user123_session456&page=1&size=20
```

**Response:**

```json
{
    "memoryId": "user123_session456",
    "page": 1,
    "size": 20,
    "total": 45,
    "messages": [
        {
            "role": "user",
            "content": "Ran 5km today"
        },
        {
            "role": "assistant",
            "content": "Recorded..."
        }
    ]
}
```

---

## Configuration

### application.yaml Main Configuration

```yaml
# Alibaba DashScope (Qwen LLM)
langchain4j:
  open-ai:
    api-key: ${DASHSCOPE_API_KEY}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model-name: qwen-plus

# Milvus Vector Database
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

### application.properties Config Items

```properties
# Concurrency Control
chat.concurrency.enabled=true
chat.concurrency.global.max-concurrency=40

# Function-level Concurrency
chat.intent-concurrency.enabled=true
chat.intent-concurrency.plan.max-concurrency=3
chat.intent-concurrency.qa.max-concurrency=8
chat.intent-concurrency.summary.max-concurrency=6

# Rate Limiting
chat.ratelimit.enabled=true
chat.ratelimit.token-bucket.capacity=50
chat.ratelimit.token-bucket.refill-rate=30
```

---

## Database Schema

### ChatLog Table (User Logs)

| Column | Type | Description |
|--------|------|-------------|
| log_id | BIGINT | Unique log ID (Snowflake) |
| user_id | VARCHAR(255) | User identifier |
| memory_id | VARCHAR(255) | Session identifier |
| raw_text | TEXT | Raw user input |
| msg | TEXT | Processed log content |
| intent | VARCHAR(50) | Intent type |
| create_time | DATETIME | Creation time |
| event_date | DATE | Event date |

### Plan Table (Health Plans)

| Column | Type | Description |
|--------|------|-------------|
| plan_id | BIGINT | Plan unique ID |
| user_id | VARCHAR(255) | User identifier |
| plan_type | VARCHAR(50) | Plan type |
| plan_content | TEXT | Plan content (JSON format) |
| plan_status | VARCHAR(50) | Status: DRAFT/ACTIVE/COMPLETED |
| create_time | DATETIME | Creation time |
| update_time | DATETIME | Update time |

---

## Redis Data Structures

| Key Pattern | Data Type | Description |
|-------------|-----------|-------------|
| `chat:memory:{memoryId}` | String | AI chat memory (ChatMemoryStore) |
| `chat:display:{memoryId}` | List | User-visible chat history |
| `chat:task:{taskId}` | String | Async task state and result |
| `chat:task:memory:{memoryId}` | String | Active task ID for session |
| `chat:lock:{memoryId}` | String | Distributed session lock |
| `chat:plan:{memoryId}` | String | Plan flow state |
| `chat:concurrency:global` | String | Global concurrency count |
| `chat:concurrency:intent:{intent}` | String | Function-level concurrency count |
| `chat:ratelimit:global` | Hash | Token bucket rate limit state |

---

## Quick Start

### Environment Requirements

- Java JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.2+
- Milvus 2.6+
- Elasticsearch 8.x

### Installation Steps

1. **Clone the project**

```bash
git clone https://github.com/obr310/demoapp.git
cd demoapp
```

2. **Configure environment variables**

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

3. **Initialize database**

```sql
CREATE DATABASE health_app;
```

4. **Build and run**

```bash
mvn clean install
mvn spring-boot:run
```

5. **Verify service**

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"memoryId": "test123", "msg": "Ran 5km today"}'
```

---

## Development Guide

### Adding New Intent Types

1. Add new type in `Intent.java` enum
2. Implement corresponding Service interface and implementation
3. Add routing logic in `ChatDispatchServiceImp`
4. Add corresponding prompt template (optional)

### Customizing Agents

The project uses LangChain4j's `@AiService` annotation to implement agents. To create a new agent:

1. Define agent interface with `@AiService` annotation
2. Implement tool class with `@Tool` annotation
3. Configure system prompt in `application.yaml`

### Extending Knowledge Base

The MedlinePlus knowledge base is automatically loaded at application startup. To add custom knowledge:

1. Modify `resources/content/MedlinePlus.txt`
2. Or extend `KnowledgeBaseInitializer`
3. Restart application to trigger loading

---

## FAQ

### Q: What if async task times out?

Async tasks have default timeout settings. Adjust via `application.properties`:

```properties
chat.task.timeout-seconds=300
```

### Q: How to check RAG retrieval effectiveness?

Run `RetrievalRelevanceTest` to verify retrieval relevance.

### Q: How to handle rate limit triggers?

System returns 429 status. Frontend should implement exponential backoff retry.

### Q: Does knowledge base update require restart?

Yes, by default. To enable hot update, extend `KnowledgeBaseInitializer` for incremental updates.
