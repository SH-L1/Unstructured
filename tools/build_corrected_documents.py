from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Inches, Pt


OUT_DIR = Path("docs/corrected-documents")
SYSTEM = "eGovFrame 5.0 기반 지능형 민원 대응 시스템"
SUBSYSTEM = "민원 분석 및 RAG 공문 초안 생성"
DATE = "2026-06-03"


def style_doc(doc: Document) -> None:
    section = doc.sections[0]
    section.top_margin = Inches(0.7)
    section.bottom_margin = Inches(0.7)
    section.left_margin = Inches(0.7)
    section.right_margin = Inches(0.7)
    normal = doc.styles["Normal"]
    normal.font.name = "Malgun Gothic"
    normal.font.size = Pt(9)
    for style_name in ["Heading 1", "Heading 2", "Heading 3"]:
        style = doc.styles[style_name]
        style.font.name = "Malgun Gothic"
        style.font.bold = True


def add_title(doc: Document, title: str) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(title)
    run.bold = True
    run.font.size = Pt(17)
    doc.add_paragraph()
    add_table(
        doc,
        ["구분", "내용", "구분", "내용"],
        [
            ["시스템명", SYSTEM, "서브시스템명", SUBSYSTEM],
            ["단계명", "분석/설계 보정", "작성일자", DATE],
            ["버전", "v1.1", "수정 기준", "egov-boot-web 실제 구현, Flyway V1~V4, Controller/DTO/SmokeTest"],
        ],
    )


def add_table(doc: Document, headers, rows):
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    hdr = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = str(h)
        for p in hdr[i].paragraphs:
            for r in p.runs:
                r.bold = True
    for row in rows:
        cells = table.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = "" if value is None else str(value)
    doc.add_paragraph()
    return table


def add_bullets(doc: Document, items):
    for item in items:
        doc.add_paragraph(item, style="List Bullet")


