import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, List
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlencode
from urllib.request import Request, urlopen

from dotenv import load_dotenv
from data.API.provider_runtime import ProviderUnavailableError, execute_provider_call


DEFAULT_BASE_URL = "https://apis.data.go.kr/1140100/CivilPolicyQnaService"
LIST_ENDPOINT = "/PolicyQnaList"
DEFAULT_TIMEOUT_SECONDS = 10
API_DIR = Path(__file__).resolve().parent
BASE_DIR = API_DIR.parents[1]
ENV_PATH = BASE_DIR / ".env"


def get_api_key() -> str:
    load_dotenv(ENV_PATH)
    api_key = (
        os.getenv("POLICY_QNA_API_KEY", "").strip()
        or os.getenv("COMPLAINT_BIGDATA_API_KEY", "").strip()
        or os.getenv("COMPLAINT_BIGDATA_SERVICE_KEY", "").strip()
    )

    if not api_key:
        raise ValueError("POLICY_QNA_API_KEY 또는 COMPLAINT_BIGDATA_API_KEY가 없습니다.")

    return unquote(api_key)


def get_base_url() -> str:
    load_dotenv(ENV_PATH)
    return os.getenv("POLICY_QNA_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def compact_text(value, max_chars: int = 1200) -> str:
    if value is None:
        return ""

    text = re.sub(r"\s+", " ", str(value)).strip()
    if len(text) <= max_chars:
        return text

    return text[:max_chars].rstrip() + " ..."


def normalize_keyword(query: str) -> str:
    text = re.sub(r"[^0-9A-Za-z가-힣]+", " ", query)
    tokens = [token for token in text.split() if len(token) >= 2]

    stopwords = {
        "민원", "문의", "요청", "확인", "검토", "처리", "관련", "내용",
        "있습니다", "합니다", "해주세요", "때문에",
    }
    filtered = [token for token in tokens if token not in stopwords]

    return " ".join(filtered[:8] or tokens[:8])


def request_policy_qna_list(keyword: str, display: int = 5, search_type: str = "1") -> Dict[str, object]:
    params = {
        "serviceKey": get_api_key(),
        "firstIndex": "1",
        "recordCountPerPage": str(display),
        "type": "3",
        "keyword": keyword,
        "searchType": search_type,
    }

    url = f"{get_base_url()}{LIST_ENDPOINT}?{urlencode(params)}"
    request = Request(
        url,
        headers={
            "User-Agent": "egov-rag-complaint-mvp/1.0",
            "Accept": "application/json, */*",
        },
    )

    body = execute_provider_call(
        "policy-qna-api",
        50,
        lambda: read_response(request),
    ).value

    text = body.decode("utf-8", errors="replace").strip()
    return json.loads(text)


def read_response(request: Request) -> bytes:
    timeout = float(os.getenv("PUBLIC_API_TIMEOUT_SECONDS", str(DEFAULT_TIMEOUT_SECONDS)))
    with urlopen(request, timeout=timeout) as response:
        return response.read()


def extract_items(data: Dict[str, object]) -> List[Dict[str, object]]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]

    candidates = [
        data.get("data"),
        data.get("items"),
        data.get("item"),
        data.get("list"),
        data.get("PolicyQnaList"),
    ]

    for candidate in candidates:
        if isinstance(candidate, list):
            return [item for item in candidate if isinstance(item, dict)]
        if isinstance(candidate, dict):
            nested = extract_items(candidate)
            if nested:
                return nested

    items = []

    def walk(value):
        if isinstance(value, dict):
            if "faqNo" in value and "title" in value:
                items.append(value)
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(data)
    return items


def is_success_response(data: Dict[str, object]) -> bool:
    result_code = str(data.get("resultCode") or data.get("code") or "").strip()
    if not result_code:
        return True
    return result_code in ["S00", "00", "200", "0"]


def summarize_qna_records(records: List[Dict[str, object]], max_items: int = 5) -> str:
    if not records:
        return "국민권익위 민원정책 Q&A: 검색 결과 없음"

    lines = ["국민권익위 민원정책 Q&A 유사 질의 목록"]
    for index, record in enumerate(records[:max_items], start=1):
        duty_name = record.get("dutySctnNm", "")
        duty_label = {
            "tqapttn": "민원",
            "tqaplcy": "정책",
        }.get(duty_name, duty_name)

        lines.append(
            "\n".join([
                f"{index}. 제목: {compact_text(record.get('title'), 250)}",
                f"   FAQ번호: {record.get('faqNo', '')}",
                f"   처리기관: {record.get('ancName', '')} ({record.get('ancCode', '')})",
                f"   업무구분: {duty_label}",
                f"   등록일시: {record.get('regDate', '')}",
            ])
        )

    return "\n\n".join(lines)


def safe_policy_qna_search(keyword: str, display: int = 5) -> List[Dict[str, object]]:
    results = []

    for search_type in ["1", "2"]:
        try:
            data = request_policy_qna_list(keyword, display=display, search_type=search_type)
        except (
            HTTPError,
            URLError,
            TimeoutError,
            ValueError,
            json.JSONDecodeError,
            ProviderUnavailableError,
        ) as exc:
            results.append({
                "error": f"민원정책 Q&A 조회 실패(searchType={search_type}): {type(exc).__name__}: {exc}",
            })
            continue

        if isinstance(data, dict) and not is_success_response(data):
            results.append({
                "error": f"민원정책 Q&A 응답 오류(searchType={search_type}): {data}",
            })
            continue

        results.extend(extract_items(data))

    deduped = []
    seen = set()
    for record in results:
        key = record.get("faqNo") or record.get("title")
        if not key or key in seen:
            continue
        seen.add(key)
        deduped.append(record)

    return deduped


def search_policy_qna_documents(query: str, display: int = 5) -> List[Dict[str, object]]:
    """
    국민권익위 민원정책 Q&A를 RAG 보조자료로 변환한다.
    처리기관/업무구분/유사 질의 제목 참고용이며 법적 근거로 단정하지 않는다.
    """
    keyword = normalize_keyword(query)
    if not keyword:
        return []

    records = safe_policy_qna_search(keyword, display=display)

    if records and "error" in records[0]:
        return []

    content = summarize_qna_records(records, max_items=display)
    if not records:
        return []

    return [{
        "title": "국민권익위 민원정책 Q&A 유사 질의",
        "source_type": "POLICY_QNA_API",
        "source_name": "CivilPolicyQnaService/PolicyQnaList",
        "content": content,
        "matched_terms": [keyword],
    }]


def main():
    query = " ".join(sys.argv[1:]).strip() or "소음 확성기"
    print("[국민권익위 민원정책 Q&A API 검색 테스트]")
    print(f"- 검색어: {query}")

    documents = search_policy_qna_documents(query)
    if not documents:
        print("- 검색 결과 없음 또는 API 조회 실패")
        return

    for document in documents:
        print(f"\n[{document['title']}]")
        print(document["content"])


if __name__ == "__main__":
    main()
