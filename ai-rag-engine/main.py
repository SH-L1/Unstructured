import os
import base64
import json
import mimetypes
import re
from pathlib import Path

from dotenv import load_dotenv
from openai import OpenAI

try:
    import psycopg2
except ImportError:
    psycopg2 = None

try:
    from data.API.law_api_client import search_law_documents
except ImportError:
    search_law_documents = None

try:
    from data.API.complaint_bigdata_api_client import search_complaint_bigdata_documents
except ImportError:
    search_complaint_bigdata_documents = None

try:
    from data.API.policy_qna_api_client import search_policy_qna_documents
except ImportError:
    search_policy_qna_documents = None

try:
    from department_router import recommend_department
except ImportError:
    recommend_department = None


# =========================
# 기본 설정
# =========================

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
MODEL_NAME = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

if not OPENAI_API_KEY:
    raise ValueError(
        "OPENAI_API_KEY가 없습니다. .env 파일에 OPENAI_API_KEY를 설정했는지 확인하세요."
    )

client = OpenAI(api_key=OPENAI_API_KEY)

BASE_DIR = Path(__file__).resolve().parent
KNOWLEDGE_DIR = BASE_DIR / "data" / "knowledge"
SAMPLE_FILE = BASE_DIR / "data" / "samples" / "sample_complaints.json"
RESULT_DIR = BASE_DIR / "result"

DB_ENV_KEYS = ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"]


# =========================
# 파일 로드 함수
# =========================

def load_sample_complaints():
    if not SAMPLE_FILE.exists():
        raise FileNotFoundError(f"샘플 민원 파일이 없습니다: {SAMPLE_FILE}")

    with open(SAMPLE_FILE, "r", encoding="utf-8") as f:
        raw_complaints = json.load(f)

    return normalize_complaints(raw_complaints)


def normalize_complaints(raw_complaints):
    """
    실제 민원 입력은 본문만 있어도 된다.
    - [{"text": "..."}] 형식
    - ["..."] 형식
    - id가 없으면 CPL-001 형태로 자동 생성
    """
    complaints = []

    for index, item in enumerate(raw_complaints, start=1):
        complaint_id = f"CPL-{index:03d}"

        if isinstance(item, str):
            text = item.strip()
            image_paths = []
        elif isinstance(item, dict):
            complaint_id = item.get("id") or complaint_id
            text = str(item.get("text", "")).strip()
            image_paths = normalize_image_paths(item)
        else:
            raise ValueError(f"지원하지 않는 민원 입력 형식입니다: {item}")

        if not text:
            raise ValueError(f"{complaint_id} 민원 본문이 비어 있습니다.")

        complaints.append({
            "id": complaint_id,
            "text": text,
            "image_paths": image_paths,
        })

    return complaints


def normalize_image_paths(item):
    image_paths = []

    if item.get("image_path"):
        image_paths.append(item["image_path"])

    if item.get("image_paths"):
        if not isinstance(item["image_paths"], list):
            raise ValueError("image_paths는 리스트 형식이어야 합니다.")
        image_paths.extend(item["image_paths"])

    cleaned_paths = []
    for image_path in image_paths:
        image_path = str(image_path).strip()
        if image_path and image_path not in cleaned_paths:
            cleaned_paths.append(image_path)

    return cleaned_paths


def get_db_config():
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": os.getenv("DB_PORT", "5432"),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": os.getenv("DB_USER", "complaint_user"),
        "password": os.getenv("DB_PASSWORD", "complaint_pass"),
    }


def has_db_config():
    return all(os.getenv(key) for key in DB_ENV_KEYS)


def load_knowledge_documents_from_markdown():
    """
    data/knowledge 하위의 모든 .md 파일을 읽는다.
    manual, national_law, ordinance 같은 하위 폴더까지 모두 포함한다.
    """
    if not KNOWLEDGE_DIR.exists():
        raise FileNotFoundError(f"지식 문서 폴더가 없습니다: {KNOWLEDGE_DIR}")

    docs = []

    for file_path in KNOWLEDGE_DIR.rglob("*.md"):
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read().strip()

        # 내용이 비어 있는 문서는 RAG 검색 대상에서 제외
        if not content:
            continue

        docs.append({
            "title": f"{file_path.parent.name}/{file_path.stem}",
            "source_type": file_path.parent.name,
            "content": content
        })

    if not docs:
        raise ValueError("data/knowledge 폴더 또는 하위 폴더에 내용이 있는 .md 문서가 없습니다.")

    return docs