def db_doc() -> Document:
    doc = Document()
    style_doc(doc)
    add_title(doc, "데이터베이스 설계서")
    doc.add_heading("1. 데이터베이스 개요", level=1)
    add_table(
        doc,
        ["DB ID", "DB명", "DBMS", "연결 기준", "비고"],
        [["DB-001", "complaintdb", "PostgreSQL", "localhost:5432, complaint_user", "Flyway V1~V4, JPA validate 기준"]],
    )
    doc.add_heading("2. 테이블 목록", level=1)
    add_table(
        doc,
        ["테이블 ID", "테이블명", "주요 역할", "관련 구현"],
        [
            ["TB-001", "complaints", "민원 원문, 접수번호, 채널, 위치, 처리 상태 저장", "Complaint"],
            ["TB-002", "departments", "담당 부서 seed 및 분류 결과 참조", "Department"],
            ["TB-003", "complaint_analysis", "민원 분석 결과, 유형, 긴급도, 감정, GeoJSON, JSON 저장", "ComplaintAnalysis"],
            ["TB-004", "knowledge_documents", "법령/조례/매뉴얼 지식문서 원본 저장", "KnowledgeDocument"],
            ["TB-005", "knowledge_document_chunks", "지식문서 청크 및 키워드 기반 RAG 검색 단위 저장", "KnowledgeDocumentChunk"],
            ["TB-006", "rag_contexts", "공문 초안에 사용된 RAG 근거와 점수 저장", "RagContext"],
            ["TB-007", "official_drafts", "공문 초안 본문, 모델명, 초안 상태 저장", "OfficialDraft"],
            ["TB-008", "draft_revisions", "공문 초안 수정 전/후 이력 저장", "DraftRevision"],
            ["TB-009", "complaint_attachments", "민원 첨부파일 메타데이터와 저장 키 저장", "ComplaintAttachment"],
            ["TB-010", "api_users", "API Key 인증 사용자와 해시 저장", "ApiUser"],
            ["TB-011", "audit_logs", "/api/** 요청 감사 로그 저장", "AuditLog"],
        ],
    )
    doc.add_heading("3. 핵심 테이블 상세", level=1)
    table_defs = {
        "complaints": [
            ["id", "uuid", "Y", "PK", "", "민원 식별자"],
            ["receipt_number", "varchar(60)", "Y", "UK", "idx_complaints_receipt_number", "CIV- 형식 접수번호"],
            ["title", "varchar(200)", "", "", "", "원문 기반 제목"],
            ["source_channel", "varchar(50)", "Y", "", "", "WEB, MOBILE, CALL_CENTER, VISIT, EMAIL, OPEN_API"],
            ["raw_text", "text", "Y", "", "", "비정형 민원 원문"],
            ["location_text", "varchar(500)", "", "", "", "위치 텍스트"],
            ["status", "varchar(30)", "Y", "", "idx_complaints_status_created_at", "RECEIVED, ANALYZED, DRAFT_GENERATED 등"],
            ["created_at/updated_at", "timestamp", "Y", "", "", "생성/수정 시각"],
        ],
        "complaint_analysis": [
            ["id", "bigint identity", "Y", "PK", "", "분석 식별자"],
            ["complaint_id", "uuid", "Y", "FK/UK", "", "complaints(id) 1:1 참조"],
            ["intent", "varchar(100)", "Y", "", "", "분석 의도"],
            ["complaint_type", "varchar(50)", "Y", "", "idx_complaint_analysis_type_urgency", "ILLEGAL_DUMPING, ROAD_DAMAGE, HAZARDOUS_MATERIAL 등"],
            ["urgency", "varchar(30)", "Y", "", "idx_complaint_analysis_type_urgency", "LOW, NORMAL, HIGH, EMERGENCY"],
            ["sentiment", "varchar(30)", "Y", "", "", "NEUTRAL, DISCOMFORT, ANGER, URGENT"],
            ["department_id", "bigint", "Y", "FK", "", "departments(id) 참조"],
            ["location_text", "varchar(500)", "", "", "", "분석 위치 텍스트"],
            ["geo_json", "text", "", "", "", "GeoJSON 응답"],
            ["analysis_json", "text", "Y", "", "", "LLM/룰 기반 분석 원문 JSON"],
        ],
        "knowledge_documents": [
            ["id", "bigint identity", "Y", "PK", "", "지식문서 식별자"],
            ["document_type", "varchar(30)", "Y", "", "", "LAW, ORDINANCE, MANUAL, CASE"],
            ["title", "varchar(200)", "Y", "", "", "문서 제목"],
            ["source_name/source_url", "varchar", "Y/N", "", "", "출처명/URL"],
            ["content", "text", "Y", "", "", "문서 본문"],
            ["keywords", "varchar(500)", "Y", "", "", "검색 키워드"],
            ["legal_basis", "varchar(500)", "", "", "", "법령/조례/매뉴얼 근거"],
        ],
        "knowledge_document_chunks": [
            ["id", "bigint identity", "Y", "PK", "", "청크 식별자"],
            ["knowledge_document_id", "bigint", "Y", "FK", "idx_knowledge_document_chunks_document_id", "knowledge_documents(id) 참조"],
            ["chunk_index", "integer", "Y", "UK", "", "문서 내 청크 순번"],
            ["content", "text", "Y", "", "", "청크 본문"],
            ["keywords", "varchar(500)", "Y", "", "", "청크 검색 키워드"],
            ["legal_basis", "varchar(500)", "", "", "", "청크별 근거"],
            ["embedding_id/token_count", "varchar/integer", "", "", "", "향후 벡터 검색 확장 필드"],
            ["active", "boolean", "Y", "", "idx_knowledge_document_chunks_active", "활성 여부"],
        ],
        "rag_contexts": [
            ["id", "bigint identity", "Y", "PK", "", "RAG 근거 식별자"],
            ["complaint_id", "uuid", "Y", "FK", "idx_rag_contexts_complaint_id_score", "complaints(id) 참조"],
            ["official_draft_id", "bigint", "", "FK", "idx_rag_contexts_official_draft_id_score", "official_drafts(id) 참조"],
            ["knowledge_document_id", "bigint", "Y", "FK", "", "knowledge_documents(id) 참조"],
            ["knowledge_document_chunk_id", "bigint", "", "FK", "idx_rag_contexts_knowledge_document_chunk_id", "knowledge_document_chunks(id) 참조"],
            ["legal_basis", "varchar(500)", "", "", "", "근거명"],
            ["content_snippet", "text", "Y", "", "", "근거 발췌"],
            ["score", "float(53)", "Y", "", "", "검색 유사도/우선순위 점수"],
        ],
        "official_drafts": [
            ["id", "bigint identity", "Y", "PK", "", "초안 식별자"],
            ["complaint_id", "uuid", "Y", "FK", "idx_official_drafts_complaint_id_created_at", "complaints(id) 참조"],
            ["draft_text", "text", "Y", "", "", "공문 초안 본문"],
            ["model_name", "varchar(100)", "Y", "", "", "openai:gpt-4o-mini 또는 mock 모델명"],
            ["status", "varchar(30)", "Y", "", "", "DRAFT, REVISED, APPROVED, REJECTED"],
        ],
        "draft_revisions": [
            ["id", "bigint identity", "Y", "PK", "", "수정 이력 식별자"],
            ["official_draft_id", "bigint", "Y", "FK", "", "official_drafts(id) 참조"],
            ["before_text", "text", "Y", "", "", "수정 전 본문"],
            ["after_text", "text", "Y", "", "", "수정 후 본문"],
            ["revised_by", "varchar(100)", "Y", "", "", "수정자"],
        ],
        "complaint_attachments": [
            ["id", "uuid", "Y", "PK", "", "첨부파일 식별자"],
            ["complaint_id", "uuid", "Y", "FK", "idx_complaint_attachments_complaint_id", "complaints(id) 참조"],
            ["original_filename", "varchar(255)", "Y", "", "", "원본 파일명"],
            ["content_type", "varchar(100)", "", "", "", "MIME 타입"],
            ["size", "bigint", "Y", "", "", "파일 크기"],
            ["storage_key", "varchar(500)", "Y", "", "", "Local/S3 저장 키"],
        ],
        "api_users": [
            ["id", "bigint identity", "Y", "PK", "", "API 사용자 식별자"],
            ["username", "varchar(100)", "Y", "UK", "", "사용자명"],
            ["api_key_hash", "varchar(128)", "Y", "UK", "idx_api_users_api_key_hash_active", "API Key SHA-256 해시"],
            ["role", "varchar(30)", "Y", "", "", "ADMIN, OFFICER, VIEWER"],
            ["active", "boolean", "Y", "", "idx_api_users_api_key_hash_active", "활성 여부"],
        ],
        "audit_logs": [
            ["id", "bigint identity", "Y", "PK", "", "감사 로그 식별자"],
            ["method", "varchar(20)", "Y", "", "", "HTTP method"],
            ["path", "varchar(500)", "Y", "", "idx_audit_logs_path_created_at", "요청 경로"],
            ["actor", "varchar(100)", "", "", "", "API Key 인증 사용자"],
            ["client_ip", "varchar(100)", "", "", "", "클라이언트 IP"],
            ["status_code", "integer", "Y", "", "", "응답 코드"],
            ["duration_ms", "bigint", "Y", "", "", "처리 시간"],
            ["created_at", "timestamp", "Y", "", "idx_audit_logs_created_at", "생성 시각"],
        ],
    }
    for name, rows in table_defs.items():
        doc.add_heading(name, level=2)
        add_table(doc, ["컬럼ID", "타입", "Not Null", "PK/FK/UK", "인덱스", "설명"], rows)
    doc.add_heading("4. 정합성 보정 사항", level=1)
    add_bullets(
        doc,
        [
            "공문 초안 저장 구조는 official_drafts와 draft_revisions의 2계층 구조를 기준으로 한다.",
            "RAG 지식 저장 구조는 knowledge_documents와 knowledge_document_chunks의 2계층 구조를 기준으로 한다.",
            "민원 ID는 SERIAL이 아니라 UUID이며, 접수번호는 receipt_number로 별도 관리한다.",
            "분석 결과는 intent, complaint_type, urgency, sentiment, department_id, analysis_json 중심으로 관리한다.",
        ],
    )
    return doc


