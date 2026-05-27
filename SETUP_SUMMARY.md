# 기본 세팅 인수인계 문서

이 문서는 다른 개발 세션에서 현재 프로젝트 상태를 빠르게 이어받기 위한 기본 세팅 정리이다.

## 프로젝트 개요

현재 프로젝트는 민원 분석 및 RAG 기반 공문 초안 생성 시스템이다. 초기 시나리오는 불법 투기 신고 민원이다.

목표 흐름은 다음과 같다.

```text
민원 접수
-> 원본 텍스트/이미지/첨부파일 저장
-> OCR/이미지 분석
-> LLM 기반 민원 구조화
-> RAG 기반 법령/조례/매뉴얼 검색
-> 담당자 검토용 공문 초안 생성
-> 웹 대시보드/지도 화면 제공
```

## 저장소 상태

작업 경로:

```text
C:\Users\user\Documents\GitHub\Unstructured
```

현재 주요 파일/폴더:

```text
README.md
PLAN.md
SETUP_SUMMARY.md
backend/
frontend/
```

`README.md`, `PLAN.md`, `SETUP_SUMMARY.md`, `backend/`는 Git 기준으로 아직 추적되지 않은 상태일 수 있다.

## 백엔드 기본 세팅

백엔드는 아래 위치에 생성되어 있다.

```text
C:\Users\user\Documents\GitHub\Unstructured\backend
```

Spring Initializr 기반 설정:

```text
Spring Boot: 3.5.14
Java: 17
Build: Gradle
Language: Java
Group: com.school
Artifact: complaint
Package: com.school.complaint
Packaging: Jar
```

현재 주요 의존성:

```text
Spring Web
Spring Security
Spring Data JPA
PostgreSQL Driver
Spring Boot Actuator
Lombok
Spring Boot Test
Spring Security Test
```

`backend/build.gradle`에 위 의존성이 반영되어 있다.

## Spring Boot 버전 판단

처음에는 Spring Boot 4.0.6도 검토했지만, 학교 프로젝트 안정성과 호환성 때문에 Spring Boot 3.5.14로 진행하기로 했다.

Spring/Spring Boot는 별도 설치하지 않는다. Gradle 의존성으로 관리된다.

## Java 설정

현재 PC 기본 Java는 Java 26이다.

```text
java version "26.0.1"
```

이 상태로 Gradle을 실행하면 다음 오류가 발생했다.

```text
Unsupported class file major version 70
```

JDK 17은 설치되어 있다.

```text
C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
```

백엔드 실행 시 반드시 JDK 17을 명시해야 한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
cd C:\Users\user\Documents\GitHub\Unstructured\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

## 로컬 DB 세팅

AWS RDS는 당장 사용하지 않고, 개발 단계에서는 Docker PostgreSQL을 사용한다.

Docker 컨테이너:

```text
container name: complaint-postgres
image: postgres:16
port: 5432:5432
status: running 확인됨
```

DB 접속 정보:

```text
Host: localhost
Port: 5432
Database: complaintdb
Username: complaint_user
Password: complaint_pass
```

DBeaver는 설치되어 있으며, 위 정보로 로컬 PostgreSQL 확인 및 수동 쿼리 검증에 사용한다.

## Spring local 설정

로컬 실행 설정 파일:

```text
backend/src/main/resources/application-local.properties
```

현재 내용:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/complaintdb
spring.datasource.username=complaint_user
spring.datasource.password=complaint_pass
spring.datasource.driver-class-name=org.postgresql.Driver

server.port=8081

spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false

management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

`8080` 포트가 이미 사용 중이라 local 프로필은 `8081`을 사용하도록 설정했다.

DB 연결은 확인되었다. Spring Boot 실행 로그에서 다음 내용이 확인되었다.

```text
HikariPool-1 - Start completed
Database version: 16.14
```

실행 후 헬스체크:

```powershell
Invoke-WebRequest -Uri http://localhost:8081/actuator/health -UseBasicParsing
```

## AWS 기본 세팅 방향

AWS는 프리티어/학교 프로젝트 기준으로 최소 사용한다.

결정된 전략:

```text
초기 개발: 로컬 PostgreSQL + Mock LLM + Mock RAG
실제 연동 최소화: S3, Secrets Manager, CloudWatch
보류: RDS, OpenSearch Serverless
AI 호출: 비용 방지를 위해 실제 호출 최소화
```

리전 전략:

```text
기본 AWS 리전: ap-northeast-2
S3: ap-northeast-2
CloudWatch: ap-northeast-2
Secrets Manager: ap-northeast-2
Textract/Rekognition: ap-northeast-2 우선
Bedrock: us-east-1 또는 ap-northeast-2 중 모델 사용 가능한 곳
```

AWS CLI는 설치했고, `aws configure`로 설정하는 방향을 안내했다.

확인 명령:

```powershell
aws sts get-caller-identity
```

## Secrets Manager

Secrets Manager에는 보안 암호를 2개로 나누는 방향을 잡았다.

DB 설정:

```text
secret name: school-complaint/dev/db
type: other type of secret
rotation: disabled
```

예상 key/value:

```text
host: localhost
port: 5432
database: complaintdb
username: complaint_user
password: complaint_pass
driver: postgresql
```

API Key 설정:

```text
secret name: school-complaint/dev/api-keys
type: other type of secret
rotation: disabled
```

예상 key/value:

```text
publicDataPortalApiKey
lawApiKey
openAiApiKey
geminiApiKey
```

아직 없는 키는 빈 값으로 넣지 않는 방침이다.

## CloudWatch

