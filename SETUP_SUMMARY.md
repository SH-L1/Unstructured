# 프로젝트 세팅 인수인계 문서

이 문서는 현재 저장소의 최종 개발 기준을 빠르게 확인하기 위한 세팅 요약이다.

## 최종 기준

전자정부프레임워크 사용이 필수이므로, 메인 백엔드는 `egov-boot-web`이다.

```text
C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
```

`egov-boot-web`은 VS Code Extension `eGovFrame Initializr 5.0.5`로 생성한 eGovFrame Boot Web 프로젝트이며, `pom.xml`에 eGovFrame 5.0 실행환경 의존성이 포함되어 있다.

기존 `backend`는 Spring Initializr 기반 Gradle 프로젝트였으나, 전자정부프레임워크 필수 조건을 만족하기 위해 더 이상 메인 백엔드로 보지 않는다. 기존 `backend`의 민원 API, 도메인, 서비스 코드는 `egov-boot-web`의 `egovframework.example.complaint` 패키지로 이식했다.

## 현재 주요 폴더

```text
egov-boot-web/   # 메인 백엔드: eGovFrame 5.0 기반 Maven 프로젝트
backend/         # 이전 Spring Boot/Gradle 백엔드. 참고용 또는 추후 정리 대상
frontend/        # 프론트엔드 작업 공간
```

## eGovFrame 적용 상태

`egov-boot-web/pom.xml` 기준:

```text
parent: org.egovframe.boot:egovframe-boot-starter-parent:5.0.0
```

포함된 eGovFrame 실행환경 모듈:

```text
org.egovframe.rte:egovframe-rte-ptl-mvc
org.egovframe.rte:egovframe-rte-psl-dataaccess
org.egovframe.rte:egovframe-rte-fdl-idgnr
org.egovframe.rte:egovframe-rte-fdl-property
org.egovframe.rte:egovframe-rte-ptl-reactive
```

추가 적용한 프로젝트 의존성:

```text
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-actuator
postgresql
```

## Java 및 Maven

Java는 JDK 17 기준이다.

```powershell
java -version
```

예상:

```text
openjdk version "17.0.19"
```

Maven은 설치 후 PowerShell에서 `mvn` 명령이 잡혀야 한다.

```powershell
mvn -v
```

현재 설치 경로:

```text
C:\maven\apache-maven-3.9.16
```

사용자 PATH에는 다음 경로를 추가했다.

```text
C:\maven\apache-maven-3.9.16\bin
```

이미 열려 있던 터미널에서는 PATH 갱신이 바로 반영되지 않을 수 있으므로, `mvn`이 인식되지 않으면 터미널을 새로 열면 된다.

## 서버 포트

`egov-boot-web/src/main/resources/application.properties` 기준:

```properties
server.port=8081
```

기존 8080 포트 충돌 가능성을 피하기 위해 8081을 사용한다.

## 로컬 DB

개발 DB는 Docker PostgreSQL을 기준으로 한다.

```text
Host: localhost
Port: 5432
Database: complaintdb
Username: complaint_user
Password: complaint_pass
```

`egov-boot-web/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/complaintdb
spring.datasource.username=complaint_user
spring.datasource.password=complaint_pass
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
```

eGovFrame 샘플의 HSQLDB 설정은 PostgreSQL 설정으로 교체했다. 샘플 파일 `db/sampledb.sql`은 더 이상 메인 업무 DB 기준이 아니다.

## 이식된 민원 API

이식 위치:

```text
egov-boot-web/src/main/java/egovframework/example/complaint
```

주요 패키지:

```text
api
api/dto
config
domain
repository
service
```

현재 API:

```text
POST /api/complaints
GET  /api/complaints
GET  /api/complaints/{id}
GET  /api/complaints/{id}/analysis
GET  /api/complaints/{id}/draft
```

현재 구현은 Mock LLM/Mock RAG 응답을 사용한다. 실제 AI, RAG, AWS 연동은 비용과 일정 때문에 후속 단계에서 어댑터 방식으로 교체한다.

## 실행 명령

Maven이 PATH에 잡힌 뒤:

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn spring-boot:run
```

상태 확인:

```powershell
Invoke-WebRequest -Uri http://localhost:8081/actuator/health -UseBasicParsing
```

## 다음 개발 순서

1. Maven PATH 문제 해결
2. `egov-boot-web` Maven 빌드 확인
3. PostgreSQL 컨테이너 실행 확인
4. 민원 등록 API 수동 호출 확인
5. 기존 `backend`를 참고용으로 둘지 삭제할지 결정
6. Mock LLM/Mock RAG를 인터페이스 기반 어댑터로 분리
7. 프론트엔드 대시보드를 `egov-boot-web` API에 연결

## 주의사항

- 전자정부프레임워크 필수 조건 때문에 최종 산출물과 발표에서는 `egov-boot-web`을 기준 백엔드로 설명한다.
- `backend`는 eGovFrame 실행환경을 포함하지 않으므로 메인 백엔드로 설명하면 안 된다.
- API Key, DB 비밀번호, AWS Access Key 등은 코드와 Git에 직접 저장하지 않는다.
- 실제 LLM/Vector DB 호출은 비용이 발생하므로 개발 초반에는 Mock 구현을 유지한다.
