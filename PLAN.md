# 전자정부 표준프레임워크 기반 민원 분석 및 RAG 공문 초안 시스템 계획

## 1. 프로젝트 개요

본 프로젝트는 전자정부 표준프레임워크 5.0을 기반으로 한 Spring 애플리케이션을 중심 백엔드로 구성하고, AWS의 저장소, AI 분석, RAG 검색, 운영 인프라를 연동하여 비정형 민원 데이터를 구조화하고 공문 답변 초안을 생성하는 시스템을 목표로 한다.

시스템은 웹 게시판 민원, 이미지, 첨부파일 등 다양한 형태의 민원 데이터를 수집한 뒤, AI 분석과 검색증강생성(RAG)을 통해 담당자가 검토할 수 있는 정형 데이터와 공문 초안을 제공한다. 최종 결과는 담당자 웹 대시보드와 지도 기반 화면에서 활용할 수 있도록 API 중심으로 설계한다.

## 2. 핵심 목표

- 비정형 민원 데이터를 수집하고 원문, 이미지, 첨부파일을 통합 관리한다.
- OCR, 이미지 분석, LLM 기반 의도 분석을 통해 민원 핵심 정보를 JSON으로 구조화한다.
- 위치 정보가 포함된 민원은 GeoJSON으로 변환하여 지도 기반 웹 대시보드에 활용한다.
- 법령, 조례, 민원 처리 매뉴얼, 과거 답변 사례를 검색 가능한 지식 베이스로 구성한다.
- RAG 검색 결과와 민원 분석 결과를 결합하여 공공기관 업무 문맥에 맞는 공문 답변 초안을 생성한다.
- 담당 부서 라우팅, 긴급도 분류, 감정 상태 파악 등 민원 처리 보조 기능을 제공한다.

## 3. 전체 아키텍처

```text
민원 입력 채널
웹 게시판 / 이미지 / 첨부파일 / 외부 연계 데이터
        |
        v
eGovFrame 5.0 + Spring Backend
민원 접수 API / 전처리 / 업무 로직 / 권한 제어
        |
        v
AWS 연동 계층
S3 / Textract / Rekognition / Bedrock / OpenSearch Serverless / RDS
        |
        v
AI 분석 및 RAG 파이프라인
OCR / 이미지 분석 / 의도 분석 / 벡터 검색 / 공문 초안 생성
        |
        v
결과 제공
JSON / GeoJSON / 공문 초안 / 담당자 웹 대시보드
```

백엔드는 전자정부 표준프레임워크 5.0의 Spring 기반 구조를 사용한다. AWS 서비스는 파일 저장, AI 분석, 벡터 검색, 업무 데이터 저장, 로그 관리, 인증정보 관리를 담당한다. 웹 대시보드는 백엔드 REST API를 통해 구조화된 민원 데이터와 지도 시각화 데이터를 조회한다.

## 4. AWS 연동 구성

| 영역 | AWS 서비스 | 역할 |
| --- | --- | --- |
| 파일 저장 | Amazon S3 | 민원 이미지, 첨부파일, 원본 문서 저장 |
| OCR 및 문서 인식 | Amazon Textract | 이미지, PDF, 스캔 문서에서 텍스트 추출 |
| 이미지 분석 | Amazon Rekognition | 현장 이미지의 객체, 장면, 위험 요소 분석 보조 |
| LLM 분석 | Amazon Bedrock | 민원 의도 분석, 구조화, 공문 초안 생성 |
| Vector DB/RAG 검색 | Amazon OpenSearch Serverless | 법령, 조례, 매뉴얼, 답변 사례 임베딩 검색 |
| 업무 데이터 저장 | Amazon RDS PostgreSQL | 민원 메타데이터, 처리 상태, 초안 이력 저장 |
| 배포 | Amazon ECS Fargate | 컨테이너 기반 백엔드 서비스 실행 |
| 로그 및 모니터링 | Amazon CloudWatch | 애플리케이션 로그, 오류 추적, 운영 지표 확인 |
| 인증정보 관리 | AWS Secrets Manager | API Key, DB 접속 정보, 외부 연계 비밀값 관리 |
| 권한 제어 | AWS IAM Role | 서비스 간 최소 권한 접근 제어 |

