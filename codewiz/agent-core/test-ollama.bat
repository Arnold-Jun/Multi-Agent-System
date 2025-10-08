@echo off
echo ========================================
echo 测试Ollama集成
echo ========================================

echo 1. 检查Ollama服务状态...
curl -s http://localhost:11434/api/tags
if %ERRORLEVEL% neq 0 (
    echo ❌ Ollama服务未运行
    echo 请先启动Ollama服务
    pause
    exit /b 1
)

echo.
echo 2. 测试聊天功能...
curl -X POST http://localhost:8081/agent/chat ^
  -H "Content-Type: application/json" ^
  -d "{\"chat\": \"你好，请介绍一下你自己\", \"sessionId\": \"test-001\"}"

echo.
echo 3. 检查Ollama状态端点...
curl http://localhost:8081/agent/ollama/status

echo.
echo 4. 获取可用模型...
curl http://localhost:8081/agent/ollama/models

echo.
echo 测试完成！
pause
