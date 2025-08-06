#!/bin/bash

# Insup Application 정지 스크립트
# 리눅스 환경에서 Spring Boot 애플리케이션을 정지하는 스크립트

set -e

echo "========================================"
echo "Insup Application 정지 스크립트"
echo "========================================"

# PID 파일 경로
PID_FILE="./insup-app.pid"

# PID 파일 존재 확인
if [ ! -f "$PID_FILE" ]; then
    echo "⚠️  PID 파일을 찾을 수 없습니다."
    echo "   애플리케이션이 실행되지 않았거나 이미 정지되었습니다."
    exit 0
fi

# PID 읽기
PID=$(cat $PID_FILE)

# 프로세스 존재 확인
if ! ps -p $PID > /dev/null 2>&1; then
    echo "⚠️  PID $PID 프로세스를 찾을 수 없습니다."
    echo "   애플리케이션이 이미 정지되었습니다."
    rm -f $PID_FILE
    exit 0
fi

echo "🛑 애플리케이션을 정지합니다... (PID: $PID)"

# SIGTERM 신호 전송 (우아한 종료)
kill -TERM $PID

# 최대 30초 대기
WAIT_TIME=0
MAX_WAIT=30

while [ $WAIT_TIME -lt $MAX_WAIT ]; do
    if ! ps -p $PID > /dev/null 2>&1; then
        echo "✅ 애플리케이션이 성공적으로 정지되었습니다."
        rm -f $PID_FILE
        exit 0
    fi
    
    sleep 1
    WAIT_TIME=$((WAIT_TIME + 1))
    
    if [ $((WAIT_TIME % 5)) -eq 0 ]; then
        echo "⏳ 정지 대기 중... ($WAIT_TIME/$MAX_WAIT 초)"
    fi
done

# 강제 종료
echo "⚠️  정상 종료에 실패했습니다. 강제 종료를 시도합니다..."
kill -KILL $PID

# 다시 확인
sleep 2
if ! ps -p $PID > /dev/null 2>&1; then
    echo "✅ 애플리케이션이 강제로 정지되었습니다."
    rm -f $PID_FILE
else
    echo "❌ 애플리케이션 정지에 실패했습니다."
    echo "   수동으로 프로세스를 확인하세요: ps -p $PID"
    exit 1
fi