def api_rows():
    return [
        ["POST", "/api/complaints", "민원 등록", "ApiResponse<ComplaintResponse>"],
        ["GET", "/api/complaints?status=&department=&urgency=&page=&size=", "목록/필터/페이징 조회", "ApiResponse<PageResponse<ComplaintResponse>>"],
        ["GET", "/api/complaints/{id}", "민원 상세 조회", "ApiResponse<ComplaintResponse>"],
        ["PATCH", "/api/complaints/{id}/status", "민원 상태 변경", "ApiResponse<ComplaintResponse>"],
        ["GET", "/api/complaints/{id}/analysis", "분석 결과 조회/생성", "ApiResponse<ComplaintAnalysisResponse>"],
        ["GET", "/api/complaints/{id}/draft", "공문 초안 조회/생성", "ApiResponse<DraftResponse>"],
        ["PUT", "/api/complaints/{id}/draft", "공문 초안 수정", "ApiResponse<DraftResponse>"],
        ["GET", "/api/complaints/{id}/rag-contexts", "RAG 근거 조회", "ApiResponse<List<RagContextResponse>>"],
        ["GET", "/api/complaints/{id}/geojson", "GeoJSON 조회", "ApiResponse<String>"],
        ["POST", "/api/complaints/{id}/attachments", "첨부파일 등록", "ApiResponse<AttachmentResponse>"],
        ["GET", "/api/complaints/{id}/attachments", "첨부파일 목록 조회", "ApiResponse<List<AttachmentResponse>>"],
        ["GET", "/api/complaints/{id}/attachments/{attachmentId}", "첨부파일 다운로드", "byte[]"],
        ["DELETE", "/api/complaints/{id}/attachments/{attachmentId}", "첨부파일 삭제", "204 No Content"],
        ["GET", "/api/departments", "부서 목록 조회", "ApiResponse<List<DepartmentResponse>>"],
        ["GET", "/dashboard", "대시보드 정적 화면", "index.html forward"],
        ["GET", "/actuator/health", "운영 상태 확인", "health"],
        ["GET", "/v3/api-docs, /swagger-ui/index.html", "OpenAPI 문서", "Swagger UI"],
    ]


