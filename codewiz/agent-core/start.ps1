# Agent Core 启动脚本 (PowerShell版本)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================" -ForegroundColor Green
Write-Host "Agent Core 启动脚本 (集成Ollama)" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# 设置JAVA_HOME (修复路径问题)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
Write-Host "JAVA_HOME设置为: $env:JAVA_HOME" -ForegroundColor Yellow

# 检查Java是否可用
Write-Host "检查Java环境..." -ForegroundColor Cyan
try {
    $javaVersion = java -version 2>&1
    Write-Host "✅ Java环境正常" -ForegroundColor Green
} catch {
    Write-Host "❌ 错误: Java未找到或无法运行" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

# 检查Ollama服务是否运行
Write-Host "检查Ollama服务..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -TimeoutSec 5 -ErrorAction Stop
    Write-Host "✅ Ollama服务正在运行" -ForegroundColor Green
} catch {
    Write-Host "⚠️  警告: Ollama服务未运行在端口11434" -ForegroundColor Yellow
    Write-Host "请确保Ollama正在运行，然后按回车键继续..." -ForegroundColor Yellow
    Read-Host
}

# 编译项目
Write-Host "编译项目..." -ForegroundColor Cyan
try {
    mvn clean compile
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ 编译成功！" -ForegroundColor Green
    } else {
        Write-Host "❌ 编译失败" -ForegroundColor Red
        Read-Host "按回车键退出"
        exit 1
    }
} catch {
    Write-Host "❌ 编译失败: $_" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

# 启动应用
Write-Host "启动Agent Core应用..." -ForegroundColor Cyan
Write-Host "应用将在 http://localhost:8081 上运行" -ForegroundColor Yellow
Write-Host "按 Ctrl+C 停止应用" -ForegroundColor Yellow
Write-Host ""

try {
    mvn spring-boot:run
} catch {
    Write-Host "应用启动失败: $_" -ForegroundColor Red
}

Read-Host "按回车键退出"
