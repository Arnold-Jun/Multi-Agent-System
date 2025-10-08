#!/bin/bash

# Boss直聘Mock API启动脚本

echo "🚀 启动Boss直聘Mock API服务..."

# 检查Java版本
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -lt 21 ]; then
    echo "❌ 错误: 需要Java 21或更高版本，当前版本: $java_version"
    exit 1
fi

echo "✅ Java版本检查通过: $java_version"

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到Maven，请先安装Maven"
    exit 1
fi

echo "✅ Maven检查通过"

# 编译项目
echo "🔨 编译项目..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"

# 启动应用
echo "🚀 启动应用..."
mvn spring-boot:run

echo "✅ 应用启动完成！"
echo "🌐 服务地址: http://localhost:8084/api"
echo "📚 API文档: http://localhost:8084/api/swagger-ui.html"
echo "🗄️ 数据库控制台: http://localhost:8084/api/h2-console"
echo "📊 健康检查: http://localhost:8084/api/actuator/health"

