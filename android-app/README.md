# 健康助手 Android 应用

一个集成了 AI 聊天功能的健康助手 Android 应用。

## 功能特性

### 1. 用户登录
- 用户名和密码登录
- 调用后端 `/auth/login` API
- 登录成功后跳转到 AI 聊天界面

### 2. AI 聊天界面
- 类似 ChatGPT 的对话界面
- 实时调用后端 `/chat` API
- 使用 userId 作为会话标识（memoryId）
- AI 回复显示在左侧（灰色气泡）
- 用户消息显示在右侧（蓝色气泡）
- 自动滚动到最新消息

### 3. 界面设计
- 蓝色和绿色主题
- Material Design 3
- 简洁现代的 UI
- 流畅的动画效果

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **网络**: Retrofit + OkHttp
- **架构**: MVVM
- **主题**: Material Design 3

## 项目结构

```
app/src/main/java/com/example/healthy/
├── MainActivity.kt                 # 主活动
├── data/
│   ├── api/
│   │   ├── AuthApi.kt             # 登录 API 接口
│   │   ├── ChatApi.kt             # 聊天 API 接口
│   │   └── RetrofitClient.kt      # Retrofit 客户端配置
│   └── model/
│       ├── AuthRequest.kt         # 登录请求模型
│       ├── AuthResponse.kt        # 登录响应模型
│       ├── ChatRequest.kt         # 聊天请求模型
│       └── ChatResponse.kt        # 聊天响应模型
└── ui/
    ├── chat/
    │   └── ChatScreen.kt          # AI 聊天界面
    ├── login/
    │   └── LoginScreen.kt         # 登录界面
    └── theme/
        ├── Color.kt               # 颜色定义
        ├── Theme.kt               # 主题配置
        └── Type.kt                # 字体配置
```

## API 接口

### 登录接口
- **路径**: `POST /auth/login`
- **请求体**:
  ```json
  {
    "username": "用户名",
    "password": "密码"
  }
  ```
- **响应**:
  ```json
  {
    "ok": true,
    "message": "登录成功",
    "userId": 270375239215616000,
    "userName": "fx"
  }
  ```

### 聊天接口
- **路径**: `POST /chat`
- **请求体**:
  ```json
  {
    "memoryId": "用户ID",
    "msg": "用户消息"
  }
  ```
- **响应**:
  ```json
  {
    "logId": "日志ID",
    "intent": "AI回复内容",
    "mysqlStored": true,
    "milvusStored": true
  }
  ```

## 配置说明

### 修改服务器地址

打开 `app/src/main/java/com/example/healthy/data/api/RetrofitClient.kt`，修改 `BASE_URL`：

```kotlin
// 当前配置（使用电脑 IP）
private const val BASE_URL = "http://10.29.55.193:8080/"

// Android 模拟器访问本机
private const val BASE_URL = "http://10.0.2.2:8080/"

// 真机访问（使用电脑局域网 IP）
private const val BASE_URL = "http://你的电脑IP:8080/"
```

## 使用流程

1. **启动应用** → 显示登录界面
2. **输入用户名和密码** → 点击登录
3. **登录成功** → 自动跳转到 AI 聊天界面
4. **输入消息** → 点击发送按钮
5. **等待 AI 回复** → 显示在聊天界面
6. **点击右上角用户图标** → 退出登录

## 运行要求

- Android SDK 24+
- Kotlin 1.9+
- 后端服务需要运行在正确的端口

## 调试日志

应用已集成详细的日志输出，可以在 Logcat 中查看：

### 筛选标签：
- `RetrofitClient` - 查看所有网络请求和响应
- `LoginScreen` - 查看登录流程
- `ChatScreen` - 查看聊天流程

### 示例日志：
```
D/LoginScreen: 开始登录请求: username=fx
D/RetrofitClient: --> POST http://10.29.55.193:8080/auth/login
D/RetrofitClient: {"password":"030809","username":"fx"}
D/RetrofitClient: <-- 200 http://10.29.55.193:8080/auth/login
D/RetrofitClient: {"ok":true,"message":"登录成功",...}
D/LoginScreen: 登录响应: ok=true, userId=270375239215616000

D/ChatScreen: 发送消息: userId=270375239215616000, msg=你好
D/RetrofitClient: --> POST http://10.29.55.193:8080/chat
D/RetrofitClient: {"memoryId":"270375239215616000","msg":"你好"}
D/RetrofitClient: <-- 200 http://10.29.55.193:8080/chat
D/ChatScreen: AI回复: intent=您好！有什么可以帮助您的吗？
```

## 常见问题

### Q: 显示 404 错误？
**A**: 检查 `BASE_URL` 是否正确，确认后端服务是否在该端口运行。

### Q: 无法连接到服务器？
**A**: 
- 模拟器：使用 `10.0.2.2`
- 真机：使用电脑的局域网 IP
- 确认防火墙没有阻止连接

### Q: 登录成功但聊天没有回复？
**A**: 查看 Logcat 日志，检查 `/chat` API 的响应内容。

## 开发者

- UI 设计：Material Design 3 + 蓝绿主题
- API 集成：Retrofit + Kotlin Coroutines
- 状态管理：Compose State