def load_knowledge_documents_from_db():
    """
    Spring 백엔드가 사용하는 knowledge_documents 테이블에서 RAG 문서를 읽는다.
    DB가 연결되는 팀원 환경에서는 이 함수의 결과를 우선 사용한다.
    """
    if psycopg2 is None:
        raise RuntimeError("psycopg2가 설치되어 있지 않습니다.")

    db_config = get_db_config()

    with psycopg2.connect(**db_config) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    id,
                    document_type,
                    title,
                    source_name,
                    content,
                    keywords,
                    legal_basis
                FROM knowledge_documents
                ORDER BY id;
                """
            )
            rows = cur.fetchall()

    docs = []

    for row in rows:
        doc_id, document_type, title, source_name, content, keywords, legal_basis = row
        if not content:
            continue

        metadata = []
        if source_name:
            metadata.append(f"출처: {source_name}")
        if keywords:
            metadata.append(f"키워드: {keywords}")
        if legal_basis:
            metadata.append(f"근거: {legal_basis}")

        metadata_text = "\n".join(metadata)
        search_content = f"{metadata_text}\n\n{content}".strip()

        docs.append({
            "id": doc_id,
            "title": title,
            "source_type": document_type,
            "source_name": source_name,
            "content": search_content,
        })

    if not docs:
        raise ValueError("knowledge_documents 테이블에 RAG 문서가 없습니다.")

    return docs


def load_knowledge_documents():
    """
    RAG 문서를 DB에서 먼저 읽고, 실패하면 기존 Markdown 문서로 fallback한다.
    - 팀원 DB 환경: knowledge_documents 사용
    - 개인 로컬 환경: data/knowledge Markdown 사용
    """
    if has_db_config():
        try:
            docs = load_knowledge_documents_from_db()
            print("[RAG 문서 로드 방식] PostgreSQL knowledge_documents")
            return docs
        except Exception as exc:
            print("[RAG 문서 로드 방식] PostgreSQL 연결 실패, Markdown 파일로 fallback")
            print(f"- DB 로드 실패 사유: {exc}")

    docs = load_knowledge_documents_from_markdown()
    print("[RAG 문서 로드 방식] Markdown data/knowledge")
    return docs


# =========================
# LLM 응답 처리 함수
# =========================

def extract_json(text):
    """
    LLM 응답에서 JSON만 추출한다.
    """
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{[\s\S]*\}", text)
    if not match:
        raise ValueError("LLM 응답에서 JSON을 찾지 못했습니다.")

    return json.loads(match.group())


def call_llm(prompt):
    """
    OpenAI Responses API 호출
    """
    response = client.responses.create(
        model=MODEL_NAME,
        input=prompt
    )

    return response.output_text


def resolve_image_path(image_path):
    path = Path(image_path)

    if not path.is_absolute():
        path = BASE_DIR / path

    if not path.exists():
        raise FileNotFoundError(f"이미지 파일이 없습니다: {path}")

    return path


def image_to_data_url(image_path):
    path = resolve_image_path(image_path)
    mime_type, _ = mimetypes.guess_type(path)

    if not mime_type or not mime_type.startswith("image/"):
        raise ValueError(f"지원하지 않는 이미지 형식입니다: {path}")

    encoded = base64.b64encode(path.read_bytes()).decode("utf-8")
    return f"data:{mime_type};base64,{encoded}"


def analyze_complaint_images(image_paths):
    """
    첨부 이미지가 있으면 현장 상황을 요약한다.
    이미지만으로 위치, 위법 여부, 조치 완료 여부를 단정하지 않는다.
    """
    if not image_paths:
        return None

    content = [
        {
            "type": "input_text",
            "text": """
첨부된 민원 이미지를 공공기관 민원 검토 보조 관점에서 분석하라.
반드시 한국어 JSON 형식으로만 출력하라.

중요:
- 사진만으로 위치, 위법 여부, 과태료 부과 여부, 조치 완료 여부를 단정하지 마라.
- 보이는 정황만 설명하라.
- 불법 투기, 폐기물, 악취 정황, 도로 파손, 포트홀, 보행/차량 위험 정황이 있으면 표시하라.

출력 형식:
{
  "image_summary": "",
  "detected_objects": [],
  "risk_signals": [],
  "possible_categories": [],
  "supports_complaint": true,
  "notes": ""
}
""".strip(),
        }
    ]

    for image_path in image_paths:
        content.append({
            "type": "input_image",
            "image_url": image_to_data_url(image_path),
        })

    response = client.responses.create(
        model=MODEL_NAME,
        input=[
            {
                "role": "user",
                "content": content,
            }
        ],
    )

    return extract_json(response.output_text)


# =========================
# 1단계: 민원 분석
# =========================

def analyze_complaint(complaint_text, image_analysis=None, department_recommendation=None):
    image_context = ""
    department_context = ""

    if image_analysis:
        image_context = f"""

첨부 이미지 분석 결과:
{json.dumps(image_analysis, ensure_ascii=False, indent=2)}

위 이미지 분석 결과는 참고 자료로만 사용하라.
사진만으로 위치, 위법 여부, 조치 완료 여부를 단정하지 마라.
"""

    if department_recommendation:
        department_context = f"""

과거 전자민원 처리부 기반 담당 부서 추천 결과:
{json.dumps(department_recommendation, ensure_ascii=False, indent=2)}

