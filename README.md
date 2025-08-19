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