AWS 연동은 선택 기능이 아니라 시스템 핵심 구성 요소로 다룬다. 다만 공공기관 내부망 또는 지정 인프라 환경이 필요한 경우, ECS Fargate 배포 구조는 컨테이너 기반 온프레미스 실행 구조로 대체 가능하도록 설계한다.

## 5. eGovFrame/Spring 백엔드 구성

백엔드는 eGovFrame 5.0 기반 Spring 애플리케이션으로 구성한다. Controller, Service, Repository 또는 Mapper 계층을 분리하고, AWS SDK for Java와 Spring AI를 통해 Amazon Bedrock 및 AWS 클라우드 서비스를 연동한다.

주요 구성은 다음과 같다.

- 민원 접수 API: 텍스트, 이미지, 첨부파일을 수신하고 기본 메타데이터를 생성한다.
- 원본 저장 서비스: 수신 파일을 Amazon S3에 저장하고 참조 경로를 업무 DB에 기록한다.
- 전처리 서비스: HTML 제거, 텍스트 정규화, OCR 결과 병합을 수행한다.
- AI 분석 서비스: Spring AI 기반 Bedrock 연동을 통해 의도, 긴급도, 감정, 위치, 담당 부서를 추출한다.
- RAG 검색 서비스: OpenSearch Serverless에서 관련 지식 문서를 검색하고 근거 문맥을 구성한다.
- 공문 초안 생성 서비스: 민원 원문, 분석 결과, RAG 문맥을 결합해 답변 초안을 생성한다.
- 결과 조회 API: 웹 대시보드에서 사용할 JSON, GeoJSON, 공문 초안 데이터를 제공한다.
- 보안 및 권한: eGovFrame/Spring Security 기반 담당자 인증과 역할별 접근 제어를 적용한다.

## 6. RAG 기반 민원 분석 파이프라인

민원 처리 흐름은 다음 기준 기능을 중심으로 구성한다.

1. 민원 원문, 이미지, 첨부파일을 접수한다.
2. 원본 데이터를 Amazon S3에 저장한다.
3. Amazon Textract로 이미지 또는 문서의 텍스트를 추출한다.
4. Amazon Rekognition으로 현장 이미지의 주요 객체와 장면 정보를 분석한다.
5. Spring AI 기반 LLM 체인을 통해 민원 의도, 긴급도, 감정 상태, 위치 정보, 담당 부서를 추출한다.
6. 분석 결과를 JSON으로 저장하고, 위치 정보가 있는 경우 GeoJSON을 생성한다.
7. 핵심 키워드와 분석 결과를 기반으로 OpenSearch Serverless에서 관련 법령, 조례, 매뉴얼, 답변 사례를 검색한다.
8. 검색된 문맥과 민원 정보를 결합하여 Amazon Bedrock 기반 공문 초안 생성을 수행한다.
9. 생성 결과를 담당자 웹 대시보드와 지도 화면에 제공한다.

AI 응답은 담당자 검토를 전제로 한다. 공문 초안에는 가능한 경우 참조 근거, 적용 조례, 관련 매뉴얼 문맥을 함께 제공하여 검토 가능성을 높인다.

## 7. 데이터 모델 초안

```java
public record ComplaintData(
    String id,
    String sourceChannel,
    String rawText,
    String structuredJsonMetadata
) {}

public record ComplaintAnalysis(
    String complaintId,
    String intent,
    String urgency,
    String sentiment,
    String department,
    String locationText,
    String geoJson
) {}

public record RagContext(
    String documentId,
    String legalBasis,
    String contentSnippet,
    Double score
) {}

public record DraftResponse(
    String complaintId,
    String draftText,
    List<RagContext> references
) {}

public interface ComplaintDraftService {
    DraftResponse generateOfficialDraft(
        ComplaintData data,
        ComplaintAnalysis analysis,
        List<RagContext> contextList
    );
}
```