위 추천 결과는 과거 유사 민원 처리 이력 기반 참고 자료다.
단, 추천 출처가 keyword_fallback이거나 best_match_score가 낮으면 현재 민원 내용과 더 직접적으로 맞는 부서를 우선하라.
department는 반드시 현재 민원 내용과 과거 유사 민원 처리부서를 함께 고려하여 작성하라.
"""

    prompt = f"""
너는 공공기관 민원 분석 시스템이다.

사용자의 민원 문장을 분석해서 반드시 JSON 형식으로만 출력하라.
설명 문장 없이 JSON만 출력하라.

중요:
- 모든 출력 값은 반드시 한국어로 작성하라.
- category는 민원 내용에 맞게 작성하라. 우선 "불법 투기", "도로 파손", "불법주정차", "생활소음", "선거유세 소음" 중 가장 적절한 값을 사용하라.
- department는 민원 내용과 과거 민원 부서 추천을 함께 고려하여 작성하라.
- keywords도 반드시 한국어 단어로 작성하라.
- 차량이 등장하더라도 주차, 정차, 견인, 통행 방해가 핵심이 아니면 불법주정차로 분류하지 마라.
- 마이크, 확성기, 데시벨, 수면 방해, 시끄러움이 핵심이면 생활소음 또는 선거유세 소음으로 분류하라.

우선 분류 가능한 민원 유형은 다음과 같다.
1. 불법 투기
2. 도로 파손
3. 불법주정차
4. 생활소음
5. 선거유세 소음

담당 부서 기준은 다음과 같다.
- 불법 투기, 쓰레기, 폐기물, 폐가구, 악취, 무단 배출: 자원순환과
- 도로 파손, 포트홀, 도로 균열, 인도 파손, 차량 위험, 보행 위험: 도로관리과
- 불법주정차, 주차 위반, 정차 위반, 견인, 차량 통행 방해, 우회전 방해: 교통행정과
- 생활소음, 확성기, 마이크, 데시벨, 공사 소음, 수면 방해: 환경보전과
- 선거운동, 선거유세, 유세차량 확성기 소음: 선거관리위원회 또는 환경보전과 확인 필요

긴급도 기준은 다음과 같다.
- High: 악취, 사고 위험, 즉시 처리 요구, 안전 위험
- Medium: 불편 발생
- Low: 단순 문의

출력 형식:
{{
  "category": "",
  "summary": "",
  "urgency": "",
  "department": "",
  "location_text": "",
  "keywords": [],
  "needs_field_check": true
}}

