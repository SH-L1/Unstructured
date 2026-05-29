# 프로젝트 세팅 요약

이 문서는 현재 저장소의 최종 개발 기준과 실행 세팅을 빠르게 확인하기 위한 요약이다.

## 2026-05-29 현재 핵심 변경 요약

팀원 변경 이후 현재 프로젝트는 `egov-boot-web` 중심으로 정리되어 있다.

```text
egov-boot-web:
메인 백엔드. eGovFrame Boot Web 5.0 기반 Maven 프로젝트.

ai-rag-engine:
Python 로컬 AI/RAG 검증 엔진. OpenAI API 실험과 Markdown 지식문서 DB 적재 담당.

backend:
초기 Spring Boot/Gradle 백엔드. 현재는 참고용 또는 삭제 검토 대상.
```

최근 팀원 수정으로 추가/확장된 내용:

- eGovFrame 5.0 기반 `egov-boot-web` 메인 서버
- Flyway V1~V3 마이그레이션 기반 DB 스키마
- 정규화된 민원 DB 모델과 Repository 계층
- 민원 등록/조회/필터링/상태 변경 API
- 민원 분석/GeoJSON/RAG 근거/공문 초안 생성 및 수정 API
- 첨부파일 업로드/목록/다운로드/삭제 API
- 부서 조회 API
- API Key 인증 옵션, API 사용자 모델, 감사 로그
- local/S3 파일 저장소 선택 구조
- Mock/Bedrock AI 분석 및 초안 생성 선택 구조
- PostgreSQL/OpenSearch RAG 검색 선택 구조
- Dockerfile, 운영 프로파일, 비AWS 기본 설정
- Python `ai-rag-engine`과 샘플 지식문서/민원 데이터

이번 확인에서 검증한 내용:

```text
egov-boot-web: mvn -q test 통과
backend: .\gradlew.bat test 통과
ai-rag-engine: python -m py_compile main.py insert_knowledge_documents.py test_db_connection.py 통과
```

주의:

- 최종 산출물과 발표 기준 서버는 `egov-boot-web`이다.
- `ai-rag-engine/main.py`는 OpenAI API를 호출하므로 실행 시 비용이 발생할 수 있다.
- 기본 개발 설정은 AWS S3, Bedrock, OpenSearch Serverless를 호출하지 않는다.
- API Key, DB 비밀번호, AWS/OpenAI Key는 코드와 Git에 직접 저장하지 않는다.

## 최종 기준

전자정부프레임워크 사용이 필수이므로 메인 백엔드는 `egov-boot-web`이다.

```text
C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
```

`egov-boot-web`은 VS Code Extension `eGovFrame Initializr 5.0.5`로 생성한 eGovFrame Boot Web 프로젝트이며, `pom.xml`에 eGovFrame 5.0 실행환경 의존성이 포함되어 있다.

기존 `backend`는 Spring Initializr 기반 Gradle 프로젝트였으나, 전자정부프레임워크 필수 조건을 만족하기 위해 더 이상 메인 백엔드로 보지 않는다. 기존 `backend`의 민원 API, 도메인, 서비스 코드는 `egov-boot-web`의 `egovframework.example.complaint` 패키지로 이식했다.

## 디렉터리 기준

```text
egov-boot-web/   # 메인 백엔드: eGovFrame 5.0 기반 Maven 프로젝트
ai-rag-engine/   # Python AI/RAG 검증 엔진 및 지식문서 적재 스크립트
backend/         # 이전 Spring Boot/Gradle 백엔드. 참고용 또는 추후 정리 대상
```

## eGovFrame 적용 상태

`egov-boot-web/pom.xml` 기준:

```xml
<parent>
    <groupId>org.egovframe.boot</groupId>
    <artifactId>egovframe-boot-starter-parent</artifactId>
    <version>5.0.0</version>
</parent>
```

포함된 eGovFrame 실행환경 모듈:

- `org.egovframe.rte:org.egovframe.rte.ptl.mvc`
- `org.egovframe.rte:org.egovframe.rte.psl.dataaccess`
- `org.egovframe.rte:org.egovframe.rte.fdl.idgnr`
- `org.egovframe.rte:org.egovframe.rte.fdl.property`

추가된 프로젝트 의존성:

- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql`
- `software.amazon.awssdk:s3`
- `software.amazon.awssdk:bedrockruntime`

## 배포 기준

`egov-boot-web`는 컨테이너 배포를 위해 다음 파일을 포함한다.

```text
egov-boot-web/Dockerfile
egov-boot-web/.dockerignore
egov-boot-web/src/main/resources/application-prod.properties
```

이미지 빌드 명령:

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn -DskipTests package
docker build -t egov-complaint-server:local .
```

운영 프로파일은 DB, API Key 값을 환경변수로 받는다. 코드나 Git에 비밀번호, AWS Access Key, Bedrock 모델 ID를 직접 저장하지 않는다.

현재 개발 단계에서는 비용 발생 방지를 위해 AWS 직접 호출을 기본값에서 제외한다. `application.properties`와 `application-prod.properties` 모두 명시적으로 AWS 기능을 켜지 않으면 S3, Bedrock, OpenSearch Serverless를 호출하지 않는다.

로컬/비AWS 운영 기준 필수 환경변수 예시:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host>:5432/complaintdb
DB_USERNAME=<db-user>
DB_PASSWORD=<db-password>
API_KEY_VALUE=<dashboard-api-key>
```

AWS 실연동을 명시적으로 켤 때만 추가하는 환경변수:

```text
AWS_S3_ENABLED=true
AWS_BEDROCK_ENABLED=true
OPENSEARCH_ENABLED=true
RAG_PROVIDER=opensearch
FILE_STORAGE_PROVIDER=s3
AI_MOCK_ENABLED=false
AI_PROVIDER=bedrock
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=<bucket-name>
AWS_BEDROCK_REGION=us-east-1
AWS_BEDROCK_MODEL_ID=<model-id>
```

개발 기본값은 API Key 인증이 꺼져 있고, 운영 프로파일은 `X-API-Key` 헤더 기반 보호가 켜져 있다.

API Key 인증을 켜면 부트스트랩 관리자 키가 `api_users` 테이블에 SHA-256 해시로 저장된다. 역할은 `ADMIN`, `OFFICER`, `VIEWER`를 사용하며, 기본 정책은 다음과 같다.

```text
ADMIN: 전체 API 허용
OFFICER: 조회, 민원 상태 변경, 공문 초안 수정 허용
VIEWER: 조회 전용
```

## Java 및 Maven

Java 기준:

```text
Java 17
```

Maven 설치 경로:

```text
C:\maven\apache-maven-3.9.16\bin
```

사용자 PATH에도 위 경로를 추가했다. 이미 열려 있던 터미널에서는 PATH 갱신이 바로 반영되지 않을 수 있으므로, `mvn`이 인식되지 않으면 터미널을 새로 열면 된다.

확인 명령:

```powershell
mvn -v
```

## 실행 포트

`egov-boot-web/src/main/resources/application.properties` 기준:

```properties
server.port=8081
```

기존 8080 포트 충돌 가능성을 피하기 위해 8081을 사용한다.

## 데이터베이스

PostgreSQL 기준:

```text
Host: localhost
Port: 5432
Database: complaintdb
User: complaint_user
Password: complaint_pass
```

애플리케이션 설정:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/complaintdb
spring.datasource.username=complaint_user
spring.datasource.password=complaint_pass
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
```

eGovFrame 샘플의 HSQLDB 설정은 PostgreSQL 설정으로 교체했다. 샘플 파일 `db/sampledb.sql`은 더 이상 메인 업무 DB 기준이 아니다.

## 구현 위치

민원 기능 구현 위치:

```text
egov-boot-web/src/main/java/egovframework/example/complaint
```

구성:

- `api`: REST API Controller, DTO, 예외 처리
- `domain`: 민원 Entity, 상태 Enum
- `repository`: JPA Repository
- `service`: 민원 접수/조회/분석 서비스
- `config`: 보안 설정

## API

현재 구현된 API:

```text
POST /api/complaints
GET  /api/complaints?status=&department=&urgency=&page=&size=
GET  /api/complaints/{id}
POST /api/complaints/{id}/attachments
GET  /api/complaints/{id}/attachments
GET  /api/complaints/{id}/attachments/{attachmentId}
DELETE /api/complaints/{id}/attachments/{attachmentId}
PATCH /api/complaints/{id}/status
GET  /api/complaints/{id}/analysis
GET  /api/complaints/{id}/draft
PUT  /api/complaints/{id}/draft
GET  /api/complaints/{id}/rag-contexts
GET  /api/complaints/{id}/geojson
GET  /api/departments
GET  /actuator/health
```

