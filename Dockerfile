# OpenJDK 17을 기반으로 하는 경량 Linux 이미지
FROM openjdk:17-jdk-slim

# 메타데이터 설정
LABEL maintainer="InComm"
LABEL description="Insup Application for Linux Environment"
LABEL version="1.0.0"

# 필요한 패키지 설치
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    procps \
    && rm -rf /var/lib/apt/lists/*

# 애플리케이션 디렉토리 생성
WORKDIR /app

# 로그 디렉토리 생성
RUN mkdir -p /app/logs

# JAR 파일 복사
COPY target/insup-application-*.jar /app/app.jar

# 애플리케이션 설정 파일 복사 (필요시)
COPY src/main/resources/application.yml /app/application.yml

# 포트 노출
EXPOSE 8080

# 환경 변수 설정
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Djava.awt.headless=true -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]