def architecture_doc() -> Document:
    doc = Document()
    style_doc(doc)
    add_title(doc, "아키텍처 설계서")
    doc.add_heading("1. 소프트웨어 아키텍처", level=1)
    add_table(
        doc,
        ["계층", "구성요소", "기술/환경", "역할"],
        [
            ["클라이언트", "웹 대시보드, Swagger UI", "/dashboard, /swagger-ui/index.html", "민원 원문 입력, Agent Loop 진행 상태, 분석 JSON, RAG 근거, 공문 초안 확인"],
            ["API", "ComplaintController, DepartmentController, DashboardController", "eGovFrame Boot Web 5.0, Spring MVC", "REST API, 대시보드 라우팅, 부서 조회 제공"],
            ["서비스", "ComplaintService, DepartmentRoutingService", "Spring Service, Transaction", "민원 접수/조회/상태 변경, 분석, 초안, 첨부파일, RAG 근거 흐름 조정"],
            ["AI 분석", "OpenAiComplaintAnalysisClient, BedrockComplaintAnalysisClient, Mock/Rule fallback", "OpenAI 기본, Bedrock 선택", "민원 유형, 긴급도, 감정, 담당 부서, 분석 JSON 생성"],
            ["초안 생성", "OpenAiDraftGenerationClient, BedrockDraftGenerationClient, fallback", "OpenAI gpt-4o-mini 기본", "RAG 근거에 기반한 공문 초안 생성"],
            ["RAG 검색", "PostgresKnowledgeDocumentSearchService, OpenSearchKnowledgeDocumentSearchService", "PostgreSQL 키워드 검색 기본, OpenSearch 선택", "knowledge_documents/chunks 검색 후 rag_contexts 저장"],
            ["저장소", "JPA Repository, Flyway", "PostgreSQL complaintdb, V1~V4", "정규화된 민원/분석/초안/청크/감사/인증 데이터 저장"],
            ["파일", "LocalFileStorageService, S3FileStorageService", "Local 기본, S3 선택", "첨부파일 저장/다운로드/삭제"],
            ["보안/운영", "SecurityConfig, ApiKeyAuthenticationFilter, ApiAuditLogFilter, Actuator", "API Key 선택 활성화, audit 기본 활성화", "X-API-Key 인증, /api/** 감사 로그, health 제공"],
        ],
    )
    doc.add_heading("2. 시스템 아키텍처 흐름", level=1)
    add_table(
        doc,
        ["순번", "흐름", "구현 근거"],
        [
            ["1", "사용자가 /dashboard에서 sourceChannel, rawText, locationText를 입력한다.", "static/dashboard/index.html, app.js"],
            ["2", "POST /api/complaints로 민원을 저장하고 receiptNumber를 발급한다.", "ComplaintController.create, ComplaintService.create"],
            ["3", "GET /analysis 호출 시 OpenAI/Bedrock/룰 기반 분석으로 complaint_analysis를 생성한다.", "ComplaintService.analyze"],
            ["4", "GET /draft 호출 시 지식문서 검색 후 official_drafts와 rag_contexts를 저장한다.", "ComplaintService.generateDraft"],
            ["5", "GET /rag-contexts와 화면 렌더링으로 근거 문서 목록을 확인한다.", "RagContextResponse, app.js renderResult"],
            ["6", "PUT /draft로 초안을 수정하면 draft_revisions에 변경 이력이 저장된다.", "ComplaintService.updateDraft"],
            ["7", "첨부파일 API는 Local 기본, S3 선택 저장소를 사용한다.", "FileStorageService 구현체"],
            ["8", "API Key 인증과 감사 로그는 설정값으로 활성화되며 api_users/audit_logs를 사용한다.", "ApiKeyAuthenticationFilter, ApiAuditLogFilter"],
        ],
    )
    doc.add_heading("3. API 목록", level=1)
    add_table(doc, ["Method", "Path", "기능", "응답"], api_rows())
    doc.add_heading("4. 아키텍처 요구사항 및 구현방안", level=1)
    add_table(
        doc,
        ["요구사항 ID", "요구사항 내용", "구현방안"],
        [
            ["COM-013", "메인 백엔드는 eGovFrame Boot Web 5.0 기반이어야 함", "egov-boot-web/pom.xml의 egovframe-boot-starter-parent 5.0.0, Java 17, Maven 기준"],
            ["COM-011", "PostgreSQL complaintdb 기준이어야 함", "application.properties의 jdbc:postgresql://localhost:5432/complaintdb와 Flyway V1~V4"],
            ["COM-025", "현재 RAG는 pgvector가 아니라 키워드 기반 검색 방식임", "PostgresKnowledgeDocumentSearchService가 knowledge_documents/chunks를 검색"],
            ["COM-027", "외부 Key를 소스에 저장하지 않아야 함", "OPENAI_API_KEY, API_KEY_VALUE 등 환경변수/프로퍼티 주입"],
            ["REQ-029", "RAG 근거에 없는 법령/조항을 임의 생성하지 않아야 함", "초안 생성 시 KnowledgeDocument legalBasis와 rag_contexts references를 근거로 사용"],
            ["SEC-001", "API Key 인증과 감사 추적을 지원해야 함", "api_users, ApiKeyAuthenticationFilter, audit_logs, ApiAuditLogFilter"],
            ["FILE-001", "첨부파일 저장 구조를 지원해야 함", "complaint_attachments, LocalFileStorageService, S3FileStorageService"],
        ],
    )
    doc.add_heading("5. 보정된 표현 기준", level=1)
    add_bullets(
        doc,
        [
            "본 시스템은 AWS 필수 사용 시스템이 아니라 OpenAI 기본 연동 및 AWS S3/Bedrock/OpenSearch 선택 연동이 가능한 eGovFrame 시스템으로 기술한다.",
            "ai-rag-engine은 백엔드 필수 런타임이 아니라 지식문서 적재 및 로컬 AI/RAG 검증 보조 도구로 분류한다.",
            "최종 제출 기준 백엔드는 backend 디렉터리가 아니라 egov-boot-web 디렉터리이다.",
        ],
    )
    return doc