비용 방지를 위해 기본 설정에서는 Mock 분석/RAG/초안 로직을 사용한다. 첨부파일 저장소와 Bedrock 초안 생성은 설정으로 local/mock과 AWS 구현을 전환할 수 있다.

2026-05-29 런타임 검증 중 `ComplaintAttachmentRepository`의 JPA 메서드명을 수정했다.

```text
findByComplaintIdOrderByCreatedAtDesc
-> findByComplaint_IdOrderByCreatedAtDesc
```

로컬 PostgreSQL에 이전 실험 스키마가 남아 있을 때 Hibernate update가 충돌할 수 있어, DB 스키마 관리는 Flyway 기준으로 전환했다. Hibernate는 테이블을 자동 생성하지 않고 Entity와 DB 스키마가 맞는지만 검증한다.

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
```

신규 DB는 `src/main/resources/db/migration/V1__create_complaint_schema.sql`로 생성된다. 이미 Hibernate로 만들어진 로컬 개발 DB는 `baseline-on-migrate`로 Flyway 이력 테이블을 생성해 현재 V1 상태로 맞춘다.

2026-05-29 추가 구현으로 `egov-boot-web`의 민원 DB 모델을 정규화했다. 기존에는 분석 결과와 초안 본문을 `complaints`에 함께 두는 단순 구조였지만, 현재는 다음 테이블을 실제 JPA Entity/Repository로 사용한다.

```text
complaints
complaint_attachments
complaint_analysis
official_drafts
draft_revisions
departments
knowledge_documents
rag_contexts
knowledge_document_chunks
```

`departments`와 `knowledge_documents`는 서버 시작 시 seed 데이터가 들어간다. Mock 분석은 `complaint_analysis`에 저장되고, Mock RAG 결과는 `knowledge_documents` 검색 후 `rag_contexts`에 저장된다. 공문 초안은 `official_drafts`에 저장되며, 담당자 수정 내용은 `draft_revisions`에 저장된다.

V4 마이그레이션부터 `complaints`에는 `receipt_number`, `title`을 추가했고, `complaint_analysis`에는 `complaint_type`을 추가했다. RAG 문서는 원본 문서 단위인 `knowledge_documents`와 검색 단위인 `knowledge_document_chunks`로 분리한다.

개발 편의를 위해 `app.seed.demo-enabled=true`일 때 데모 민원 3건을 자동 등록하고, Mock 분석과 Mock 공문 초안 생성까지 수행한다. 테스트와 운영 프로파일에서는 기본값을 `false`로 두어 원하지 않는 데모 데이터가 들어가지 않도록 한다.

운영 추적을 위해 `audit_logs` 테이블도 사용한다. `/api/**` 요청의 HTTP method, path, actor, client IP, status code, duration, createdAt을 저장하며, `app.audit.enabled=false`로 비활성화할 수 있다.

API 사용자는 `api_users` 테이블에 저장한다. API Key 원문은 저장하지 않고 SHA-256 해시만 저장한다.

AWS/AI/RAG 교체 지점을 위해 `application.properties`에 아래 설정 경계를 추가했다.

```properties
app.ai.mock-enabled=true
app.ai.provider=mock-bedrock
app.aws.region=ap-northeast-2
app.aws.s3.enabled=false
app.aws.bedrock.enabled=false
app.rag.provider=postgres-mock
app.rag.opensearch.enabled=false
app.rag.opensearch.endpoint=
app.rag.opensearch.region=ap-northeast-2
app.rag.opensearch.index-name=civil-complaint-knowledge
app.file-storage.provider=local
app.security.api-key.enabled=false
app.security.bootstrap-admin.username=local-admin
app.audit.enabled=true
```

현재 서버는 비용 방지를 위해 Mock 분석/RAG/초안 생성과 로컬 파일 저장소를 기본값으로 사용한다. 실제 S3, Bedrock, OpenSearch 연동은 위 설정을 명시적으로 켠 뒤 교체한다.

첨부파일 저장소는 `FileStorageService` 인터페이스 뒤에 local/S3 구현을 분리했다.

```text
LocalFileStorageService: app.file-storage.provider=local
S3FileStorageService: app.file-storage.provider=s3
```

S3 저장소로 전환할 때 필요한 설정:

```properties
app.file-storage.provider=s3
app.aws.s3.enabled=true
app.aws.region=ap-northeast-2
app.aws.s3.bucket=<bucket-name>
app.aws.s3.key-prefix=complaints
```

AWS 인증정보는 코드에 넣지 않고 AWS SDK 기본 인증 체인, 환경변수, IAM Role, 또는 로컬 AWS CLI 설정을 사용한다.

Bedrock 민원 분석과 공문 초안 생성은 각각 `ComplaintAnalysisClient`, `DraftGenerationClient` 인터페이스 뒤에 선택형 구현을 추가했다.

```text
기본값: Mock 분석/초안 생성
BedrockComplaintAnalysisClient: app.aws.bedrock.enabled=true
BedrockDraftGenerationClient: app.aws.bedrock.enabled=true
```

Bedrock 분석/초안 생성으로 전환할 때 필요한 설정:

```properties
app.aws.bedrock.enabled=true
app.aws.bedrock.region=us-east-1
app.aws.bedrock.model-id=<bedrock-model-id>
```

Bedrock 구현은 Anthropic Claude Messages 형식의 request/response를 기준으로 작성되어 있다. 실제 모델을 바꿀 경우 request body와 response parser를 모델 형식에 맞게 조정한다.

RAG 문서 검색은 `KnowledgeDocumentSearchService` 인터페이스 뒤에 PostgreSQL 기반 기본 구현과 OpenSearch Serverless 구현을 분리했다.

```text
기본값: PostgresKnowledgeDocumentSearchService
OpenSearchKnowledgeDocumentSearchService: app.rag.provider=opensearch
```

OpenSearch Serverless RAG 검색으로 전환할 때 필요한 설정:

```properties
app.rag.provider=opensearch
app.rag.opensearch.enabled=true
app.rag.opensearch.endpoint=<collection-endpoint>
app.rag.opensearch.region=ap-northeast-2
app.rag.opensearch.index-name=civil-complaint-knowledge
app.rag.opensearch.result-size=3
```

OpenSearch Serverless 연결은 AWS 서명 기반 `AwsSdk2Transport`를 사용하며, Serverless 서비스명은 `aoss`로 설정한다. 검색 결과는 `title`, `documentType`, `sourceName`, `sourceUrl`, `content`, `keywords`, `legalBasis` 필드를 기준으로 읽고, 공문 초안 생성에 필요한 문서 메타데이터는 `knowledge_documents`에도 저장한다.

## 실행 명령

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn test
mvn spring-boot:run
```

상태 확인:

```powershell
Invoke-WebRequest -Uri http://localhost:8081/actuator/health -UseBasicParsing
```

## 확인 완료

- Maven 설치 및 PATH 추가
- `egov-boot-web` Maven 테스트 통과
- `egov-boot-web` 8081 런타임 기동 확인
- PostgreSQL 컨테이너 실행 확인
- 8081 포트 애플리케이션 실행 확인
- 민원 등록 API 수동 호출 확인
- 민원 분석 결과 조회 API 수동 호출 확인
- RAG/GeoJSON/초안 생성/초안 수정 API 수동 호출 확인
- 첨부파일 업로드/조회 API 수동 호출 확인
- 정규화 테이블 8개 생성 확인
- seed/API 데이터 저장 확인: departments 4건, knowledge_documents 3건, complaints 1건, complaint_analysis 1건, official_drafts 1건, rag_contexts 3건
- 부서 조회 API가 departments seed 데이터 4건을 반환하는 것 확인
- 필터링/상태 변경/부서/첨부파일 API 컴파일 확인
- `mvn test`에 H2 기반 API smoke test를 추가했다. 이 테스트는 민원 등록, 분석, 초안 생성, 초안 수정, RAG 조회, 부서 조회, 첨부파일 업로드/조회 흐름을 자동 검증한다.
- 첨부파일 다운로드/삭제 API를 추가했다. smoke test에서 업로드, 목록, 다운로드, 삭제 후 목록 재조회까지 검증한다.
- 선택형 S3 파일 저장 구현을 추가했다. 기본 테스트와 로컬 실행은 여전히 local 파일 저장소를 사용하므로 S3 비용은 발생하지 않는다.
- 선택형 Bedrock 민원 분석/초안 생성 구현을 추가했다. 기본 설정에서는 비활성화되어 Mock 분석/초안 생성을 사용한다.
- `mvn test` 통과를 통해 S3/Bedrock SDK 포함 상태의 컴파일과 API smoke test를 확인했다.
- 패키징된 JAR 실행 후 `GET /actuator/health`에서 PostgreSQL 상태가 `UP`으로 응답하는 것을 확인했다.
- Flyway V1 마이그레이션을 추가하고 `spring.jpa.hibernate.ddl-auto=validate`로 전환했다.
- `mvn test`에서 Flyway가 H2 테스트 DB에 V1 스키마를 적용하고 API smoke test가 통과하는 것을 확인했다.
- 로컬 PostgreSQL 런타임에서 Flyway baseline 및 Hibernate validate 후 서버가 정상 기동하는 것을 확인했다.
- Dockerfile, `.dockerignore`, 운영 프로파일 `application-prod.properties`를 추가했다.
- `docker build -t egov-complaint-server:local .` 명령으로 컨테이너 이미지 빌드를 확인했다.
- 설정 기반 API Key 필터를 추가했다. 검증 결과 API Key가 없으면 `/api/departments`가 401, 올바른 `X-API-Key`가 있으면 200을 반환했다.
- API 감사 로그 필터와 Flyway V2 마이그레이션을 추가했다.
- PostgreSQL에서 `flyway_schema_history`가 V2까지 성공 상태이고, API 호출 2건이 `audit_logs`에 저장된 것을 확인했다.
- OpenSearch Serverless RAG 검색 구현을 추가했다. 기본 테스트는 PostgreSQL 검색을 유지하지만, `app.rag.provider=opensearch` 설정으로 OpenSearch 검색으로 전환할 수 있다.
- OpenSearch Java Client 및 AWS SDK `AwsSdk2Transport` 포함 상태에서 `mvn test`, JAR 패키징, Docker 이미지 빌드를 확인했다.
- API 사용자/역할 모델을 추가하고 Flyway V3 마이그레이션을 적용했다.
- API Key 인증 활성화 시 부트스트랩 관리자 사용자가 `api_users`에 저장되는 것을 PostgreSQL에서 확인했다.
- 관리자 API Key로 `/api/departments` 200, 키가 없으면 401을 반환하는 것을 확인했다.
- 개발/운영 기본값을 모두 비AWS 호출 기준으로 조정했다. 명시적으로 `AWS_S3_ENABLED`, `AWS_BEDROCK_ENABLED`, `OPENSEARCH_ENABLED`를 `true`로 설정하지 않으면 AWS 비용 발생 API를 호출하지 않는다.
- 데모 민원 seed 옵션을 추가했다. 로컬 개발 기본값에서는 데모 민원 3건을 자동 생성하되, 테스트/운영 기본값에서는 비활성화한다.
- `mvn test`와 `mvn -DskipTests package` 통과를 확인했다.
- 패키징된 JAR가 로컬 PostgreSQL에 연결되고 8081 포트로 정상 기동하는 것을 확인했다. 확인 후 테스트용 Java 프로세스는 종료했다.
- Flyway V4 마이그레이션을 추가했다. 민원 접수번호/제목, 분석 민원 유형, `knowledge_document_chunks`, `rag_contexts.knowledge_document_chunk_id`를 포함한다.
- 로컬 PostgreSQL에서 V4 적용 후 현재 schema version 4와 Hibernate validate 정상 기동을 확인했다.
- DB 점검용 문서와 쿼리를 `docs/db-schema-review.md`, `docs/db-check-queries.sql`에 추가했다.

## 다음 개발 순서

1. 기존 `backend`를 참고용으로 둘지 삭제할지 결정
2. 프론트엔드 대시보드를 `egov-boot-web` API에 연결
3. 로컬 Mock 기준 통합 시나리오와 데모 데이터 보강
4. API 문서와 DBeaver 확인 쿼리 정리
5. AWS 실연동은 비용 검토 후 별도 단계에서 S3/Bedrock/OpenSearch 순서로 켠다
6. ECS 작업 정의, Secrets Manager, CloudWatch 설정은 실제 AWS 계정 값이 확정된 뒤 연결한다

## 주의사항

- 최종 산출물과 발표에서는 `egov-boot-web`을 기준 백엔드로 설명한다.
- `backend`는 eGovFrame 실행환경을 포함하지 않으므로 메인 백엔드로 설명하면 안 된다.
- API Key, DB 비밀번호, AWS Access Key 등은 코드와 Git에 직접 저장하지 않는다.
- 실제 LLM/Vector DB 호출은 비용이 발생하므로 개발 초반에는 Mock 구현을 유지한다.