민원 문장:
\"\"\"
{complaint_text}
\"\"\"
{image_context}
{department_context}
"""

    result_text = call_llm(prompt)
    return normalize_analysis(extract_json(result_text), complaint_text)


def normalize_analysis(analysis, complaint_text):
    """
    LLM이 제한된 단어에 끌려 민원 유형을 잘못 고르는 경우를 줄이기 위한 후처리 규칙.
    """
    text = f"{complaint_text} {analysis.get('summary', '')} {' '.join(analysis.get('keywords', []))}"

    noise_terms = ["소음", "시끄", "마이크", "확성기", "데시벨", "잠", "수면", "노래"]
    election_terms = ["선거", "유세", "선거운동"]
    parking_terms = ["불법주차", "불법주정차", "주정차", "주차", "정차", "견인", "우회전", "통행 방해"]

    has_noise = any(term in text for term in noise_terms)
    has_election = any(term in text for term in election_terms)
    has_parking = any(term in text for term in parking_terms)

    if has_noise and not has_parking:
        analysis["category"] = "선거유세 소음" if has_election else "생활소음"
        analysis["department"] = "선거관리위원회 또는 환경보전과 확인 필요" if has_election else "환경보전과"
        keywords = analysis.get("keywords", [])
        if not isinstance(keywords, list):
            keywords = []
        for term in ["소음", "마이크", "데시벨"]:
            if term in text and term not in keywords:
                keywords.append(term)
        if has_election and "선거유세" not in keywords:
            keywords.append("선거유세")
        analysis["keywords"] = keywords
        analysis["needs_field_check"] = True

    return analysis


# =========================
# 2단계: 간단 RAG 검색
# =========================

def build_query_terms(analysis):
    """
    LLM 분석 결과를 기반으로 검색 키워드를 확장한다.
    현재는 pgvector가 없으므로 키워드 기반 검색 정확도를 높이기 위해 동의어를 추가한다.
    """
    keywords = analysis.get("keywords", [])
    category = analysis.get("category", "")
    department = analysis.get("department", "")
    summary = analysis.get("summary", "")
    location_text = analysis.get("location_text", "")

    query_terms = []

    if isinstance(keywords, list):
        query_terms.extend(keywords)

    query_terms.extend([category, department, summary, location_text])

    if category == "불법 투기" or department == "자원순환과":
        query_terms.extend([
            "불법 투기",
            "쓰레기",
            "폐기물",
            "생활폐기물",
            "폐가구",
            "악취",
            "무단 배출",
            "수거",
            "자원순환과",
            "폐기물관리법",
            "아산시 폐기물 관리 조례"
        ])

    if category == "도로 파손" or department == "도로관리과":
        query_terms.extend([
            "도로 파손",
            "포트홀",
            "도로 균열",
            "인도 파손",
            "차량 통행 위험",
            "보행 위험",
            "보수",
            "도로관리과",
            "도로법"
        ])

    if category == "불법주정차" or department == "교통행정과":
        query_terms.extend([
            "불법주정차",
            "불법주차",
            "주정차",
            "주차 위반",
            "정차 위반",
            "견인",
            "교통행정과",
            "차량 통행 방해",
            "우회전 방해",
            "도로교통법"
        ])

    if category in ["생활소음", "선거유세 소음"] or "환경보전과" in department:
        query_terms.extend([
            "소음",
            "생활소음",
            "확성기",
            "마이크",
            "데시벨",
            "수면 방해",
            "환경보전과",
            "소음ㆍ진동관리법"
        ])

    if category == "선거유세 소음" or "선거관리위원회" in department:
        query_terms.extend([
            "선거",
            "선거운동",
            "선거유세",
            "유세차량",
            "확성장치",
            "공직선거법"
        ])

    # 빈 문자열 제거 + 중복 제거
    generic_terms = {"불편", "민원", "확인", "요청", "처리", "검토"}
    cleaned_terms = []
    for term in query_terms:
        if not term:
            continue

        term = str(term).strip()
        if term in generic_terms:
            continue
        if term and term not in cleaned_terms:
            cleaned_terms.append(term)

    return cleaned_terms


def simple_rag_search(analysis, docs, top_k=5, debug=True):
    """
    pgvector 없이 키워드 기반으로 RAG 검색을 흉내낸다.
    나중에 DB 담당자가 PostgreSQL + pgvector로 교체하면 된다.
    """
    query_terms = build_query_terms(analysis)

    scored_docs = []

    for doc in docs:
        content = doc["content"]
        title = doc["title"]
        score = 0
        matched_terms = []

        search_target = f"{title}\n{content}"

        for term in query_terms:
            if term and term in search_target:
                score += 1
                matched_terms.append(term)

        scored_docs.append({
            "title": title,
            "source_type": doc.get("source_type", ""),
            "content": content,
            "score": score,
            "matched_terms": matched_terms
        })

    scored_docs.sort(key=lambda x: x["score"], reverse=True)

    if debug:
        print("\n[전체 문서 점수 확인]")
        for doc in scored_docs:
            print(f"- {doc['title']} / score={doc['score']} / matched={doc['matched_terms']}")

    results = [doc for doc in scored_docs if doc["score"] > 0]

    if any(analysis.get("category", "") == item for item in ["생활소음", "선거유세 소음"]):
        results = [
            doc for doc in results
            if doc["score"] >= 2
            or "소음" in doc["title"]
            or "선거" in doc["title"]
            or "소음" in doc["content"]
            or "선거" in doc["content"]
        ]

    # 점수가 있는 문서가 없으면 빈 리스트 반환
    if not results:
        return []

    return results[:top_k]


def build_law_api_query(analysis):
    """
    법제처 API 검색용 문장을 만든다.
    data/API/law_api_client.py에서 이 문장을 국가법령/자치법규 검색어로 다시 분리한다.
    """
    query_parts = []
    keywords = analysis.get("keywords", [])

    query_parts.append(analysis.get("category", ""))
    query_parts.append(analysis.get("department", ""))
    query_parts.append(analysis.get("summary", ""))

    if isinstance(keywords, list):
        query_parts.extend(keywords)

    category = analysis.get("category", "")
    if category in ["생활소음", "선거유세 소음"]:
        query_parts.extend(["소음진동관리법", "소음ㆍ진동관리법"])

    if category == "선거유세 소음":
        query_parts.extend(["공직선거법", "아산시 선거 소음"])

    return " ".join(str(part).strip() for part in query_parts if part).strip()


def search_law_api_results(analysis, display=2):
    """
    법제처 Open API에서 국가법령/자치법규 검색 결과를 가져와 RAG 결과 형식으로 변환한다.
    API 키가 없거나 네트워크 오류가 있어도 기존 RAG 흐름은 계속 진행한다.
    """
    if search_law_documents is None:
        print("[법제처 API] data/API/law_api_client.py를 불러오지 못해 건너뜁니다.")
        return []

    query = build_law_api_query(analysis)

    if not query:
        print("[법제처 API] 검색어가 없어 건너뜁니다.")
        return []

    try:
        law_docs = search_law_documents(query, display=display)
    except Exception as exc:
        print(f"[법제처 API] 검색 실패: {exc}")
        return []

    results = []

    for doc in law_docs:
        results.append({
            "id": doc.get("law_id"),
            "title": doc.get("title", ""),
            "source_type": doc.get("source_type", "LAW_API"),
            "source_name": doc.get("source_name", ""),
            "content": doc.get("content", ""),
            "score": 1,
            "matched_terms": [query],
        })

    return results


def build_complaint_bigdata_query(complaint_text, analysis):
    query_parts = [
        complaint_text,
        analysis.get("category", ""),
        analysis.get("summary", ""),
        analysis.get("department", ""),
    ]

    keywords = analysis.get("keywords", [])
    if isinstance(keywords, list):
        query_parts.extend(keywords)

    return " ".join(str(part).strip() for part in query_parts if part).strip()


def search_complaint_bigdata_api_results(complaint_text, analysis, display=3):
    """
    국민권익위 민원빅데이터 API에서 유사사례/연관어/핵심키워드를 가져와 RAG 보조 자료로 사용한다.
    API 실패 시에도 기존 처리 흐름은 계속 진행한다.
    """
    if search_complaint_bigdata_documents is None:
        print("[민원빅데이터 API] data/API/complaint_bigdata_api_client.py를 불러오지 못해 건너뜁니다.")
        return []

    query = build_complaint_bigdata_query(complaint_text, analysis)
    if not query:
        print("[민원빅데이터 API] 검색어가 없어 건너뜁니다.")
        return []

    try:
        return search_complaint_bigdata_documents(query, display=display)
    except Exception as exc:
        print(f"[민원빅데이터 API] 검색 실패: {exc}")
        return []


def search_policy_qna_api_results(complaint_text, analysis, display=3):
    """
    국민권익위 민원정책 Q&A에서 유사 질의와 처리기관을 가져와 RAG 보조 자료로 사용한다.
    """
    if search_policy_qna_documents is None:
        print("[민원정책 Q&A API] data/API/policy_qna_api_client.py를 불러오지 못해 건너뜁니다.")
        return []

    query = build_complaint_bigdata_query(complaint_text, analysis)
    if not query:
        print("[민원정책 Q&A API] 검색어가 없어 건너뜁니다.")
        return []

    try:
        return search_policy_qna_documents(query, display=display)
    except Exception as exc:
        print(f"[민원정책 Q&A API] 검색 실패: {exc}")
        return []


# =========================
# 3단계: 공문 초안 생성
# =========================

def generate_draft(complaint_text, analysis, rag_results, image_analysis=None):
    rag_context = ""

    for idx, item in enumerate(rag_results, start=1):
        rag_context += f"""