권장 로그 그룹:

```text
/school-complaint/dev/app
/school-complaint/dev/aws-ai
```

권장 retention:

```text
7 days
```

## Bedrock 관련 최신 정리

AWS Bedrock의 기존 `Model access` 페이지는 retired 되었다.

현재 방식:

```text
일반 serverless foundation model은 첫 호출 시 자동 활성화
Anthropic 모델은 최초 사용 시 use case details 제출 필요 가능
Marketplace 모델은 Marketplace 권한이 있는 사용자의 첫 호출로 계정 전체 활성화 가능
IAM/SCP로 모델 접근 제어
```

필요한 IAM 액션 예시:

```text
bedrock:ListFoundationModels
bedrock:GetFoundationModel
bedrock:InvokeModel
bedrock:InvokeModelWithResponseStream
bedrock:Converse
bedrock:ConverseStream
```

현재 프로젝트는 비용 때문에 실제 Bedrock 호출은 나중으로 미룬다.

## LLM 비용/모델 결정

GPT-4o/GPT-4.0 메인 사용은 비용 때문에 비추천했다.

현재 결정:

```text
개발 기본값: Mock LLM
선택 후보: Gemini Free Tier 또는 GPT-4o mini
AWS 일관성 후보: Bedrock
```

3주 학교 프로젝트 기준 권장 흐름:

```text
Mock LLM으로 전체 기능 구현
필요 시 마지막 시연에서만 실제 LLM 짧게 호출
```

## RAG 결정

OpenSearch Serverless는 비용과 난이도 때문에 당장 만들지 않는다.

초기 구현:

```text
Mock RAG
또는 PostgreSQL 기반 단순 검색
```

운영/문서상 최종 구조:

```text
OpenSearch Serverless 기반 Vector DB/RAG
```

## eGovFrame 5.0 판단

공식 문서 확인 결과:

```text
eGovFrame 5.0 실행환경: Spring Framework 6.2 기반
JDK 17 이상
Jakarta EE
Servlet 5.0 이상
Spring Boot 사용 시 Servlet 6.0 이상
개발환경 도구는 Eclipse 기반이며 JDK 21 필요 안내
```

현재 프로젝트:

```text
VSCode
Spring Boot 3.5.14
Java 17
Gradle
```

Spring Boot 3.5.14는 Spring Framework 6.2.x 계열을 관리하므로 eGovFrame 5.0 실행환경 방향과 같은 세대이다.

다만 공식 eGovFrame 템플릿 프로젝트를 사용한 것은 아니다.

최종 결정:

```text
선택지 A로 진행
현재 Spring Boot 프로젝트 유지
eGovFrame 5.0 공식 Eclipse 템플릿으로 재생성하지 않음
eGovFrame 5.0의 계층 구조와 공공기관 백엔드 설계 관례를 반영
```

개발 시 반영할 구조:

```text
Controller
Service
Repository
DTO
Entity
Config
Common
Security
Exception
```

정확한 표현:

```text
"eGovFrame 5.0 완전 템플릿 기반"은 아님
"Spring Boot 기반 프로젝트에 eGovFrame 5.0의 계층 구조와 공공기관 백엔드 설계 관례를 반영"하는 방식
```

## DB 접근 방식

MyBatis와 JPA 중에서는 현재 JPA 사용으로 진행한다.

근거:

```text
spring-boot-starter-data-jpa 이미 추가됨
PostgreSQL Driver 추가됨
로컬 datasource 연결 확인됨
```

## Spring AI

Spring AI는 아직 실제 의존성 추가/구현하지 않았다.

현재 방향:

```text
처음에는 Mock LLM Client
나중에 Spring AI 또는 별도 LLM Client로 교체 가능하게 설계
```

eGovFrame 5.0 공식 실행환경 문서에는 AI 통합, Spring AI, RAG 관련 항목이 존재한다.

## 현재 PLAN.md 업데이트

`PLAN.md`에는 아래 내용이 반영되어 있다.

```text
현재 개발 환경 구성 진행 상태
로컬 DB 연결 확인
Java 26 문제와 JDK 17 실행 방법
eGovFrame 5.0 적용 방식 확인
선택지 A 확정
개발 세션에서는 실제 도메인/API 구현 진행
현재 세션은 기본 환경 구성과 방향 확정까지만 진행
```

## 다음 개발 세션에서 할 일

개발 세션에서는 바로 아래부터 시작하면 된다.

```text
1. 현재 backend 구조 확인
2. 공통 패키지 구조 생성
3. Complaint Entity 설계
4. ComplaintAnalysis Entity 설계
5. DraftResponse 또는 OfficialDraft Entity 설계
6. Repository 생성
7. Service 계층 생성
8. Controller 생성
9. Mock LLM 서비스 생성
10. Mock RAG 서비스 생성
11. 샘플 민원 등록 API 구현
12. 조회 API 구현
13. Actuator health + DB 연결 재확인
```

권장 첫 구현 범위:

```text
POST /api/complaints
GET /api/complaints
GET /api/complaints/{id}
GET /api/complaints/{id}/analysis
GET /api/complaints/{id}/draft
```

## 주의사항

- AWS Access Key, OpenAI Key, 공공데이터 API Key는 코드나 Git에 넣지 않는다.
- 실제 AI 호출은 비용 때문에 개발 초반에 하지 않는다.
- RDS/OpenSearch Serverless는 현재 만들지 않는다.
- Java 26이 기본값이라 실행 시 JDK 17 지정이 필요하다.
- local DB 비밀번호 `complaint_pass`는 개발용 예시값이다.
