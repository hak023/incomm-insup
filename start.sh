#!/bin/bash

# Insupclient Application 시작 스크립트
# 리눅스 환경에서 Spring Boot 애플리케이션을 시작하는 스크립트

set -e

echo "========================================"
echo "Insupclient Application 시작 스크립트"
echo "========================================"

# 환경 변수 설정
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
export SERVER_PORT=${SERVER_PORT:-8080}

# JVM 옵션 설정
JAVA_OPTS="-Xms512m -Xmx1024m"
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE"
JAVA_OPTS="$JAVA_OPTS -Dserver.port=$SERVER_PORT"

# 로그 디렉토리 생성
mkdir -p logs

# JAR 파일 찾기
JAR_FILE=$(find target -name "insupclient-*.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR 파일을 찾을 수 없습니다. Maven 빌드를 먼저 실행하세요."
    echo "   mvn clean package"
    exit 1
fi

echo "📦 JAR 파일: $JAR_FILE"
echo "🚀 Java 옵션: $JAVA_OPTS"
echo "🌍 프로파일: $SPRING_PROFILES_ACTIVE"
echo "🔗 포트: $SERVER_PORT"
echo ""

# PID 파일 경로
PID_FILE="./insupclient-app.pid"

# 이미 실행 중인 프로세스 확인
if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null 2>&1; then
        echo "⚠️  애플리케이션이 이미 실행 중입니다 (PID: $PID)"
        echo "   정지하려면 stop.sh를 실행하세요."
        exit 1
    else
        rm -f $PID_FILE
    fi
fi

# 애플리케이션 시작
echo "🎯 애플리케이션을 시작합니다..."
nohup java $JAVA_OPTS -jar $JAR_FILE > logs/application.out 2>&1 &

# PID 저장
echo $! > $PID_FILE

echo "✅ 애플리케이션이 시작되었습니다!"
echo "   PID: $(cat $PID_FILE)"
echo "   로그: logs/application.out"
echo "   Health Check: http://localhost:$SERVER_PORT/api/health"
echo ""
echo "🔍 로그 모니터링: tail -f logs/application.out"
echo "🛑 애플리케이션 정지: ./stop.sh"

# 잠시 대기 후 상태 확인
sleep 5
if ps -p $(cat $PID_FILE) > /dev/null 2>&1; then
    echo "🎉 애플리케이션이 성공적으로 시작되었습니다!"
else
    echo "❌ 애플리케이션 시작에 실패했습니다. 로그를 확인하세요."
    echo "   tail logs/application.out"
    exit 1
fi