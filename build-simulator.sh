#!/bin/bash

# Insupclient Test Simulator 빌드 스크립트
# 별도의 실행 가능한 시뮬레이터 JAR 파일을 생성

set -e

echo "========================================"
echo "Insupclient Test Simulator 빌드 스크립트"
echo "========================================"

# Java 버전 확인
if ! command -v java &> /dev/null; then
    echo "❌ Java가 설치되지 않았습니다."
    echo "   Java 21 이상을 설치하세요."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "?(1\.)?\K[0-9]+')
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Java 21 이상이 필요합니다. 현재 버전: $JAVA_VERSION"
    exit 1
fi

echo "✅ Java 버전: $(java -version 2>&1 | head -n 1)"

# Maven 확인
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven이 설치되지 않았습니다."
    echo "   Maven을 설치하거나 Maven Wrapper를 사용하세요."
    exit 1
fi

echo "✅ Maven 버전: $(mvn -version | head -n 1)"

echo ""
echo "🔨 시뮬레이터 빌드 시작..."

# 시뮬레이터 전용 POM으로 빌드
mvn clean package -f simulator-pom.xml -DskipTests

# 빌드 결과 확인
SIMULATOR_JAR="target/insupclient-simulator.jar"

if [ -f "$SIMULATOR_JAR" ]; then
    echo ""
    echo "✅ 시뮬레이터 빌드 성공!"
    echo "   생성된 JAR: $SIMULATOR_JAR"
    echo "   파일 크기: $(du -h $SIMULATOR_JAR | cut -f1)"
    echo ""
    echo "🚀 시뮬레이터 실행 방법:"
    echo "   ./run-simulator.sh"
    echo ""
    echo "🐳 또는 직접 실행:"
    echo "   java -jar $SIMULATOR_JAR"
else
    echo "❌ 시뮬레이터 JAR 파일 생성 실패"
    exit 1
fi