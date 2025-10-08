# Agent Core - 本地学习版本 (集成Ollama)

这是一个基于Spring Boot的智能体核心系统，集成本地Ollama大模型服务，用于学习和理解多Agent协作的工作流架构。

## 项目特点

- 🚀 基于Spring Boot 3.2.12
- 🧠 集成LangGraph4j工作流引擎
- 🤖 支持多Agent协作
- 🛠️ 集成LangChain4j工具管理
- 📡 支持WebSocket实时通信
- 🔧 模块化设计，易于扩展
- 🎯 **集成本地Ollama服务，支持离线AI对话**

## 技术栈

- **后端框架**: Spring Boot 3.2.12
- **工作流引擎**: LangGraph4j 1.5.8
- **LLM集成**: LangChain4j 1.0.0-beta2
- **本地AI服务**: Ollama (支持deepseek等模型)
- **通信协议**: WebSocket + HTTP
- **模板引擎**: FreeMarker 2.3.31
- **序列化**: Jackson 2.17.2 + FastJSON
- **Java版本**: JDK 21

## 环境要求

### 必需环境
- JDK 21+
- Maven 3.6+
- Ollama (本地运行)

### Ollama安装和配置
1. **安装Ollama**
   - 访问 https://ollama.ai/ 下载安装
   - 或使用包管理器安装

2. **下载deepseek模型**
   ```bash
   ollama pull deepseek
   ```

3. **启动Ollama服务**
   ```bash
   ollama serve
   ```
   默认端口: 11434

4. **验证安装**
   ```bash
   curl http://localhost:11434/api/tags
   ```

## 快速开始

### 1. 启动Ollama服务
```bash
# 启动Ollama服务
ollama serve

# 在另一个终端验证服务
curl http://localhost:11434/api/tags
```

### 2. 设置JAVA_HOME
```bash
# Windows CMD
set JAVA_HOME="C:\Program Files\Java\jdk-21"

# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

### 3. 运行应用

#### 方法1: 使用启动脚本 (推荐)
```bash
# Windows CMD
start.bat

# Windows PowerShell
.\start.ps1
```

#### 方法2: 手动运行
```bash
# 编译项目
mvn clean compile

# 启动应用
mvn spring-boot:run
```

### 4. 访问应用
- 健康检查: http://localhost:8081/agent/health
- 聊天API: http://localhost:8081/agent/chat
- Ollama状态: http://localhost:8081/agent/ollama/status
- 可用模型: http://localhost:8081/agent/ollama/models

## 测试API

### 发送聊天消息
```bash
curl -X POST http://localhost:8081/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "chat": "你好，请帮我分析一下这个需求",
    "sessionId": "test-session-001"
  }'
