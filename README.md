# DemoApp - Health Assistant RAG Application

An intelligent health assistant application based on LangChain4j and Milvus, supporting personalized health log recording and intelligent question answering.

## Features

- Health log recording (diet, sleep, exercise, mood)
- Semantic retrieval based on a vector database
- Summarization of health trends over a period of time
- Intelligent Q&A and personalized recommendations

## Tech Stack

- **Framework**: Spring Boot 3.3.6
- **LLM**: Alibaba Cloud Qwen
- **Vector Database**: Milvus
- **Database**: MySQL
- **Cache**: Redis
- **AI Framework**: LangChain4j

## Environment Requirements

- Java JDK 21
- Spring Boot 3.3.6
- LangChain4j 1.0.1-beta6
- MySQL 8.0.41 (structured logs and history)
- Redis 7.2.1 (session context and session IDs)
- Milvus 2.6.7 (knowledge_base_vectors / chat_log_vectors)

### 1. Clone the Project

```bash
git clone https://github.com/obr310/demoapp.git
cd demoapp