[근거 {idx}]
문서명: {item['title']}
문서 유형: {item.get('source_type', '')}
검색 점수: {item.get('score', 0)}
내용:
{item['content']}
"""

    if not rag_context.strip():
        rag_context = "검색된 근거 없음"

    image_context = "첨부 이미지 없음"
    if image_analysis:
        image_context = json.dumps(image_analysis, ensure_ascii=False, indent=2)

    prompt = f"""
너는 공공기관 민원 답변 초안 작성 보조 시스템이다.

아래 민원 원문, 민원 분석 결과, RAG 검색 결과를 바탕으로 공무원이 검토할 수 있는 답변 초안을 작성하라.

중요 규칙:
1. 공문 초안은 반드시 한국어로 작성하라.
2. 영어 표현을 사용하지 마라.
3. 제공된 RAG 검색 결과에 없는 법령명, 조례명, 조항 번호, 기관명은 절대 지어내지 마라.
4. 근거가 부족하면 "관련 근거 확인 필요"라고 작성하라.
5. 답변은 공공기관 민원 답변처럼 정중하게 작성하라.
6. COMPLAINT_BIGDATA_API 자료는 법적 근거가 아니라 유사 민원/연관어 참고자료로만 활용하라.
7. POLICY_QNA_API 자료는 처리기관/업무구분/유사 질의 참고자료로만 활용하고, 현재 민원의 담당 부서로 확정하지 마라.
8. 처리 완료라고 단정하지 마라.
9. "현장 확인 후 조치 예정", "검토 예정"처럼 작성하라.
10. 최종 발송 문서가 아니라 공무원 검토용 초안이라는 느낌으로 작성하라.
11. 활용 근거에는 RAG 검색 결과에 포함된 문서명만 작성하라.

민원 원문:
{complaint_text}

민원 분석 결과:
{json.dumps(analysis, ensure_ascii=False, indent=2)}

첨부 이미지 분석 결과:
{image_context}

RAG 검색 결과:
{rag_context}

출력 형식:
[공문 초안]
...

