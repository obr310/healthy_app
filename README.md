# DemoApp - 健康助手RAG应用

基于LangChain4j和Milvus的智能健康助手应用,支持个性化健康日志记录和智能问答。

## 功能特性

- 📝 健康日志记录(饮食、睡眠、运动、情绪)
- 🔍 基于向量数据库的语义检索
- 🤖 智能问答和个性化建议
- 📊 RAG系统性能测试套件

## 技术栈

- **框架**: Spring Boot 3.3.6
- **LLM**: 阿里云通义千问(Qwen)
- **向量数据库**: Milvus
- **数据库**: MySQL
- **缓存**: Redis
- **AI框架**: LangChain4j

## 环境要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.3+

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/your-username/demoapp.git
cd demoapp
```

### 2. 配置环境变量

在系统环境变量中配置API密钥:

**macOS/Linux:**
```bash
# 编辑 ~/.bash_profile 或 ~/.zshrc
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

**Windows:**
```cmd
setx DASHSCOPE_API_KEY "your-dashscope-api-key"
```

### 3. 配置数据库

修改 `application.yaml` 中的数据库连接信息。

### 4. 启动依赖服务

确保MySQL、Redis、Milvus都已启动。

### 5. 运行应用

```bash
mvn spring-boot:run
```

## 配置说明

主要配置文件: `src/main/resources/application.yaml`

### 必需的环境变量

| 变量名 | 说明 |
|--------|------|
| `DASHSCOPE_API_KEY` | 阿里云DashScope API密钥 |

## RAG测试套件

项目包含完整的RAG系统测试:

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=RetrievalRelevanceTest
```

## 许可证

MIT License
