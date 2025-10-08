@echo off
chcp 65001 >nul
echo ========================================
echo Agent Core 启动脚本 (集成Ollama)
echo ========================================

REM 设置JAVA_HOME (修复路径问题)
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
echo JAVA_HOME设置为: %JAVA_HOME%

REM 检查Java是否可用
echo 检查Java环境...
java -version
if %ERRORLEVEL% neq 0 (
    echo 错误: Java未找到或无法运行
    pause
    exit /b 1
)

REM 检查Ollama服务是否运行
echo 检查Ollama服务...
curl -s http://localhost:11434/api/tags >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo 警告: Ollama服务未运行在端口11434
    echo 请确保Ollama正在运行，然后按任意键继续...
    pause
) else (
    echo ✅ Ollama服务正在运行
)

REM 编译项目
echo 编译项目...
mvn clean compile
if %ERRORLEVEL% neq 0 (
    echo 编译失败
    pause
    exit /b 1
)

echo ✅ 编译成功！

REM 启动应用
echo 启动Agent Core应用...
echo 应用将在 http://localhost:8081 上运行
echo 按 Ctrl+C 停止应用
echo.

mvn spring-boot:run

pause
