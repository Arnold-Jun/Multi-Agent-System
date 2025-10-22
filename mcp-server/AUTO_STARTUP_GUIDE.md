# MCP 自动启动指南

## 🎯 功能概述

现在 MCP 代理服务器会在启动时**自动启动所有配置的进程**，无需等待第一次调用！

## 🚀 启动流程

### 1. 启动 MCP 代理服务器

```bash
cd mcp-server
mvn spring-boot:run
```

### 2. 自动启动过程

启动时你会看到类似这样的日志：

```
🚀 启动MCP代理服务器...
✅ MCP服务器启动完成！
🌐 服务地址: http://localhost:18083
📝 配置文件: src/main/resources/mcp.json
📡 可用端点：
  🔧 健康检查:     GET  /mcp/health
  📊 服务器信息:   GET  /mcp/info
  🔌 MCP协议端点:  POST /mcp/jsonrpc
  📋 可用服务器:   GET  /mcp/servers
  📈 会话统计:     GET  /mcp/sessions
  🔄 重新加载配置: POST /mcp/reload
  🐛 进程调试:     GET  /mcp/debug/processes
  🚀 启动状态:     GET  /mcp/startup/status

🔄 开始自动启动配置的MCP进程...
📋 发现 3 个配置的服务器
🚀 启动进程: xiaohongshu-mcp
🚀 启动进程: airbnb-dev
🚀 启动进程: amap-maps-streamableHTTP

🚀 正在启动进程: go 在目录: C:\Users\ZhuanZ1\mcp\xiaohongshu-mcp
✅ MCP进程启动成功: xiaohongshu-mcp (PID: 12345)
✅ 进程启动成功: xiaohongshu-mcp

🚀 正在启动进程: node 在目录: C:\Users\ZhuanZ1\mcp\mcp-server-airbnb
✅ MCP进程启动成功: airbnb-dev (PID: 12346)
✅ 进程启动成功: airbnb-dev

📊 进程启动结果:
  ✅ 运行中 xiaohongshu-mcp - go
  ✅ 运行中 airbnb-dev - node
  📝 没有配置进程模式的服务器
```

## 📊 监控和检查

### 1. 检查启动状态

```bash
curl http://localhost:18083/mcp/startup/status
```

返回示例：
```json
{
  "startupComplete": true,
  "totalProcesses": 2,
  "runningProcesses": 2,
  "failedProcesses": 0,
  "successRate": 100.0,
  "timestamp": 1698000000000,
  "processDetails": {
    "xiaohongshu-mcp": {
      "command": "go",
      "args": ["run", "."],
      "workingDirectory": "C:\\Users\\ZhuanZ1\\mcp\\xiaohongshu-mcp",
      "enabled": true,
      "processStatus": {
        "status": "running",
        "pid": 12345,
        "startTime": 1698000000000,
        "uptime": 5000,
        "alive": true
      },
      "isRunning": true
    },
    "airbnb-dev": {
      "command": "node",
      "args": ["dist/index.js", "--ignore-robots-txt"],
      "workingDirectory": "C:\\Users\\ZhuanZ1\\mcp\\mcp-server-airbnb",
      "enabled": true,
      "processStatus": {
        "status": "running",
        "pid": 12346,
        "startTime": 1698000000000,
        "uptime": 5000,
        "alive": true
      },
      "isRunning": true
    }
  }
}
```

### 2. 检查进程调试信息

```bash
curl http://localhost:18083/mcp/debug/processes
```

### 3. 检查可用服务器

```bash
curl http://localhost:18083/mcp/servers
```

## 🔧 配置说明

### 当前配置 (mcp.json)

```json
{
  "mcpServers": {
    "amap-maps-streamableHTTP": {
      "url": "https://mcp.amap.com/mcp?key=16d2c1ab42a4fa07d82faf602af4bcef",
      "description": "高德地图MCP服务 - 提供地图、导航、地理编码等功能",
      "enabled": true,
      "type": "http"
    },
    "xiaohongshu-mcp": {
      "command": "go",
      "args": ["run", "."],
      "workingDirectory": "C:\\Users\\ZhuanZ1\\mcp\\xiaohongshu-mcp",
      "description": "小红书内容发布服务",
      "enabled": true,
      "type": "process"
    },
    "airbnb-dev": {
      "command": "node",
      "args": ["dist/index.js", "--ignore-robots-txt"],
      "workingDirectory": "C:\\Users\\ZhuanZ1\\mcp\\mcp-server-airbnb",
      "description": "Airbnb开发工具MCP服务",
      "enabled": true,
      "type": "process"
    },
    "jinko-travel": {
      "command": "npx",
      "args": ["jinko-mcp-dev@latest"],
      "description": "Jinko旅行MCP服务",
      "enabled": false,
      "type": "process"
    }
  }
}
```

## 🎯 自动启动的服务器

- ✅ **xiaohongshu-mcp**: Go 进程，自动在指定目录运行 `go run .`
- ✅ **airbnb-dev**: Node.js 进程，自动在指定目录运行 `node dist/index.js --ignore-robots-txt`
- ⏸️ **jinko-travel**: NPX 进程（已禁用）
- 🌐 **amap-maps-streamableHTTP**: HTTP 模式（无需启动进程）

## 🛑 优雅关闭

当应用关闭时，所有进程会被自动停止：

```
🛑 应用正在关闭，停止所有MCP进程...
✅ 所有MCP进程已停止
```

## 🔍 故障排除

### 问题 1: 进程启动失败

**症状**: 日志显示 "进程启动失败"

**检查步骤**:
1. 检查工作目录是否存在
2. 检查命令是否可用
3. 查看详细错误日志

```bash
# 手动测试
cd C:\Users\ZhuanZ1\mcp\xiaohongshu-mcp
go run .

cd C:\Users\ZhuanZ1\mcp\mcp-server-airbnb
node dist/index.js --ignore-robots-txt
```

### 问题 2: 进程启动后立即退出

**症状**: 进程启动成功但立即停止

**可能原因**:
- 项目依赖未安装
- 配置文件错误
- 端口冲突

**解决方法**:
- 检查项目依赖
- 查看进程输出日志
- 检查端口占用

### 问题 3: 启动超时

**症状**: 启动过程很慢或超时

**解决方法**:
- 检查网络连接（对于 NPX 包）
- 检查系统资源
- 调整启动超时时间

## 📈 性能优化

### 1. 并行启动

进程会并行启动，提高启动速度。

### 2. 启动顺序

- HTTP 模式服务器：立即可用
- 进程模式服务器：按配置顺序启动

### 3. 资源管理

- 自动清理死进程
- 内存使用监控
- 进程状态跟踪

## 🎉 优势总结

1. **完全自动化**: 无需手动启动任何进程
2. **智能管理**: 自动启动、监控、清理
3. **统一接口**: 所有服务器通过统一 MCP 接口访问
4. **优雅关闭**: 应用关闭时自动停止所有进程
5. **实时监控**: 随时查看进程状态和启动结果

现在你只需要启动 MCP 代理服务器，所有子进程都会自动运行！🚀