업무 DB에는 민원 기본 정보, 원본 파일 참조, 분석 결과, RAG 검색 결과, 공문 초안, 담당자 검토 상태를 저장한다. Vector DB에는 법령, 조례, 매뉴얼, 답변 사례의 청크와 임베딩 벡터를 저장한다.

## 8. 웹 대시보드 연동 방향

프론트엔드는 담당자가 민원을 조회, 필터링, 검토, 수정할 수 있는 웹 대시보드 형태로 구성한다. 지도 기반 화면은 GeoJSON 데이터를 받아 민원 위치를 마커로 표시하고, 마커 선택 시 민원 원문, 분석 결과, 공문 초안을 확인할 수 있도록 구성한다.

백엔드는 다음 데이터를 API로 제공한다.

- 민원 목록 및 처리 상태
- 민원 상세 원문과 첨부파일 참조
- LLM 분석 결과 JSON
- 위치 기반 GeoJSON
- RAG 참조 문서 목록
- 공문 답변 초안
- 담당자 수정 및 확정 이력

실시간성이 필요한 알림은 WebSocket 또는 Server-Sent Events 방식으로 확장 가능하게 설계한다.

초기 대표 업무 시나리오는 불법 투기 신고로 고정한다. 시민 신고 이미지와 민원 원문을 분석하여 위치, 신고 의도, 긴급도, 담당 부서 후보를 추출하고, 폐기물 관리 관련 법령과 지자체 조례, 민원 응대 예시를 RAG로 검색한 뒤 담당자 검토용 공문 초안을 생성한다.

## 9. 운영 및 보안 고려사항

- 원본 민원 데이터와 첨부파일은 S3에 저장하고, 접근 권한은 IAM Role과 버킷 정책으로 제한한다.
- API Key, DB 접속 정보, 외부 서비스 인증정보는 AWS Secrets Manager에서 관리한다.
- 담당자 계정은 Spring Security와 eGovFrame 보안 구조를 기반으로 역할별 접근을 제한한다.
- LLM 호출 결과는 그대로 확정하지 않고 담당자 검토 상태를 거치도록 한다.
- 공문 초안 생성 시 RAG 참조 근거를 함께 저장하여 추적 가능성을 확보한다.
- CloudWatch를 통해 오류 로그, API 호출 실패, AI 서비스 응답 오류를 확인한다.
- 비용 관리를 위해 개발 및 검증 환경에서는 Mock 응답 전환 기능을 제공한다.
- PostgreSQL 확인 및 수동 쿼리 검증은 DBeaver Community와 psql을 함께 사용한다.

## 10. 구축 우선순위

1. eGovFrame 5.0 기반 Spring 백엔드 프로젝트 구조 확립
2. 민원 접수 API와 S3 원본 저장 연동
3. Textract/Rekognition 기반 텍스트 및 이미지 분석 연동
4. Spring AI 기반 Bedrock 민원 의도 분석 구현
5. OpenSearch Serverless 기반 RAG 지식 베이스 구축
6. 공문 초안 생성 서비스 구현
7. JSON/GeoJSON 결과 API 제공
8. 담당자 웹 대시보드 및 지도 시각화 연동
9. 보안, 로깅, Mock 응답 전환, 예외 처리 보강

## 11. 현재 개발 환경 구성 진행 상태

2026-05-27 기준으로 로컬 개발 환경의 1차 백엔드 실행 준비를 완료했다.