```

### 检查Ollama状态
```bash
curl http://localhost:8081/agent/ollama/status
```

### 获取可用模型
```bash
curl http://localhost:8081/agent/ollama/models
```

### 查看会话状态
```bash
curl "http://localhost:8081/agent/testState?requestId=test-session-001"
```

## 项目结构

```
src/main/java/com/example/agentcore/
├── agent/                    # Agent核心模块
│   ├── BaseAgent.java       # 基础Agent类
│   ├── SupervisorAgent.java # 智能分发主管
│   ├── controller/          # 控制器
│   │   └── AgentController.java
│   ├── core/               # 核心逻辑
│   │   └── AgentControllerCore.java
│   └── state/              # 状态管理
│       └── AgentMessageState.java
├── common/                  # 公共工具
│   └── PromptTemplateReader.java
├── mcp/                    # 工具管理
│   └── ToolProviderManager.java
├── session/                # 会话管理
│   └── SessionManager.java
├── scene/                  # 场景配置
│   ├── SceneConfig.java
│   └── SceneAgentConfig.java
├── discovery/              # 服务发现
│   └── ServiceDiscovery.java
├── a2a/                   # Agent间通信
│   └── A2aClientManager.java
├── auth/                   # 认证授权
│   └── AuthManager.java
├── studio/                 # LangGraph Studio集成
│   └── LangGraphStudioConfig.java
├── config/                 # 配置类
│   └── OllamaConfig.java
├── service/                # 服务类
│   └── OllamaService.java
└── Application.java        # 主应用程序
```

## 核心模块说明

### 1. Agent模块 (`agent/`)
- **BaseAgent**: 所有Agent的基础类，提供LLM交互能力
- **SupervisorAgent**: 智能分发主管，负责任务路由和Agent选择
- **AgentController**: REST API控制器，处理HTTP请求
- **AgentControllerCore**: 核心业务逻辑，管理工作流执行和Ollama集成
- **AgentMessageState**: 状态管理，维护执行上下文

### 2. Ollama集成 (`config/` + `service/`)
- **OllamaConfig**: Ollama服务配置管理
- **OllamaService**: 与本地Ollama服务的通信服务
- 支持聊天、模型查询、服务状态检查等功能

### 3. 工具管理 (`mcp/`)
- **ToolProviderManager**: 统一管理MCP和本地工具
- 支持工具的动态加载和执行
- 工具执行结果的拦截和处理

### 4. 会话管理 (`session/`)
- **SessionManager**: 管理用户会话和WebSocket连接
- 支持实时消息推送
- 会话状态持久化

### 5. 场景配置 (`scene/`)
- **SceneConfig**: 场景配置管理
- **SceneAgentConfig**: 场景内Agent配置
- 支持多场景切换和配置

### 6. 服务发现 (`discovery/`)
- **ServiceDiscovery**: 服务注册和发现
- 支持动态服务管理
- 负载均衡支持

### 7. A2A通信 (`a2a/`)
- **A2aClientManager**: Agent间通信管理
- 支持Agent注册和发现
- 通信协议标准化

### 8. 认证授权 (`auth/`)
- **AuthManager**: 用户认证和token管理
- 支持多种认证方式
- 安全访问控制

### 9. Studio集成 (`studio/`)
- **LangGraphStudioConfig**: LangGraph Studio配置
- 支持可视化工作流编辑
- 开发调试工具

## 配置说明

### Ollama配置
```yaml
ollama:
  base-url: http://localhost:11434  # Ollama服务地址
  model: deepseek                   # 使用的模型名称
  timeout: 30000                    # 请求超时时间(毫秒)
  temperature: 0.7                  # 生成温度
  max-tokens: 4096                  # 最大生成token数
```

### 自定义配置
您可以在 `application.yml` 中修改这些配置来适应您的需求。

## 故障排除

### 1. Ollama服务不可用
```bash
# 检查Ollama是否运行
curl http://localhost:11434/api/tags

# 启动Ollama服务
ollama serve
```

### 2. 模型未找到
```bash
# 查看可用模型
ollama list

# 下载deepseek模型
ollama pull deepseek
```

### 3. 端口冲突
如果8081端口被占用，可以在 `application.yml` 中修改：
```yaml
server:
  port: 8082  # 改为其他可用端口
```

## 学习要点

1. **LangGraph4j工作流**: 理解状态图和工作流编排
2. **多Agent协作**: 学习Agent间的通信和协作模式
3. **状态管理**: 掌握复杂状态的管理和持久化
4. **工具集成**: 了解如何集成和管理各种工具
5. **实时通信**: 学习WebSocket在Agent系统中的应用
6. **模块化设计**: 理解大型系统的模块化架构
7. **配置管理**: 学习场景配置和动态配置
8. **本地AI集成**: 学习如何集成本地大模型服务

## 扩展建议

1. **添加更多Agent**: 实现特定领域的专业Agent
2. **集成其他模型**: 尝试不同的Ollama模型
3. **增加工具**: 添加文件处理、数据库操作等工具
4. **优化工作流**: 根据实际需求优化工作流设计
5. **添加监控**: 集成日志、指标监控等
6. **安全增强**: 添加更完善的认证和授权机制
7. **性能优化**: 添加缓存、连接池等性能优化
8. **模型管理**: 实现模型的动态切换和配置

## 注意事项

- 这是一个学习版本，移除了企业级功能
- 部分功能使用模拟实现，仅用于理解架构
- 生产环境使用需要添加安全、监控等功能
- 所有内部依赖都已替换为开源替代方案
- **需要本地运行Ollama服务**
- **支持离线AI对话，保护隐私**

## 贡献

欢迎提交Issue和Pull Request来改进这个项目！

## 许可证

本项目仅用于学习和研究目的。
