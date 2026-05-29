import os
import json
import re
from pathlib import Path

from dotenv import load_dotenv
from openai import OpenAI


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


# =========================
# 파일 로드 함수
# =========================

def load_sample_complaints():
    if not SAMPLE_FILE.exists():
        raise FileNotFoundError(f"샘플 민원 파일이 없습니다: {SAMPLE_FILE}")

    with open(SAMPLE_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def load_knowledge_documents():
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


# =========================
# 1단계: 민원 분석
# =========================

def analyze_complaint(complaint_text):
    prompt = f"""
너는 공공기관 민원 분석 시스템이다.

사용자의 민원 문장을 분석해서 반드시 JSON 형식으로만 출력하라.
설명 문장 없이 JSON만 출력하라.

중요:
- 모든 출력 값은 반드시 한국어로 작성하라.
- category는 반드시 "불법 투기" 또는 "도로 파손" 중 하나로 작성하라.
- department는 반드시 "자원순환과" 또는 "도로관리과" 중 하나로 작성하라.
- keywords도 반드시 한국어 단어로 작성하라.

분류 가능한 민원 유형은 다음 두 가지다.
1. 불법 투기
2. 도로 파손

담당 부서 기준은 다음과 같다.
- 불법 투기, 쓰레기, 폐기물, 폐가구, 악취, 무단 배출: 자원순환과
- 도로 파손, 포트홀, 도로 균열, 인도 파손, 차량 위험, 보행 위험: 도로관리과

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
"""

    result_text = call_llm(prompt)
    return extract_json(result_text)


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

    # 빈 문자열 제거 + 중복 제거
    cleaned_terms = []
    for term in query_terms:
        if not term:
            continue

        term = str(term).strip()
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

    # 점수가 있는 문서가 없으면 빈 리스트 반환
    if not results:
        return []

    return results[:top_k]


# =========================
# 3단계: 공문 초안 생성
# =========================

def generate_draft(complaint_text, analysis, rag_results):
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

    prompt = f"""
너는 공공기관 민원 답변 초안 작성 보조 시스템이다.

아래 민원 원문, 민원 분석 결과, RAG 검색 결과를 바탕으로 공무원이 검토할 수 있는 답변 초안을 작성하라.

중요 규칙:
1. 공문 초안은 반드시 한국어로 작성하라.
2. 영어 표현을 사용하지 마라.
3. 제공된 RAG 검색 결과에 없는 법령명, 조례명, 조항 번호, 기관명은 절대 지어내지 마라.
4. 근거가 부족하면 "관련 근거 확인 필요"라고 작성하라.
5. 답변은 공공기관 민원 답변처럼 정중하게 작성하라.
6. 처리 완료라고 단정하지 마라.
7. "현장 확인 후 조치 예정", "검토 예정"처럼 작성하라.
8. 최종 발송 문서가 아니라 공무원 검토용 초안이라는 느낌으로 작성하라.
9. 활용 근거에는 RAG 검색 결과에 포함된 문서명만 작성하라.

민원 원문:
{complaint_text}

민원 분석 결과:
{json.dumps(analysis, ensure_ascii=False, indent=2)}

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

def save_result_files(complaint, analysis, rag_results, draft):
    output = {
        "complaintId": complaint["id"],
        "rawText": complaint["text"],
        "analysis": analysis,
        "ragResults": [
            {
                "title": item["title"],
                "source_type": item.get("source_type", ""),
                "content": item["content"],
                "score": item["score"],
                "matched_terms": item.get("matched_terms", [])
            }
            for item in rag_results
        ],
        "draft": draft
    }

    output_file = BASE_DIR / f"output_{complaint['id']}.json"

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    report_file = BASE_DIR / f"output_{complaint['id']}_report.md"

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

        f.write("## 3. RAG 검색 결과\n\n")

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

        f.write("## 4. 공문 초안\n\n")
        f.write(draft)

    print(f"\nJSON 저장 완료: {output_file}")
    print(f"보고서 저장 완료: {report_file}")


# =========================
# 실행 함수
# =========================

def run_one_complaint(complaint):
    print("=" * 80)
    print(f"[민원 ID] {complaint['id']}")
    print(f"[민원 원문] {complaint['text']}")
    print("=" * 80)

    docs = load_knowledge_documents()

    print(f"\n[로드된 RAG 문서 수] {len(docs)}개")
    for doc in docs:
        print(f"- {doc['title']}")

    print("\n1) 민원 분석 중...")
    analysis = analyze_complaint(complaint["text"])

    print("\n[AI 분석 결과]")
    print(json.dumps(analysis, ensure_ascii=False, indent=2))

    print("\n2) RAG 검색 중...")
    rag_results = simple_rag_search(analysis, docs, top_k=5, debug=True)

    print("\n[RAG 검색 결과]")
    if rag_results:
        for item in rag_results:
            print(f"- {item['title']} / score={item['score']}")
    else:
        print("- 검색 결과 없음")

    print("\n3) 공문 초안 생성 중...")
    draft = generate_draft(complaint["text"], analysis, rag_results)

    print("\n[최종 공문 초안]")
    print(draft)

    save_result_files(complaint, analysis, rag_results, draft)


if __name__ == "__main__":
    complaints = load_sample_complaints()

    for complaint in complaints:
        run_one_complaint(complaint)