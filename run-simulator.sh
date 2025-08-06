#!/bin/bash

# Test Simulator 실행 스크립트
# Gateway 서버의 전체 플로우를 테스트하는 시뮬레이터

set -e

echo "========================================"
echo "Incomm-Insup Gateway 테스트 시뮬레이터"
echo "========================================"

# 환경 변수 설정
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-test}

# JVM 옵션 설정
JAVA_OPTS="-Xms256m -Xmx512m"
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE"

# 시뮬레이터 JAR 파일 확인
SIMULATOR_JAR="target/test-simulator.jar"

if [ ! -f "$SIMULATOR_JAR" ]; then
    echo "❌ 시뮬레이터 JAR 파일을 찾을 수 없습니다: $SIMULATOR_JAR"
    echo "   먼저 시뮬레이터를 빌드하세요:"
    echo "   mvn clean package -f simulator-pom.xml"
    exit 1
fi

echo "📦 시뮬레이터 JAR: $SIMULATOR_JAR"
echo "🚀 Java 옵션: $JAVA_OPTS"
echo ""

# Gateway 서버 상태 확인
echo "🔍 Gateway 서버 상태 확인 중..."
if curl -s http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
    echo "✅ Gateway 서버가 실행 중입니다"
else
    echo "⚠️  Gateway 서버에 연결할 수 없습니다"
    echo "   Gateway 서버가 실행 중인지 확인하세요:"
    echo "   ./start.sh"
    echo ""
    echo "   테스트를 계속 진행하시겠습니까? (y/N)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "테스트를 중단합니다."
        exit 1
    fi
fi

echo ""
echo "🎯 테스트 시뮬레이터를 시작합니다..."
echo "   - INSUPC 서버 시뮬레이터: localhost:19000"
echo "   - sipsvc 클라이언트 테스트: localhost:9090 → Gateway"
echo ""

# 시뮬레이터 실행
java $JAVA_OPTS -jar $SIMULATOR_JAR

echo ""
echo "✅ 테스트 시뮬레이터 실행 완료"
echo ""
echo "📋 테스트 결과:"
echo "   1. INSUPC 서버 시뮬레이터가 Gateway 연결을 수락했는지 확인"
echo "   2. sipsvc 클라이언트가 인증, heartbeat, execute 메시지를 전송했는지 확인"  
echo "   3. Gateway가 INSUPC에 질의하고 응답을 sipsvc에 반환했는지 확인"
echo ""
echo "🔍 Gateway 로그 확인:"
echo "   tail -f logs/incomm-insup.log"