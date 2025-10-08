@echo off
echo ========================================
echo 快速测试脚本
echo ========================================

echo 1. 检查Java环境...
java -version
if %ERRORLEVEL% neq 0 (
    echo ❌ Java环境检查失败
    pause
    exit /b 1
)

echo.
echo 2. 检查Maven环境...
mvn -version
if %ERRORLEVEL% neq 0 (
    echo ❌ Maven环境检查失败
    pause
    exit /b 1
)

echo.
echo 3. 检查Ollama服务...
curl -s http://localhost:11434/api/tags >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ⚠️  Ollama服务未运行，请先启动Ollama
    echo 启动命令: ollama serve
    pause
    exit /b 1
) else (
    echo ✅ Ollama服务正在运行
)

echo.
echo 4. 编译项目...
mvn clean compile
if %ERRORLEVEL% neq 0 (
    echo ❌ 编译失败
    pause
    exit /b 1
)

echo.
echo ✅ 所有检查通过！现在可以启动应用了
echo 启动命令: mvn spring-boot:run
echo 或者运行: start.bat
echo.
pause