def ui_doc() -> Document:
    doc = Document()
    style_doc(doc)
    add_title(doc, "사용자 인터페이스 설계서")
    doc.add_heading("1. 사용자 인터페이스 구조도", level=1)
    add_table(
        doc,
        ["업무 영역", "화면/인터페이스", "주요 구성", "비고"],
        [
            ["웹 대시보드", "/dashboard", "민원 원문 입력, 채널/위치/API Key 입력, Agent Loop 진행 표시, 결과 패널", "static/dashboard/index.html, app.js"],
            ["결과 조회", "대시보드 결과 패널", "접수번호, 민원 유형, 담당 부서, 긴급도, 처리 상태, 분석 JSON, RAG 근거, 공문 초안", "renderResult()"],
            ["OpenAPI", "/swagger-ui/index.html", "민원 API, 부서 API, 첨부파일 API, 초안 수정 API 확인", "springdoc-openapi"],
            ["운영 상태", "/actuator/health", "서버 상태 확인", "Actuator"],
        ],
    )
    doc.add_heading("2. 화면 목록", level=1)
    add_table(
        doc,
        ["화면 ID", "화면명", "관련 유스케이스", "실제 구현 여부"],
        [
            ["SC-001", "민원 분석 Agent Loop 결과 화면", "UC-001~UC-005", "대시보드 결과 패널 구현"],
            ["SC-002", "민원 원문 입력 및 분석 시작", "UC-001", "대시보드 입력 패널 구현"],
            ["SC-003", "분석 JSON 확인", "UC-002, UC-005", "대시보드 결과 하위 섹션 구현"],
            ["SC-004", "RAG 근거 및 공문 초안 확인", "UC-003, UC-004, UC-005", "대시보드 결과 하위 섹션 구현"],
            ["SC-005", "생성 결과 요약", "UC-004, UC-005", "metric-grid 구현"],
            ["SC-006", "OpenAPI/Swagger API 문서", "UC-001~UC-010", "Swagger UI 구현"],
            ["SC-007", "API 수준 첨부파일/초안 수정 검증", "UC-006, UC-008", "대시보드 UI는 미구현, Swagger/API로 검증"],
        ],
    )
    doc.add_heading("3. 출력물 목록", level=1)
    add_table(
        doc,
        ["출력물 ID", "출력물명", "응답 DTO/필드", "관련 유스케이스"],
        [
            ["OUT-001", "민원 접수 결과", "ComplaintResponse.id, receiptNumber, status", "UC-001"],
            ["OUT-002", "민원 분석 JSON", "ComplaintAnalysisResponse.analysisJson", "UC-002"],
            ["OUT-003", "RAG 근거 문서 목록", "RagContextResponse.title, legalBasis, contentSnippet, score", "UC-003"],
            ["OUT-004", "공문 초안", "DraftResponse.draftText, modelName, status", "UC-004, UC-008"],
            ["OUT-005", "첨부파일 메타데이터", "AttachmentResponse", "UC-006"],
            ["OUT-006", "부서 목록", "DepartmentResponse", "UC-007"],
        ],
    )
    doc.add_heading("4. 화면 상세 설계", level=1)
    screens = [
        (
            "SC-002 민원 원문 입력 및 분석 시작",
            [
                ["민원 원문", "rawText", "TEXT", "I/E", "필수, 최대 10000자"],
                ["접수 채널", "sourceChannel", "VARCHAR(50)", "I/E", "WEB, MOBILE, CALL_CENTER 등"],
                ["위치 정보", "locationText", "VARCHAR(500)", "I/E", "선택, 최대 500자"],
                ["API Key", "apiKey", "PASSWORD", "I/E", "선택, 입력 시 X-API-Key 헤더로 전송"],
                ["분석 시작", "processButton", "BUTTON", "I/E", "rawText 존재 시 POST /api/complaints 실행"],
            ],
            "runPipeline()이 민원 등록 후 /analysis, /draft, /rag-contexts를 순차 호출한다.",
        ),
        (
            "SC-001/SC-005 결과 요약",
            [
                ["접수번호", "receiptNumber", "VARCHAR(60)", "O/RO", "ComplaintResponse.receiptNumber"],
                ["민원 유형", "complaintType", "VARCHAR(50)", "O/RO", "ComplaintAnalysisResponse.complaintType"],
                ["담당 부서", "department", "VARCHAR(100)", "O/RO", "departmentCode와 department 표시"],
                ["긴급도", "urgency", "VARCHAR(30)", "O/RO", "LOW/NORMAL/HIGH/EMERGENCY"],
                ["처리 상태", "complaintStatus", "VARCHAR(30)", "O/RO", "ComplaintStatus 또는 DraftStatus"],
                ["접수 채널", "sourceChannelResult", "VARCHAR(50)", "O/RO", "ComplaintResponse.sourceChannel"],
            ],
            "서버 응답을 화면 표시용 한글 라벨로 변환하되 원본 API 값은 그대로 유지한다.",
        ),
        (
            "SC-003 분석 JSON 확인",
            [
                ["분석 JSON", "analysisJson", "TEXT", "O/RO", "intent, complaintType, urgency, sentiment, departmentCode 포함"],
                ["GeoJSON", "geoJson", "TEXT", "O/RO", "API 수준으로 /geojson 제공, 대시보드 상세 표시는 현재 제외"],
            ],
            "분석 결과는 complaint_analysis 테이블과 ComplaintAnalysisResponse 기준이다.",
        ),
        (
            "SC-004 RAG 근거 및 공문 초안 확인",
            [
                ["RAG 근거", "ragList", "LIST", "O/RO", "title, legalBasis, contentSnippet, score"],
                ["공문 초안", "draftText", "TEXT", "O/RO", "DraftResponse.draftText"],
                ["API 상세 보기", "detailLink", "LINK", "O/RO", "/api/complaints/{id}"],
            ],
            "초안 수정 PUT /draft와 첨부파일 API는 현재 대시보드 편집 UI가 아니라 Swagger/API 통합시험 범위이다.",
        ),
        (
            "SC-006 OpenAPI/Swagger API 문서",
            [
                ["OpenAPI 제목", "apiTitle", "TEXT", "O/RO", "Civil Complaint API"],
                ["API 경로 목록", "apiPaths", "LIST", "O/RO", "/api/complaints, /api/departments, attachments 등"],
            ],
            "브라우저에서 /swagger-ui/index.html로 접근해 API 요청/응답 구조를 확인한다.",
        ),
    ]
    for title, rows, desc in screens:
        doc.add_heading(title, level=2)
        add_table(doc, ["항목명", "컨트롤명", "타입", "속성", "Validation/Mapping"], rows)
        doc.add_paragraph(desc)
    return doc


