# eGovFrame 기반 민원 분석 및 RAG 공문 초안 시스템

전자정부 표준프레임워크(eGovFrame) 5.0 기반 백엔드를 중심으로 비정형 민원 데이터를 접수, 분류, 저장하고 향후 RAG 기반 공문 답변 초안 생성을 연동하기 위한 프로젝트입니다.

## 현재 기준

현재 메인 백엔드는 `egov-boot-web`입니다.

- 생성 방식: VS Code Extension `eGovFrame Initializr 5.0.5`
- 프레임워크: eGovFrame Boot Web 5.0, Spring Boot, Spring MVC
- 빌드 도구: Maven
- Java: 17
- 실행 포트: `8081`
- DB: PostgreSQL `complaintdb`

기존 `backend` 디렉터리는 Spring Initializr 기반 Gradle 프로젝트였으며, 전자정부프레임워크 필수 조건을 만족하기 위해 더 이상 메인 백엔드로 사용하지 않습니다. 필요한 민원 API, 도메인, 서비스 코드는 `egov-boot-web` 하위로 이식했습니다.

## 주요 디렉터리

```text
egov-boot-web/   # 메인 백엔드: eGovFrame 5.0 기반 Maven 프로젝트
ai-rag-engine/   # Python 로컬 AI/RAG 검증 엔진 및 지식문서 적재 스크립트
backend/         # 이전 Spring Boot/Gradle 백엔드. 참고용 또는 추후 정리 대상
docs/            # 산출물/문서 참고 영역
```

## 구현된 기능

- 민원 접수 API
- 민원 목록/단건 조회 API
- 민원 목록 필터링 및 페이지네이션
- 민원 분석 결과 조회 API
- RAG 근거 문맥 조회 API
- 답변 초안 생성/수정 API
- 첨부파일 등록/목록 API
- 민원 상태 변경 API
- 부서 목록 조회 API
- GeoJSON 조회 API
- JPA 기반 민원 저장
- PostgreSQL 연동
- Flyway 기반 DB 마이그레이션
- RAG 문서 청크 테이블
- API Key 인증 옵션
- API 감사 로그 저장
- Spring Security 기본 설정
- Actuator health endpoint
- Mock 기반 민원 분류/담당 부서 추론
- Python 기반 로컬 AI/RAG 검증 스크립트
- 지식문서 Markdown을 `knowledge_documents` 테이블에 적재하는 스크립트

현재 개발 기본값에서는 실제 외부 AI/AWS 서비스를 호출하지 않습니다. AI 분석, RAG 검색, 공문 초안 생성은 로컬 Mock/PostgreSQL 기반으로 동작하며, S3, Bedrock, OpenSearch Serverless는 명시적으로 설정을 켰을 때만 연결됩니다.

`ai-rag-engine`은 별도 Python 검증 도구입니다. OpenAI API를 직접 사용할 수 있으므로 `.env`에 API Key를 넣어야 하며, 기본 백엔드 실행에는 필요하지 않습니다.

## 주요 API

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

## 로컬 실행

PostgreSQL 컨테이너 또는 로컬 DB가 먼저 실행되어 있어야 합니다.

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn test
mvn spring-boot:run
```

헬스 체크:

```powershell
Invoke-WebRequest -Uri http://localhost:8081/actuator/health -UseBasicParsing
```

Python AI/RAG 엔진을 별도로 확인할 때:

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\ai-rag-engine
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
.venv\Scripts\python.exe test_db_connection.py
.venv\Scripts\python.exe insert_knowledge_documents.py
```

`main.py`는 OpenAI API를 호출하므로 비용이 발생할 수 있다.

## DB 기준

```text
Host: localhost
Port: 5432
Database: complaintdb
User: complaint_user
Password: complaint_pass
```

설정 파일:

```text
egov-boot-web/src/main/resources/application.properties
```

## 개발 방향

1. eGovFrame 기반 백엔드 구조를 기준으로 유지합니다.
2. 민원 접수, 분석, 담당 부서 분류, 답변 초안 생성/수정 흐름을 API 단위로 안정화합니다.
3. 개발 중에는 Mock 분석/RAG/초안 서비스와 로컬 파일 저장소를 기본값으로 유지합니다.
4. AWS 실연동은 비용 검토 후 별도 단계에서 필요한 기능만 명시적으로 켭니다.
5. PostgreSQL 업무 데이터와 문서/벡터 저장소를 분리해 확장합니다.
6. 운영 단계에서는 보안, 로깅, 예외 처리, 감사 추적, 배포 자동화를 강화합니다.
