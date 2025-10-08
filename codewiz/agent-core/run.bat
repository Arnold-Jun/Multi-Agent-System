@echo off
echo ========================================
echo Agent Core 启动脚本
echo ========================================

REM 设置JAVA_HOME
set JAVA_HOME="C:\Program Files\Java\jdk-21"
echo JAVA_HOME设置为: %JAVA_HOME%

REM 检查Java是否可用
echo 检查Java环境...
java -version
if %ERRORLEVEL% neq 0 (
    echo 错误: Java未找到或无法运行
    pause
    exit /b 1
)

REM 检查Maven是否可用
echo 检查Maven环境...
mvn -version
if %ERRORLEVEL% neq 0 (
    echo 警告: Maven未找到，尝试使用Maven Wrapper...
    
    REM 尝试使用Maven Wrapper
    if exist "mvnw.cmd" (
        echo 使用Maven Wrapper...
        mvnw.cmd clean compile
        if %ERRORLEVEL% neq 0 (
            echo 编译失败
            pause
            exit /b 1
        )
        
        echo 启动应用...
        mvnw.cmd spring-boot:run
    ) else (
        echo 错误: 未找到Maven或Maven Wrapper
        echo 请安装Maven或确保mvnw.cmd文件存在
        pause
        exit /b 1
    )
) else (
    echo 使用系统Maven...
    mvn clean compile
    if %ERRORLEVEL% neq 0 (
        echo 编译失败
        pause
        exit /b 1
    )
    
    echo 启动应用...
    mvn spring-boot:run
)

pause


