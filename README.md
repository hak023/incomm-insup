# 프로젝트명: AMAS(=Application Modernization intelligent network Application Server)
지능망AS 현대화 상용화 프로젝트

# 코드 표기법
- JAVA Code: Camel, Python: Snake 표기법 

# 맴버
- yongesamo@naver.com
- dantae74@gmail.com
- hak023@nate.com

## 개발 환경 ( sping initializr, https://start.spring.io )
- Gradle(groovy)
- Java Open JDK 21
- Spring Framework 3.5.3
- Packaging JAR
- Dependencies
  - Lombok
  - Spring Web (REST API)
  - Gradle 추가
    - implementation 'io.netty:netty-all:4.2.2.Final' (TCP)
    - implementation 'com.fasterxml.jackson.core:jackson-databind:2.19.2'
    - implementation 'org.json:json:20250517'
- Project setting
  - Group: com.in.amas, Artifact: sipproxy, Package: com.in.amas.sipproxy
  - Group: com.in.amas, Artifact: sipsvc, Package: com.in.amas.sipsvc
  - Group: com.in.amas, Artifact: ingwclient, Package: com.in.amas.ingwclient
  - Group: com.in.amas, Artifact: insupclient, Package: com.in.amas.insupclient

## 대상 모듈
- sipproxy
- sipsvc
- insupclient
- ingwclient

## 실행 환경
### TEST
- host: tbamas02
- ip: 192.1.73.78
- id: amuser / 8!amuser
- home directory: $AMAS_HOME, /home/amuser/amas
- process directory
  - sipproxy: $AMAS_HOME/sipproxy
  - sipsvc: $AMAS_HOME/sipsvc
  - ingwclient: $AMAS_HOME/ingwclient
  - insupclient: $AMAS_HOME/insupclient
- script directory
  - $AMAS_HOME/script
    - amasctl <service_name|all> <start|stop|restart|status|check>

### 서비스 포트
- sipproxy : 서비스 controller port 5060, 15000, 운용관리controller port 15001
- sipsvc : 서비스 controller port 15010, 운용관리controller port 15011
- ingwclient : 서비스 controller port 15020, 운용관리controller port 15021
- insupclient : 서비스 controller port 15030, 운용관리controller port 15031

## 📋 최근 업데이트 (2025-08-26)

### 🚀 insupclient 모듈 주요 개선사항

#### 🌏 한글 로그 메시지 영어화 완료
- **170+ 개의 한글 로그 메시지**를 모두 영어로 변경
- 터미널 인코딩 문제 해결로 로그 가독성 대폭 향상
- 국제화 대응 및 유지보수성 개선

**변경된 파일:**
- `ConnectionManagementService.java` - 연결 관리 로그
- `MessageProcessingService.java` - 메시지 처리 로그  
- `InsupcTcpClient.java` - INSUPC TCP 클라이언트 로그
- `SipsvcTcpServer.java` - sipsvc TCP 서버 로그
- `WorkerThreadPool.java` - 워커 스레드 풀 로그
- `WorkerTask.java` - 워커 작업 로그
- `WorkerQueue.java` - 워커 큐 로그
- `TestSimulator.java` - 테스트 시뮬레이터 로그
- `InsupcProtocolParser.java` - INSUPC 프로토콜 파서 로그
- `SipsvcProtocolParser.java` - sipsvc 프로토콜 파서 로그

#### 🔧 버그 수정
- **NullPointerException 수정**: `ConnectionManagementService.authenticateClient()` 메소드
  - `allowedClients` 리스트 null 체크 추가
  - 인증 설정 누락 시 안전한 처리 로직 구현

#### ⚙️ 설정 개선
- **application.yml 개선**: dev 프로파일 설정 추가
  - TCP 서버 설정 (포트 9090)
  - INSUPC 클라이언트 설정 (포트 19000)
  - 워커 스레드 풀 설정
  - 보안 설정 (허용된 클라이언트 목록)

#### 🎯 주요 개선 효과
- ✅ **터미널 인코딩 문제 해결** - 한글 깨짐 현상 완전 제거
- ✅ **시스템 안정성 향상** - NullPointerException 방지
- ✅ **개발 생산성 향상** - 영어 로그로 디버깅 용이
- ✅ **국제화 대응** - 글로벌 개발팀 협업 지원
- ✅ **유지보수성 개선** - 일관된 영어 로그 메시지

#### 🔄 Git 브랜치 정보
- **Branch**: `insupclient-integration`
- **Commit**: `7e5c1e0`
- **Files Changed**: 23개 파일
- **Lines Added**: 4,443줄

#### 🏃‍♂️ 실행 방법
1. **IDE에서 실행 (권장)**:
   ```
   InsupclientApplication.java → Run
   Program arguments: --spring.profiles.active=dev
   ```

2. **포트 정보**:
   - HTTP 서버: 8080
   - TCP 서버 (sipsvc): 9090
   - INSUPC 연결: 19000

#### 📝 다음 계획
- [ ] Maven/Gradle wrapper 설정 추가
- [ ] Docker 컨테이너 환경 설정
- [ ] 통합 테스트 케이스 추가
- [ ] 성능 모니터링 대시보드 구축