- IDE는 VSCode를 사용한다.
- 백엔드는 `backend` 디렉터리에 Spring Boot 3.5.14, Java 17, Gradle 기반으로 생성했다.
- 주요 초기 의존성은 Spring Web, Spring Security, Spring Data JPA, PostgreSQL Driver, Actuator, Lombok으로 구성했다.
- Docker Desktop 기반 로컬 PostgreSQL 컨테이너 `complaint-postgres`를 사용한다.
- 로컬 DB 접속 기준은 `localhost:5432`, database `complaintdb`, user `complaint_user`이다.
- DBeaver는 로컬 PostgreSQL 확인 및 수동 쿼리 검증 도구로 사용한다.
- 로컬 실행 전용 설정 파일 `backend/src/main/resources/application-local.properties`를 추가했다.
- `local` 프로필은 로컬 PostgreSQL datasource, JPA 설정, Actuator health endpoint, `8081` 포트를 사용한다.
- Spring Boot 실행 중 HikariCP가 PostgreSQL 연결을 생성했고, PostgreSQL 16.14 연결이 확인되었다.
- 기존 실행 실패 원인은 DB 연결 실패가 아니라 기본 `8080` 포트 충돌과 시스템 기본 Java 26 사용 문제로 확인되었다.

로컬 실행 시에는 JDK 17을 명시한 뒤 `local` 프로필로 실행한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
cd C:\Users\user\Documents\GitHub\Unstructured\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

실행 후 상태 확인은 다음 endpoint로 수행한다.

```powershell
Invoke-WebRequest -Uri http://localhost:8081/actuator/health -UseBasicParsing
```

다음 구현 단계는 민원 도메인 엔티티, Repository, Service, Controller를 추가하고, Mock LLM과 Mock RAG 응답을 먼저 연결하는 것이다.

## 12. eGovFrame 5.0 적용 방식 확인

2026-05-27 기준으로 eGovFrame 5.0 공식 개발가이드와 현재 Spring Boot 프로젝트의 호환 방향을 확인했다.

- eGovFrame 공식 5.0 Getting Started 문서는 전자정부 표준프레임워크 5.0 개발 시작 문서를 제공한다.
- eGovFrame 5.0 실행환경은 Spring Framework 6.2 기반이며, JDK 17 이상, Jakarta EE, Servlet 5.0 이상을 요구한다.
- 공식 문서 기준 Spring Boot 사용 시에는 Servlet 6.0 이상 환경이 필요하다.
- eGovFrame 5.0 개발환경 도구 자체는 Eclipse 기반 구현도구를 전제로 하며, 개발환경 5.0부터 JDK 21이 필요하다고 안내한다.
- 현재 프로젝트는 VSCode 기반 Spring Boot 3.5.14, Java 17, Gradle 구조로 생성되어 있으므로 eGovFrame 공식 Eclipse 템플릿 프로젝트를 그대로 사용한 형태는 아니다.
- Spring Boot 3.5.14는 Spring Framework 6.2.x 계열 의존성을 관리하므로 eGovFrame 5.0의 Spring Framework 6.2 기반 실행환경 방향과는 같은 세대에 있다.
- 다만 eGovFrame 5.0 공식 문서에서 Spring Boot 3.5.14를 특정하여 보증한다고 확인되지는 않았으므로, 본 프로젝트는 "eGovFrame 5.0 완전 템플릿 기반"이 아니라 "Spring Boot 기반 프로젝트에 eGovFrame 5.0의 계층 구조와 공공기관 백엔드 설계 관례를 반영"하는 방식으로 진행한다.

본 프로젝트의 eGovFrame 적용 방식은 선택지 A로 확정한다.

```text
선택지 A:
현재 Spring Boot 프로젝트 유지
eGovFrame 5.0 공식 템플릿으로 재생성하지 않음
Controller / Service / Repository / DTO / Entity / Config / Common 계층 구조를 명확히 분리
공통 예외 처리, 공통 응답 형식, 보안, 로깅, 검증, 트랜잭션 관리 등 eGovFrame식 공공기관 백엔드 설계 관례를 반영
```