[활용 근거]
- ...
"""

    return call_llm(prompt)


# =========================
# 결과 저장 함수
# =========================

def save_result_files(
        complaint,
        analysis,
        rag_results,
        draft,
        image_analysis=None,
        department_recommendation=None
):
    output = {
        "complaintId": complaint["id"],
        "rawText": complaint["text"],
        "imagePaths": complaint.get("image_paths", []),
        "imageAnalysis": image_analysis,
        "departmentRecommendation": department_recommendation,
        "analysis": analysis,
        "ragResults": [
            {
                "document_id": item.get("id"),
                "title": item["title"],
                "source_type": item.get("source_type", ""),
                "source_name": item.get("source_name", ""),
                "content": item["content"],
                "score": item["score"],
                "matched_terms": item.get("matched_terms", [])
            }
            for item in rag_results
        ],
        "draft": draft
    }

    RESULT_DIR.mkdir(parents=True, exist_ok=True)

    output_file = RESULT_DIR / f"output_{complaint['id']}.json"

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    report_file = RESULT_DIR / f"output_{complaint['id']}_report.md"

    with open(report_file, "w", encoding="utf-8") as f:
        f.write("# 민원 처리 결과 보고서\n\n")

        f.write("## 1. 민원 원문\n\n")
        f.write(f"{complaint['text']}\n\n")

        f.write("## 2. AI 분석 결과\n\n")
        f.write(f"- 민원 유형: {analysis.get('category')}\n")
        f.write(f"- 요약: {analysis.get('summary')}\n")
        f.write(f"- 긴급도: {analysis.get('urgency')}\n")
        f.write(f"- 담당 부서: {analysis.get('department')}\n")
        f.write(f"- 위치: {analysis.get('location_text')}\n")
        f.write(f"- 검색 키워드: {', '.join(analysis.get('keywords', []))}\n")
        f.write(f"- 현장 확인 필요 여부: {analysis.get('needs_field_check')}\n\n")

        f.write("## 3. 과거 민원 기반 부서 추천 결과\n\n")

        if department_recommendation:
            f.write(f"- 추천 부서: {department_recommendation.get('recommended_department') or '추천 결과 없음'}\n")
            f.write(f"- 검색 토큰: {', '.join(department_recommendation.get('query_tokens', []))}\n\n")
            f.write("### 유사 과거 민원\n\n")
            for item in department_recommendation.get("top_matches", []):
                f.write(f"- score={item.get('score')} / {item.get('department')}\n")
                f.write(f"  - 민원명: {item.get('title')}\n")
                f.write(f"  - 요지: {item.get('summary')}\n")
                f.write(f"  - 지역: {item.get('region')} / 시트: {item.get('year_sheet')}\n")
        else:
            f.write("부서 추천 결과 없음\n")
        f.write("\n")

        f.write("## 4. 첨부 이미지 분석 결과\n\n")

        if image_analysis:
            f.write("```json\n")
            f.write(json.dumps(image_analysis, ensure_ascii=False, indent=2))
            f.write("\n```\n\n")
        else:
            f.write("첨부 이미지 없음\n\n")

        f.write("## 5. RAG 검색 결과\n\n")

        if rag_results:
            for item in rag_results:
                f.write(f"### {item['title']}\n\n")
                f.write(f"- 문서 유형: {item.get('source_type', '')}\n")
                f.write(f"- 검색 점수: {item['score']}\n")
                f.write(f"- 매칭 키워드: {', '.join(item.get('matched_terms', []))}\n\n")
                f.write(item["content"])
                f.write("\n\n")
        else:
            f.write("검색된 근거 없음\n\n")

        f.write("## 6. 공문 초안\n\n")
        f.write(draft)

    print(f"\nJSON 저장 완료: {output_file}")
    print(f"보고서 저장 완료: {report_file}")

    official_file = RESULT_DIR / f"output_{complaint['id']}_official_review.md"
    save_official_review_file(
        official_file,
        complaint,
        analysis,
        rag_results,
        draft,
        image_analysis=image_analysis,
        department_recommendation=department_recommendation,
    )
    print(f"공무원 검토용 요약 저장 완료: {official_file}")


def extract_draft_body(draft):
    marker = "[공문 초안]"
    if marker in draft:
        return draft.split(marker, 1)[1].split("[활용 근거]", 1)[0].strip()
    return draft.strip()


def summarize_processing_steps(analysis, rag_results):
    category = analysis.get("category", "")
    department = analysis.get("department", "")
    keywords = " ".join(analysis.get("keywords", []))
    combined_text = f"{category} {department} {keywords}"

    if "소음" in combined_text or "마이크" in combined_text or "확성기" in combined_text or "데시벨" in combined_text:
        if "선거" in combined_text or "유세" in combined_text:
            return [
                "소음 발생 위치, 시간대, 확성기 또는 유세차량 여부를 확인한다.",
                "선거운동 관련 사항인지 확인하고 필요 시 선거관리위원회 등 관계기관 소관 여부를 검토한다.",
                "생활소음 측정 또는 현장 확인이 필요한지 담당 부서에서 판단한다.",
                "민원인에게 현장 확인 예정과 관계 법령 및 소관 기관 검토 예정임을 안내한다.",
            ]

        return [
            "소음 발생 위치, 시간대, 반복 여부와 소음원을 확인한다.",
            "현장 확인 또는 소음 측정 필요 여부를 담당 부서에서 검토한다.",
            "기준 초과나 행정처분 여부는 현장 확인 전 단정하지 않는다.",
            "민원인에게 접수 사실과 관련 절차에 따른 검토 예정임을 안내한다.",
        ]

    if "불법주차" in combined_text or "주정차" in combined_text or "주차" in combined_text:
        return [
            "신고 위치와 차량 통행 방해 여부를 확인한다.",
            "현장 확인 또는 단속 가능 여부를 담당 부서에서 검토한다.",
            "주정차 위반 여부는 관련 절차에 따라 확인하며, 과태료 또는 견인 여부를 사전에 단정하지 않는다.",
            "민원인에게 접수 사실, 현장 확인 예정, 관련 절차에 따른 검토 예정임을 안내한다.",
        ]

    if category == "불법 투기" or department == "자원순환과":
        return [
            "신고 위치와 폐기물 종류, 악취 또는 통행 불편 여부를 확인한다.",
            "현장 확인 후 수거, 단속, 계도 필요성을 검토한다.",
            "투기자 특정 또는 과태료 부과 여부는 현장 확인 전 단정하지 않는다.",
            "민원인에게 현장 확인 예정과 관련 절차에 따른 조치 검토 예정임을 안내한다.",
        ]

    if category == "도로 파손" or department == "도로관리과":
        return [
            "신고 위치와 도로 파손 규모, 보행자 및 차량 통행 위험 여부를 확인한다.",
            "현장 확인 후 긴급 보수 또는 보수 일정 검토 필요성을 판단한다.",
            "현장 확인 전 보수 완료나 구체적 공사 일정을 단정하지 않는다.",
            "민원인에게 현장 확인 예정과 보수 필요성 검토 예정임을 안내한다.",
        ]

    return [
        "민원 위치와 사실관계를 우선 확인한다.",
        "담당 부서 배정이 적절한지 검토한다.",
        "관련 법령, 조례, 매뉴얼 근거를 확인한다.",
        "처리 완료를 단정하지 않고 검토 예정 사항을 안내한다.",
    ]


def select_key_references(rag_results, max_items=5):
    selected = []

    for item in rag_results:
        title = item.get("title", "")
        content = item.get("content", "")

        if "일치하는 법령이 없습니다" in content:
            continue

        selected.append(item)

        if len(selected) >= max_items:
            break

    return selected


def save_official_review_file(
        output_file,
        complaint,
        analysis,
        rag_results,
        draft,
        image_analysis=None,
        department_recommendation=None
):
    key_references = select_key_references(rag_results)
    processing_steps = summarize_processing_steps(analysis, rag_results)
    draft_body = extract_draft_body(draft)

    with open(output_file, "w", encoding="utf-8") as f:
        f.write("# 공무원 검토용 민원 처리 요약\n\n")

        f.write("## 1. 민원 접수 내용\n\n")
        f.write(f"- 민원 ID: {complaint['id']}\n")
        f.write(f"- 민원 원문: {complaint['text']}\n")
        f.write(f"- 첨부 이미지: {', '.join(complaint.get('image_paths', [])) or '없음'}\n\n")

        f.write("## 2. AI 분석 요약\n\n")
        f.write(f"- 민원 유형: {analysis.get('category')}\n")
        f.write(f"- 담당 부서 후보: {analysis.get('department')}\n")
        f.write(f"- 긴급도: {analysis.get('urgency')}\n")
        f.write(f"- 위치 정보: {analysis.get('location_text')}\n")
        f.write(f"- 핵심 키워드: {', '.join(analysis.get('keywords', []))}\n")
        f.write(f"- 현장 확인 필요: {analysis.get('needs_field_check')}\n\n")
        f.write("## 3. 처리 절차 요약\n\n")
        for index, step in enumerate(processing_steps, start=1):
            f.write(f"{index}. {step}\n")
        f.write("\n")

        f.write("## 4. 공무원 확인 사항\n\n")
        f.write("- 담당 부서가 실제 업무분장과 맞는지 확인\n")
        if department_recommendation and department_recommendation.get("recommended_department"):
            f.write(f"- 과거 유사 민원 기준 추천 부서 `{department_recommendation.get('recommended_department')}`와 AI 담당 부서가 일치하는지 확인\n")
        f.write("- 민원 위치가 충분히 특정되는지 확인\n")
        f.write("- 현장 확인 또는 추가 정보 요청 필요 여부 확인\n")
        f.write("- 법령/조례 근거가 민원 내용과 직접 관련 있는지 확인\n")
        f.write("- 초안에 처리 완료, 단속 완료, 과태료 부과 등 단정 표현이 없는지 확인\n\n")

        if image_analysis:
            f.write("## 5. 첨부 이미지 확인 요약\n\n")
            f.write(f"- 이미지 요약: {image_analysis.get('image_summary')}\n")
            f.write(f"- 위험 신호: {', '.join(image_analysis.get('risk_signals', []))}\n")
            f.write(f"- 참고 사항: {image_analysis.get('notes')}\n\n")

        f.write("## 6. 주요 근거 문서\n\n")
        if key_references:
            for item in key_references:
                f.write(f"- {item.get('title')} ({item.get('source_type', '')})\n")
        else:
            f.write("- 직접 활용 가능한 근거 문서 확인 필요\n")
        f.write("\n")

        f.write("## 7. 공문 초안\n\n")
        f.write(draft_body)
        f.write("\n")


# =========================
# 실행 함수
# =========================

def run_one_complaint(complaint):
    print("=" * 80)
    print(f"[민원 ID] {complaint['id']}")
    print(f"[민원 원문] {complaint['text']}")
    if complaint.get("image_paths"):
        print(f"[첨부 이미지] {', '.join(complaint['image_paths'])}")
    print("=" * 80)

    docs = load_knowledge_documents()

    print(f"\n[로드된 RAG 문서 수] {len(docs)}개")
    for doc in docs:
        print(f"- {doc['title']}")

    image_analysis = None

    if complaint.get("image_paths"):
        print("\n1) 첨부 이미지 분석 중...")
        try:
            image_analysis = analyze_complaint_images(complaint["image_paths"])
            print("\n[이미지 분석 결과]")
            print(json.dumps(image_analysis, ensure_ascii=False, indent=2))
        except Exception as exc:
            print(f"\n[이미지 분석 실패] {exc}")
            print("이미지 분석 없이 텍스트 민원 분석을 계속 진행합니다.")

    department_recommendation = None

    print("\n1-1) 과거 민원 기반 담당 부서 추천 중...")
    if recommend_department is None:
        print("[부서 추천] department_router.py를 불러오지 못해 건너뜁니다.")
    else:
        try:
            department_recommendation = recommend_department(complaint["text"], top_k=5)
            print("\n[부서 추천 결과]")
            print(f"- 추천 부서: {department_recommendation.get('recommended_department') or '추천 결과 없음'}")
            for item in department_recommendation.get("top_matches", []):
                print(f"- score={item.get('score')} / {item.get('department')} / {item.get('title')}")
        except Exception as exc:
            print(f"[부서 추천 실패] {exc}")
            print("부서 추천 없이 텍스트 민원 분석을 계속 진행합니다.")

    print("\n1-2) 민원 분석 중...")
    analysis = analyze_complaint(
        complaint["text"],
        image_analysis=image_analysis,
        department_recommendation=department_recommendation,
    )

    print("\n[AI 분석 결과]")
    print(json.dumps(analysis, ensure_ascii=False, indent=2))

    print("\n2) RAG 검색 중...")
    rag_results = simple_rag_search(analysis, docs, top_k=5, debug=True)

    print("\n2-1) 법제처 Open API 검색 중...")
    law_api_results = search_law_api_results(analysis, display=2)

    if law_api_results:
        print("\n[법제처 API 검색 결과]")
        for item in law_api_results:
            print(f"- {item['title']} / type={item.get('source_type', '')}")
    else:
        print("\n[법제처 API 검색 결과]")
        print("- 검색 결과 없음 또는 API 검색 건너뜀")

    print("\n2-2) 국민권익위 민원빅데이터 API 검색 중...")
    complaint_bigdata_results = search_complaint_bigdata_api_results(
        complaint["text"],
        analysis,
        display=3,
    )

    if complaint_bigdata_results:
        print("\n[민원빅데이터 API 검색 결과]")
        for item in complaint_bigdata_results:
            print(f"- {item['title']} / type={item.get('source_type', '')} / score={item.get('score', 0)}")
    else:
        print("\n[민원빅데이터 API 검색 결과]")
        print("- 검색 결과 없음 또는 API 검색 건너뜀")

    print("\n2-3) 국민권익위 민원정책 Q&A API 검색 중...")
    policy_qna_results = search_policy_qna_api_results(
        complaint["text"],
        analysis,
        display=3,
    )

    if policy_qna_results:
        print("\n[민원정책 Q&A API 검색 결과]")
        for item in policy_qna_results:
            print(f"- {item['title']} / type={item.get('source_type', '')} / score={item.get('score', 0)}")
    else:
        print("\n[민원정책 Q&A API 검색 결과]")
        print("- 검색 결과 없음 또는 API 검색 건너뜀")

    combined_rag_results = rag_results + law_api_results + complaint_bigdata_results + policy_qna_results

    print("\n[RAG 검색 결과]")
    if rag_results:
        for item in rag_results:
            print(f"- {item['title']} / score={item['score']}")
    else:
        print("- 검색 결과 없음")

    print("\n3) 공문 초안 생성 중...")
    draft = generate_draft(
        complaint["text"],
        analysis,
        combined_rag_results,
        image_analysis=image_analysis,
    )

    print("\n[최종 공문 초안]")
    print(draft)

    save_result_files(
        complaint,
        analysis,
        combined_rag_results,
        draft,
        image_analysis=image_analysis,
        department_recommendation=department_recommendation,
    )


if __name__ == "__main__":
    complaints = load_sample_complaints()

    for complaint in complaints:
        run_one_complaint(complaint)
