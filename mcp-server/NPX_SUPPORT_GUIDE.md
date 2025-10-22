# NPX命令支持指南

## 概述

MCP服务器现在完全支持 `npx` 命令，可以直接运行npm包作为MCP服务器，无需全局安装。

## NPX命令优势

1. **无需全局安装**：直接运行npm包，无需 `npm install -g`
2. **版本管理**：可以指定特定版本或使用 `@latest`
3. **环境隔离**：每个包在独立环境中运行
4. **自动下载**：首次运行时自动下载包

## 配置格式

### 基本NPX配置

```json
{
  "mcpServers": {
    "your-npx-server": {
      "command": "npx",
      "args": ["package-name@version"],
      "description": "服务器描述",
      "enabled": true,
      "type": "process"
    }
  }
}
```

### 配置示例

#### 1. 使用最新版本
```json
{
  "jinko-travel": {
    "command": "npx",
    "args": ["jinko-mcp-dev@latest"],
    "description": "Jinko旅行MCP服务",
    "enabled": true,
    "type": "process"
  }
}
```

#### 2. 使用特定版本
```json
{
  "data-analysis": {
    "command": "npx",
    "args": ["data-analysis-mcp@1.2.3"],
    "description": "数据分析MCP服务",
    "enabled": true,
    "type": "process"
  }
}
```

#### 3. 使用本地包
```json
{
  "local-tools": {
    "command": "npx",
    "args": ["./local-mcp-server"],
    "description": "本地工具MCP服务",
    "enabled": true,
    "type": "process"
  }
}
```

## 环境配置

### 自动环境设置

MCP服务器会自动为 `npx` 命令设置以下环境变量：

- `NODE_ENV=production` - 生产环境
- `NPM_CONFIG_LOGLEVEL=error` - 减少日志输出
- `NPM_CONFIG_PROGRESS=false` - 禁用进度条
- `NPM_CONFIG_AUDIT=false` - 禁用安全审计
- `NPM_CONFIG_CACHE` - 设置npm缓存目录

### 路径配置

- 自动将npm全局目录添加到PATH
- 确保npx命令可用
- 支持Windows和Unix系统

## 支持的NPX包类型

### 1. 官方MCP包
```json
{
  "official-mcp": {
    "command": "npx",
    "args": ["@modelcontextprotocol/server-filesystem"],
    "description": "文件系统MCP服务器",
    "enabled": true,
    "type": "process"
  }
}
```

### 2. 第三方MCP包
```json
{
  "third-party": {
    "command": "npx",
    "args": ["some-mcp-package@latest"],
    "description": "第三方MCP包",
    "enabled": true,
    "type": "process"
  }
}
```

### 3. 本地开发包
```json
{
  "dev-package": {
    "command": "npx",
    "args": ["../my-mcp-server"],
    "description": "本地开发MCP服务器",
    "enabled": true,
    "type": "process"
  }
}
```

## 故障排除

### 常见问题

#### 1. NPX命令不可用
**症状**：日志显示 "命令可能不可用: npx"
**解决**：
- 确保Node.js已安装
- 检查PATH环境变量
- 尝试运行 `npx --version`

#### 2. 包下载失败
**症状**：进程启动失败，网络错误
**解决**：
- 检查网络连接
- 清除npm缓存：`npm cache clean --force`
- 尝试使用不同的npm镜像

#### 3. 权限问题
**症状**：无法写入npm缓存目录
**解决**：
- 检查目录权限
- 以管理员身份运行
- 修改npm缓存目录权限

### 调试技巧

#### 1. 启用详细日志
```bash
# 设置环境变量
export NPM_CONFIG_LOGLEVEL=verbose
export NODE_ENV=development
```

#### 2. 手动测试NPX包
```bash
# 直接运行测试
npx jinko-mcp-dev@latest

# 检查包信息
npm info jinko-mcp-dev
```

#### 3. 清理缓存
```bash
# 清理npm缓存
npm cache clean --force

# 清理npx缓存
npx clear-npx-cache
```

## 性能优化

### 1. 缓存策略
- NPX会自动缓存下载的包
- 首次运行较慢，后续运行更快
- 可以预下载常用包

### 2. 网络优化
```json
{
  "optimized-server": {
    "command": "npx",
    "args": ["--prefer-offline", "package-name"],
    "description": "离线优先的MCP服务器",
    "enabled": true,
    "type": "process"
  }
}
```

### 3. 资源管理
- 进程自动清理
- 内存使用监控
- 超时处理

## 最佳实践

### 1. 版本管理
- 生产环境使用固定版本
- 开发环境可以使用 `@latest`
- 定期更新依赖

### 2. 安全考虑
- 验证包来源
- 使用可信的npm源
- 定期安全审计

### 3. 监控和日志
- 启用详细日志记录
- 监控进程状态
- 设置告警机制

## 示例配置

### 完整的NPX配置示例

```json
{
  "mcpServers": {
    "jinko-travel": {
      "command": "npx",
      "args": ["jinko-mcp-dev@latest"],
      "description": "Jinko旅行MCP服务",
      "enabled": true,
      "type": "process"
    },
    "data-analysis": {
      "command": "npx",
      "args": ["data-analysis-mcp@1.2.3"],
      "description": "数据分析MCP服务",
      "enabled": true,
      "type": "process"
    },
    "file-system": {
      "command": "npx",
      "args": ["@modelcontextprotocol/server-filesystem"],
      "description": "文件系统MCP服务器",
      "enabled": true,
      "type": "process"
    }
  }
}
```

现在您可以轻松使用任何npm包作为MCP服务器，无需全局安装！🎉
