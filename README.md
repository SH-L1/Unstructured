# eGovFrame 기반 민원 분석 및 RAG 공문 초안 시스템

전자정부 표준프레임워크(eGovFrame) 5.0 기반 Spring 애플리케이션을 중심으로, AWS의 저장소, AI 분석, RAG 검색, 운영 인프라를 연동하여 비정형 민원 데이터를 구조화하고 공문 답변 초안을 생성하는 시스템입니다.

웹 게시판 텍스트, 이미지, 첨부파일 등 다양한 형태의 민원 데이터를 수집하고, Amazon Bedrock 기반 LLM과 검색증강생성(RAG)을 활용해 담당자가 검토할 수 있는 JSON, GeoJSON, 공문 초안을 제공합니다.

## 핵심 목표

- 비정형 민원 데이터를 수집하고 원본 데이터를 안전하게 관리
- OCR, 이미지 분석, LLM 기반 의도 분석을 통한 민원 정보 구조화
- 위치 정보 기반 GeoJSON 생성 및 지도 기반 웹 대시보드 연동
- 법령, 조례, 민원 처리 매뉴얼, 과거 답변 사례 기반 RAG 검색
- 공공기관 업무 문맥에 맞는 공문 답변 초안 생성
- 담당 부서 라우팅, 긴급도 분류, 감정 상태 파악 등 민원 처리 보조

## 전체 아키텍처

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

## 주요 기능

### 민원 데이터 수집

- 웹 게시판 민원 텍스트 수집
- 이미지 및 첨부파일 업로드
- HTML 태그 제거 및 순수 텍스트 추출
- 원본 데이터와 처리 메타데이터 분리 관리

### AI 기반 민원 분석

- Amazon Textract 기반 OCR 및 문서 텍스트 추출
- Amazon Rekognition 기반 현장 이미지 분석 보조
- Spring AI 기반 Amazon Bedrock LLM 분석
- 민원 의도, 긴급도, 감정 상태, 위치 정보, 담당 부서 추출

### RAG 기반 지식 검색

- 법령, 조례, 민원 처리 매뉴얼, 답변 사례를 지식 베이스로 구성
- Amazon OpenSearch Serverless 기반 벡터 검색
- 민원 분석 결과와 핵심 키워드를 활용한 관련 문맥 검색
- 공문 초안 생성 시 참조 근거로 활용

### 공문 초안 생성

- 민원 원문, 구조화된 분석 결과, RAG 검색 결과를 프롬프트에 결합
- 공공기관 응대 문맥에 맞는 정중한 답변 초안 생성
- 적용 가능한 법적 근거와 참조 문서를 함께 제공
- 담당자 검토 및 수정 이력을 남길 수 있는 구조로 확장

### 웹 대시보드 및 지도 시각화

- 위치 정보가 포함된 민원을 GeoJSON으로 변환
- 지도 기반 마커 표시
- 마커 선택 시 민원 원문, 분석 결과, 공문 초안 조회

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 백엔드 프레임워크 | eGovFrame 5.0, Spring Boot, Spring MVC |
| AI 연동 | Spring AI, Amazon Bedrock |
| 파일 저장 | Amazon S3 |
| OCR/문서 인식 | Amazon Textract |
| 이미지 분석 | Amazon Rekognition |
| Vector DB/RAG | Amazon OpenSearch Serverless |
| 업무 데이터 저장 | Amazon RDS PostgreSQL |
| PostgreSQL 클라이언트 | DBeaver Community, psql |
| 배포 | Amazon ECS Fargate 또는 컨테이너 기반 배포 환경 |
| 로그/모니터링 | Amazon CloudWatch |
| 인증정보 관리 | AWS Secrets Manager |
| 권한 제어 | Spring Security, eGovFrame 보안 구조, AWS IAM Role |
| 시각화 연동 | REST API, GeoJSON, 지도 API, 웹 대시보드 |

## 민원 처리 흐름

1. 민원 원문, 이미지, 첨부파일을 접수합니다.
2. 원본 데이터는 Amazon S3에 저장합니다.
3. 이미지 또는 문서는 Amazon Textract로 텍스트를 추출합니다.
4. 현장 이미지는 Amazon Rekognition으로 주요 객체와 장면 정보를 분석합니다.
5. Spring AI 기반 LLM 체인을 통해 민원 의도, 긴급도, 감정 상태, 위치 정보, 담당 부서를 추출합니다.
6. 분석 결과를 JSON으로 저장하고, 위치 정보가 있는 경우 GeoJSON을 생성합니다.
7. OpenSearch Serverless에서 관련 법령, 조례, 매뉴얼, 답변 사례를 검색합니다.
8. 검색된 문맥과 민원 정보를 결합하여 Amazon Bedrock 기반 공문 초안을 생성합니다.
9. 생성 결과를 담당자 웹 대시보드와 지도 화면에 제공합니다.

## 대표 업무 시나리오

우선 구축 대상 민원 유형은 불법 투기 신고입니다.

1. 시민이 불법 투기 현장 사진과 신고 내용을 제출합니다.
2. 백엔드는 원본 이미지와 민원 텍스트를 S3에 저장합니다.
3. Textract와 Rekognition으로 이미지 내 텍스트, 객체, 현장 상태를 분석합니다.
4. Bedrock 기반 LLM이 신고 의도, 위치, 긴급도, 감정 상태, 담당 부서 후보를 추출합니다.
5. OpenSearch Serverless에서 폐기물 관리 관련 법령, 지자체 조례, 민원 응대 예시를 검색합니다.
6. 검색 근거와 민원 분석 결과를 결합하여 담당자 검토용 공문 초안을 생성합니다.
7. 웹 대시보드에서 지도 마커, 원문, 분석 결과, 참조 근거, 공문 초안을 함께 확인합니다.

## 백엔드 데이터 모델 예시

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

## API 구성 방향

백엔드는 웹 대시보드를 위해 REST API 중심으로 구성합니다.

- `POST /api/complaints`: 민원 텍스트, 이미지, 첨부파일 접수
- `GET /api/complaints`: 민원 목록 및 처리 상태 조회
- `GET /api/complaints/{id}`: 민원 상세 조회
- `GET /api/complaints/{id}/analysis`: LLM 분석 결과 조회
- `GET /api/complaints/{id}/geojson`: 위치 기반 GeoJSON 조회
- `GET /api/complaints/{id}/rag-contexts`: RAG 참조 문서 조회
- `GET /api/complaints/{id}/draft`: 공문 초안 조회
- `PUT /api/complaints/{id}/draft`: 담당자 수정 초안 저장

## 운영 및 보안 방향

- 원본 민원 데이터와 첨부파일은 S3에 저장하고 접근 권한을 제한합니다.
- API Key, DB 접속 정보, 외부 서비스 인증정보는 AWS Secrets Manager에서 관리합니다.
- 담당자 계정과 역할은 Spring Security 및 eGovFrame 보안 구조를 기반으로 관리합니다.
- AWS 서비스 접근은 IAM Role 기반 최소 권한 원칙을 적용합니다.
- LLM 생성 결과는 담당자 검토를 거쳐 확정하는 구조로 운영합니다.
- RAG 참조 문서를 함께 저장하여 공문 초안의 근거 추적성을 확보합니다.
- CloudWatch를 통해 애플리케이션 로그, AI 서비스 호출 오류, API 실패를 모니터링합니다.

## 관련 문서

- [PLAN.md](./PLAN.md): 상세 구축 계획 및 아키텍처 정리