def usecase_doc() -> Document:
    doc = Document()
    style_doc(doc)
    add_title(doc, "유스케이스 명세서")
    doc.add_heading("1. 서브시스템 목록", level=1)
    add_table(
        doc,
        ["서브시스템 ID", "서브시스템명", "설명"],
        [
            ["SS-001", "클라이언트 계층", "/dashboard 대시보드와 Swagger UI"],
            ["SS-002", "eGovFrame 백엔드", "egov-boot-web, Controller-Service-Repository, Java 17, Maven"],
            ["SS-003", "AI 분석/초안 서비스", "OpenAI 기본, Bedrock 선택, 룰/Mock fallback"],
            ["SS-004", "RAG 검색 서비스", "PostgreSQL knowledge_documents/chunks 키워드 검색, OpenSearch 선택"],
            ["SS-005", "데이터베이스", "PostgreSQL complaintdb, Flyway V1~V4"],
            ["SS-006", "파일 저장소", "Local 기본, S3 선택"],
            ["SS-007", "보안/운영", "API Key 인증, 감사 로그, Actuator, OpenAPI"],
            ["SS-008", "보조 Python 도구", "ai-rag-engine 지식문서 적재 및 로컬 검증 도구, 백엔드 필수 런타임 아님"],
        ],
    )
    doc.add_heading("2. 유스케이스 목록", level=1)
    ucs = [
        ["UC-001", "민원 등록", "sourceChannel, rawText, locationText를 받아 complaints에 저장하고 receiptNumber를 발급", "POST /api/complaints"],
        ["UC-002", "민원 분석 결과 조회/생성", "민원 원문을 분석하여 complaint_analysis 생성 또는 기존 결과 조회", "GET /api/complaints/{id}/analysis"],
        ["UC-003", "RAG 근거 검색", "knowledge_documents/chunks 검색 후 rag_contexts에 근거 저장/조회", "GET /api/complaints/{id}/rag-contexts"],
        ["UC-004", "공문 초안 생성", "분석 결과와 RAG 근거를 기반으로 official_drafts 생성", "GET /api/complaints/{id}/draft"],
        ["UC-005", "대시보드 검토", "대시보드에서 접수/분석/RAG/초안 결과 확인", "/dashboard"],
        ["UC-006", "첨부파일 관리", "민원 첨부파일 등록, 목록 조회, 다운로드, 삭제", "POST/GET/DELETE attachments"],
        ["UC-007", "부서 목록 조회", "seed 부서 5건 조회 및 분류 결과 확인", "GET /api/departments"],
        ["UC-008", "공문 초안 수정", "초안 본문 수정 후 draft_revisions에 이력 저장", "PUT /api/complaints/{id}/draft"],
        ["UC-009", "상태 변경 및 목록 필터", "민원 상태 변경, 상태/부서/긴급도 기준 목록 조회", "PATCH /status, GET /api/complaints"],
        ["UC-010", "운영/보안 검증", "health, Swagger, API Key 인증, 감사 로그 확인", "/actuator/health, /swagger-ui, api_users, audit_logs"],
    ]
    add_table(doc, ["UC ID", "유스케이스명", "설명", "주요 구현"], ucs)
    doc.add_heading("3. 액터 목록", level=1)
    add_table(
        doc,
        ["액터 ID", "액터명", "유형", "설명"],
        [
            ["ACT-001", "담당 공무원", "주요", "대시보드에서 민원 원문 입력, 분석 결과와 초안 검토"],
            ["ACT-002", "백엔드 시스템", "보조", "REST API, 업무 로직, DB 접근, 파일 저장, 인증/감사 처리"],
            ["ACT-003", "OpenAI API", "보조", "gpt-4o-mini 기반 분석/초안 생성, API Key 설정 시 사용"],
            ["ACT-004", "PostgreSQL", "보조", "민원, 분석, 지식문서, RAG 근거, 초안, 첨부, 감사/인증 저장"],
            ["ACT-005", "파일 저장소", "보조", "Local 또는 S3 첨부파일 저장"],
            ["ACT-006", "개발자/운영자", "주요", "Swagger, health, API Key, 감사 로그, 테스트 확인"],
        ],
    )
    doc.add_heading("4. 유스케이스 기술서", level=1)
    details = {
        "UC-001 민원 등록": [
            "전제조건: egov-boot-web가 8081 포트에서 실행되고 PostgreSQL complaintdb가 연결되어야 한다.",
            "기본 흐름: 담당 공무원이 rawText/sourceChannel/locationText 입력 -> POST /api/complaints -> ComplaintService 저장 -> complaints.id와 receipt_number 생성 -> ApiResponse<ComplaintResponse> 반환.",
            "종료조건: status=RECEIVED 상태의 민원이 목록/상세 조회 가능하다.",
            "예외: rawText 공백은 validation 오류로 처리한다.",
        ],
        "UC-002 민원 분석 결과 조회/생성": [
            "전제조건: complaints.id가 존재해야 한다.",
            "기본 흐름: GET /api/complaints/{id}/analysis -> 기존 complaint_analysis 조회, 없으면 OpenAI/Bedrock/룰 기반 분석 생성 -> intent, complaintType, urgency, sentiment, departmentCode, geoJson, analysisJson 반환.",
            "종료조건: complaint_analysis가 저장되고 complaints.status가 ANALYZED로 전환된다.",
            "주의: 구현된 API는 POST /analyze가 아니라 GET /analysis이다.",
        ],
        "UC-003 RAG 근거 검색": [
            "전제조건: knowledge_documents와 knowledge_document_chunks seed 데이터가 존재해야 한다.",
            "기본 흐름: 초안 생성 과정에서 PostgresKnowledgeDocumentSearchService가 분석 결과 기반 키워드 검색 -> rag_contexts 저장 -> GET /rag-contexts로 근거 조회.",
            "종료조건: RagContextResponse 목록에 documentId, title, documentType, legalBasis, contentSnippet, score가 포함된다.",
        ],
        "UC-004 공문 초안 생성": [
            "전제조건: 민원과 분석 결과가 존재하거나 생성 가능해야 한다.",
            "기본 흐름: GET /draft -> 분석 생성/조회 -> RAG 검색 -> OpenAI/Bedrock/fallback 초안 생성 -> official_drafts 저장 -> rag_contexts 연결.",
            "종료조건: DraftResponse.status=DRAFT와 draftText, references가 반환된다.",
        ],
        "UC-005 대시보드 검토": [
            "전제조건: /dashboard 정적 화면이 접근 가능해야 한다.",
            "기본 흐름: app.js runPipeline()이 민원 등록, 분석, 초안, RAG 조회를 순차 실행하고 renderResult()가 결과를 표시한다.",
            "종료조건: 접수번호, 유형, 부서, 긴급도, 상태, 분석 JSON, RAG 근거, 공문 초안이 화면에 표시된다.",
        ],
        "UC-006 첨부파일 관리": [
            "전제조건: 민원 ID와 Local 또는 S3 저장소 설정이 존재해야 한다.",
            "기본 흐름: POST attachments로 저장 -> GET attachments로 목록 조회 -> GET attachmentId로 다운로드 -> DELETE attachmentId로 삭제.",
            "종료조건: complaint_attachments 메타데이터와 저장소 파일이 일관되게 생성/삭제된다.",
        ],
        "UC-007 부서 목록 조회": [
            "전제조건: ComplaintSeedDataConfig가 부서 seed를 적재해야 한다.",
            "기본 흐름: GET /api/departments -> RESOURCE_RECYCLING, ROAD, TRAFFIC, CIVIL_AFFAIRS, SAFETY_CONTROL 반환.",
        ],
        "UC-008 공문 초안 수정": [
            "전제조건: official_drafts가 존재하거나 generateDraft로 생성 가능해야 한다.",
            "기본 흐름: PUT /draft with draftText -> 기존 초안 revise -> draft_revisions에 before/after 저장 -> DraftResponse.status=REVISED 반환.",
            "주의: 현재 대시보드 편집 UI는 없으며 Swagger/API 수준 기능이다.",
        ],
        "UC-009 상태 변경 및 목록 필터": [
            "전제조건: 민원 ID가 존재해야 한다.",
            "기본 흐름: PATCH /status로 RECEIVED/IN_PROGRESS/COMPLETED 등 변경, GET /api/complaints에서 status/department/urgency/page/size 조건으로 조회.",
        ],
        "UC-010 운영/보안 검증": [
            "전제조건: 설정값에 따라 app.security.api-key.enabled와 app.audit.enabled가 활성화될 수 있다.",
            "기본 흐름: health/Swagger 접근 확인, API Key 미포함/포함 요청 검증, audit_logs 저장 확인.",
            "주의: 개발 기본값은 API Key 인증 비활성화이며, 인증 시험은 설정 활성화 조건부이다.",
        ],
    }
    for title, bullets in details.items():
        doc.add_heading(title, level=2)
        add_bullets(doc, bullets)
    return doc


