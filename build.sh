#!/bin/bash

# Insup Application 빌드 스크립트
# Maven을 사용하여 애플리케이션을 빌드하는 스크립트

set -e

echo "========================================"
echo "Insup Application 빌드 스크립트"
echo "========================================"

# Java 버전 확인
if ! command -v java &> /dev/null; then
    echo "❌ Java가 설치되지 않았습니다."
    echo "   Java 17 이상을 설치하세요."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "?(1\.)?\K[0-9]+')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17 이상이 필요합니다. 현재 버전: $JAVA_VERSION"
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

# 이전 빌드 결과 정리
echo ""
echo "🧹 이전 빌드 결과를 정리합니다..."
mvn clean

# 의존성 다운로드
echo ""
echo "📦 의존성을 다운로드합니다..."
mvn dependency:resolve

# 컴파일 및 테스트
echo ""
echo "🔨 애플리케이션을 컴파일합니다..."
mvn compile

echo ""
echo "🧪 테스트를 실행합니다..."
mvn test

# 패키징
echo ""
echo "📦 JAR 파일을 생성합니다..."
mvn package -DskipTests

# 빌드 결과 확인
JAR_FILE=$(find target -name "insup-application-*.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR 파일 생성에 실패했습니다."
    exit 1
fi

echo ""
echo "✅ 빌드가 성공적으로 완료되었습니다!"
echo "   생성된 JAR: $JAR_FILE"
echo "   파일 크기: $(du -h $JAR_FILE | cut -f1)"

# JAR 파일 정보 출력
echo ""
echo "📋 JAR 파일 정보:"
java -jar $JAR_FILE --version 2>/dev/null || echo "   메인 클래스: com.incomm.insup.InsupApplication"

echo ""
echo "🚀 애플리케이션을 시작하려면:"
echo "   ./start.sh"
echo ""
echo "🐳 Docker로 실행하려면:"
echo "   docker-compose up -d"