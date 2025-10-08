@echo off
chcp 65001 >nul

echo 🚀 启动Boss直聘Mock API服务...

REM 检查Java版本
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到Java，请先安装Java 21或更高版本
    pause
    exit /b 1
)

echo ✅ Java检查通过

REM 检查Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到Maven，请先安装Maven
    pause
    exit /b 1
)

echo ✅ Maven检查通过

REM 编译项目
echo 🔨 编译项目...
mvn clean compile

if %errorlevel% neq 0 (
    echo ❌ 编译失败
    pause
    exit /b 1
)

echo ✅ 编译成功

REM 启动应用
echo 🚀 启动应用...
mvn spring-boot:run

echo ✅ 应用启动完成！
echo 🌐 服务地址: http://localhost:8084/api
echo 📚 API文档: http://localhost:8084/api/swagger-ui.html
echo 🗄️ 数据库控制台: http://localhost:8084/api/h2-console
echo 📊 健康检查: http://localhost:8084/api/actuator/health

pause