def test_doc() -> Document:
    doc = Document()
    style_doc(doc)
    add_title(doc, "통합시험 시나리오 및 결과서")
    doc.add_heading("1. 통합시험 범위", level=1)
    add_bullets(
        doc,
        [
            "시험 기준은 최종 백엔드 egov-boot-web, Flyway V1~V4, ComplaintApiSmokeTest, 수동 API Key/감사 로그 검증이다.",
            "대시보드에 없는 첨부파일/초안 수정 기능은 Swagger 또는 API 수준 통합시험으로 검증한다.",
            "API Key 인증 시험은 app.security.api-key.enabled=true 조건에서 수행하는 조건부 보안 시험이다.",
        ],
    )
    doc.add_heading("2. 시험 시나리오 목록", level=1)
    add_table(
        doc,
        ["시나리오 ID", "시나리오명", "관련 UC", "시험 절차", "판정 기준"],
        [
            ["ITS-001", "민원 접수-분석-RAG-초안 Agent Loop", "UC-001~UC-005, UC-008", "POST /complaints -> GET /analysis -> GET /draft -> PUT /draft -> GET /rag-contexts", "ComplaintApiSmokeTest 자동 검증 성공"],
            ["ITS-002", "첨부파일 관리", "UC-006", "POST attachments -> GET attachments -> GET download -> DELETE attachment", "첨부 내용 일치 및 삭제 후 목록 0건"],
            ["ITS-003", "부서 조회 및 위험물 분류", "UC-002, UC-003, UC-007", "GET /departments -> 위험물 민원 POST -> GET /analysis -> GET /draft", "부서 5건, SAFETY_CONTROL, EMERGENCY 검증"],
            ["ITS-004", "상태/목록/GeoJSON API", "UC-009, UC-002", "PATCH /status, GET /complaints filters, GET /geojson", "상태 변경, 필터 조건, 위치 정보 기반 geoJson 확인"],
            ["ITS-005", "운영 상태 및 OpenAPI 문서", "UC-010", "GET /actuator/health, GET /v3/api-docs, GET /swagger-ui/index.html", "정상 응답 및 API path 노출"],
            ["ITS-006", "API Key 인증 및 감사 로그", "UC-010", "인증 활성화 후 X-API-Key 미포함/포함 요청, audit_logs 조회", "키 없음 401, 올바른 키 200, audit_logs 저장"],
        ],
    )
    doc.add_heading("3. 시험 케이스 상세", level=1)
    cases = [
        ["ITC-001", "민원 등록", "egov-boot-web 실행, DB 연결", "sourceChannel=WEB, rawText, locationText", "201 Created, success=true, receiptNumber=CIV-*", "성공"],
        ["ITC-002", "분석 결과 조회", "등록된 complaintId", "GET /api/complaints/{id}/analysis", "complaintType, urgency, sentiment, departmentCode, analysisJson 반환", "성공"],
        ["ITC-003", "RAG 근거 및 초안 생성", "분석 생성 가능, 지식문서 seed 존재", "GET /draft, GET /rag-contexts", "DraftResponse.status=DRAFT, references 1건 이상", "성공"],
        ["ITC-004", "초안 수정", "official_drafts 존재", "PUT /draft draftText", "DraftResponse.status=REVISED, draft_revisions 저장", "성공"],
        ["ITC-005", "첨부파일 등록", "등록된 complaintId, local 저장소", "POST /attachments evidence.txt", "AttachmentResponse 반환, complaint_attachments 저장", "성공"],
        ["ITC-006", "첨부파일 조회/다운로드/삭제", "등록된 attachmentId", "GET/DELETE attachments/{attachmentId}", "다운로드 내용 일치, 삭제 후 목록 0건", "성공"],
        ["ITC-007", "부서 목록 조회", "부서 seed 존재", "GET /api/departments", "5건 및 SAFETY_CONTROL 포함", "성공"],
        ["ITC-008", "위험물 민원 분류", "위험물 seed 지식문서 존재", "위험물 rawText -> GET /analysis", "HAZARDOUS_MATERIAL, EMERGENCY, SAFETY_CONTROL", "성공"],
        ["ITC-009", "상태 변경", "등록된 complaintId", "PATCH /status IN_PROGRESS", "ComplaintResponse.status=IN_PROGRESS", "보완 시험"],
        ["ITC-010", "목록 필터/페이징", "분석 완료 데이터 존재", "GET /api/complaints?status=&department=&urgency=&page=&size=", "PageResponse 반환 및 조건 필터 적용", "보완 시험"],
        ["ITC-011", "GeoJSON 조회", "locationText 존재", "GET /geojson", "Feature JSON 또는 data 문자열 반환", "보완 시험"],
        ["ITC-012", "운영 상태 확인", "서버 실행", "GET /actuator/health", "health=UP", "성공"],
        ["ITC-013", "OpenAPI 문서 확인", "springdoc 설정", "GET /v3/api-docs, /swagger-ui/index.html", "Civil Complaint API path 표시", "성공"],
        ["ITC-014", "API Key 인증 및 감사 로그", "인증/감사 활성화", "X-API-Key 미포함/포함 요청", "401/200 및 audit_logs 저장", "조건부 성공"],
    ]
    add_table(doc, ["TC ID", "시험 항목", "사전조건", "입력/절차", "예상결과", "시험결과"], cases)
    doc.add_heading("4. 유스케이스 커버리지", level=1)
    add_table(
        doc,
        ["유스케이스", "커버 시험", "커버리지 판단"],
        [
            ["UC-001 민원 등록", "ITC-001", "자동 검증"],
            ["UC-002 민원 분석", "ITC-002, ITC-008", "자동 검증"],
            ["UC-003 RAG 근거 검색", "ITC-003, ITC-008", "자동 검증"],
            ["UC-004 공문 초안 생성", "ITC-003", "자동 검증"],
            ["UC-005 대시보드 검토", "SC 문서 및 ITS-001 흐름", "화면/흐름 검증"],
            ["UC-006 첨부파일 관리", "ITC-005, ITC-006", "자동 검증"],
            ["UC-007 부서 조회", "ITC-007", "자동 검증"],
            ["UC-008 초안 수정", "ITC-004", "자동 검증"],
            ["UC-009 상태/목록/GeoJSON", "ITC-009~ITC-011", "보완 시험 필요 항목으로 명시"],
            ["UC-010 운영/보안", "ITC-012~ITC-014", "자동/수동/조건부 검증"],
        ],
    )
    return doc


def save(doc: Document, filename: str) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.save(OUT_DIR / filename)


def main() -> None:
    save(db_doc(), "데이터베이스_설계서_수정본.docx")
    save(ui_doc(), "사용자_인터페이스_설계서_수정본.docx")
    save(architecture_doc(), "아키텍처_설계서_수정본.docx")
    save(usecase_doc(), "유스케이스_명세서_수정본.docx")
    save(test_doc(), "통합시험_시나리오_및_결과서_수정본.docx")


if __name__ == "__main__":
    main()