개발 세션에서는 이 결정을 기준으로 실제 도메인/API 구현을 진행한다. 현재 세션에서는 기본 환경 구성과 프로젝트 방향 확정까지만 다룬다.

## 13. 1차 민원 API 구현 상태

2026-05-28 기준으로 권장 첫 구현 범위의 1차 백엔드 API를 구현했다.

구현된 endpoint는 다음과 같다.

```text
POST /api/complaints
GET /api/complaints
GET /api/complaints/{id}
GET /api/complaints/{id}/analysis
GET /api/complaints/{id}/draft
```

구현 범위는 로컬 개발과 시연용 Mock 흐름에 맞춰 제한했다.

- `Complaint` JPA Entity와 `ComplaintRepository`를 추가했다.
- `ComplaintService`에서 민원 등록, 목록 조회, 상세 조회, Mock 분석, Mock 공문 초안 생성을 처리한다.
- 실제 LLM, RAG, AWS 호출은 아직 연결하지 않았다.
- `/analysis`는 민원 원문 키워드를 기반으로 의도, 긴급도, 감정 상태, 담당 부서, 위치 기반 GeoJSON 초안을 생성한다.
- `/draft`는 Mock RAG 참조 문서와 담당자 검토용 공문 초안을 반환한다.
- 개발 편의를 위해 `/api/**`, `/actuator/health`, `/actuator/info`는 인증 없이 접근 가능하도록 설정했다.
- 테스트 프로필은 H2 메모리 DB를 사용하도록 분리했다.
- 로컬 프로필은 PostgreSQL에 대해 `spring.jpa.hibernate.ddl-auto=update`를 사용해 개발용 테이블을 자동 생성한다.

검증 결과:

```text
./gradlew.bat test 성공
local 프로필 Spring Boot 실행 성공
PostgreSQL 16.14 연결 성공
POST /api/complaints 저장 성공
GET /api/complaints/{id}/analysis 응답 성공
GET /api/complaints/{id}/draft 응답 성공
```

다음 개발 단계는 API 응답 형식 정리, 도메인 필드 확장, 샘플 데이터 보강, 실제 파일 업로드/S3 연동 또는 Mock 파일 저장 계층 추가이다.
# 2026-05-28 최종 백엔드 기준 변경

전자정부프레임워크 사용이 필수 조건이므로, 최종 메인 백엔드는 기존 `backend`가 아니라 eGovFrame Initializr 5.0.5로 생성한 `egov-boot-web`으로 변경한다.

## 최종 결정

- `egov-boot-web`: eGovFrame 5.0 실행환경 기반 메인 백엔드
- `backend`: 기존 Spring Initializr/Gradle 기반 백엔드. 참고용 또는 추후 정리 대상
- 민원 API, 도메인, Repository, Service 코드는 `egov-boot-web/src/main/java/egovframework/example/complaint` 하위로 이식
- 포트는 `8081`, DB는 로컬 PostgreSQL `complaintdb` 기준
- 초기 AI/RAG는 비용 절감을 위해 Mock 구현 유지

## 현재 적용한 핵심 세팅

```text
parent: org.egovframe.boot:egovframe-boot-starter-parent:5.0.0
server.port=8081
spring.datasource.url=jdbc:postgresql://localhost:5432/complaintdb
spring.datasource.username=complaint_user
spring.datasource.password=complaint_pass
spring.jpa.hibernate.ddl-auto=update
```

## 다음 확인 작업

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn test
mvn spring-boot:run
```

Maven이 PowerShell에서 인식되지 않으면 Maven 설치 경로의 `bin` 디렉터리를 Windows PATH에 추가하고 터미널을 다시 열어야 한다.

현재 Maven 설치 경로는 `C:\maven\apache-maven-3.9.16`이며, 사용자 PATH에 `C:\maven\apache-maven-3.9.16\bin`을 추가했다.
