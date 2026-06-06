import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from datetime import date, timedelta
from pathlib import Path
from typing import Dict, List
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from dotenv import load_dotenv
from data.API.provider_runtime import ProviderUnavailableError, execute_provider_call


DEFAULT_BASE_URL = "https://apis.data.go.kr/1140100/minAnalsInfoView5"
DEFAULT_DISPLAY = 5
DEFAULT_TIMEOUT_SECONDS = 10
API_DIR = Path(__file__).resolve().parent
BASE_DIR = API_DIR.parents[1]
ENV_PATH = BASE_DIR / ".env"

ENDPOINTS = {
    "rising_keywords": "/minRisingKeyword5",
    "top_keywords": "/minTopNKeyword5",
    "classification": "/minClfcInfo5",
    "statistics": "/minStaticsInfo5",
    "keyword_trend": "/minTimeSeriseView5",
    "similar_cases": "/minSimilarInfo5",
    "related_words": "/minWdcloudInfo5",
    "today_topics": "/minTodayTopicInfo5",
    "agency_rank": "/minMofacetInfo5",
    "region_rank": "/minMrfacetInfo5",
    "keyword_count": "/minSearchDocCnt5",
    "population_ratio": "/minMrPopltnRtInfo5",
    "df_top_keywords": "/minDFTopNKeyword5",
    "analysis_reports": "/minAnalsRptstInfo5",
    "gender_by_keyword": "/minPttnStstGndrInfo5",
    "age_by_keyword": "/minPttnStstAgeInfo5",
}

TEXT_KEYS = {
    "title", "ttl", "sj", "subject", "question", "query", "content", "cn",
    "summary", "smry", "answer", "ans", "keyword", "kwrd", "word", "relword",
    "clfc", "category", "large", "middle", "small", "기관", "지역", "분류",
    "제목", "내용", "요지", "답변", "키워드", "연관어",
}


def get_api_key() -> str:
    load_dotenv(ENV_PATH)
    api_key = (
        os.getenv("COMPLAINT_BIGDATA_API_KEY", "").strip()
        or os.getenv("COMPLAINT_BIGDATA_SERVICE_KEY", "").strip()
    )

    if not api_key:
        raise ValueError("COMPLAINT_BIGDATA_API_KEY가 없습니다. .env 파일에 인증키를 설정하세요.")

    return api_key


def get_base_url() -> str:
    load_dotenv(ENV_PATH)
    return os.getenv("COMPLAINT_BIGDATA_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def default_date_range(days: int = 30) -> Dict[str, str]:
    end_date = date.today()
    start_date = end_date - timedelta(days=days)
    return {
        "startDate": start_date.strftime("%Y%m%d"),
        "endDate": end_date.strftime("%Y%m%d"),
        "baseDate": end_date.strftime("%Y%m%d"),
    }


def compact_text(value, max_chars: int = 1400) -> str:
    if value is None:
        return ""

    text = re.sub(r"\s+", " ", str(value)).strip()
    if len(text) <= max_chars:
        return text

    return text[:max_chars].rstrip() + " ..."


def normalize_keyword(query: str) -> str:
    text = re.sub(r"[^0-9A-Za-z가-힣]+", " ", query)
    tokens = [token for token in text.split() if len(token) >= 2]
    return " ".join(tokens[:20])


def build_request_params(extra_params: Dict[str, str] = None) -> Dict[str, str]:
    params = {
        "serviceKey": get_api_key(),
        "dataType": "json",
    }

    if extra_params:
        params.update({key: value for key, value in extra_params.items() if value not in [None, ""]})

    return params


def request_endpoint(endpoint_key: str, params: Dict[str, str] = None):
    if endpoint_key not in ENDPOINTS:
        raise ValueError(f"지원하지 않는 상세기능입니다: {endpoint_key}")

    url = f"{get_base_url()}{ENDPOINTS[endpoint_key]}?{urlencode(build_request_params(params))}"
    request = Request(
        url,
        headers={
            "User-Agent": "egov-rag-complaint-mvp/1.0",
            "Accept": "application/json, application/xml, text/xml, */*",
        },
    )

    body, content_type = execute_provider_call(
        "complaint-bigdata-api",
        50,
        lambda: read_response(request),
    ).value

    return parse_response(body, content_type)


def read_response(request: Request) -> tuple[bytes, str]:
    timeout = float(os.getenv("PUBLIC_API_TIMEOUT_SECONDS", str(DEFAULT_TIMEOUT_SECONDS)))
    with urlopen(request, timeout=timeout) as response:
        return response.read(), response.headers.get("Content-Type", "")


def parse_response(body: bytes, content_type: str = ""):
    text = body.decode("utf-8", errors="replace").strip()

    if "json" in content_type.lower() or text.startswith("{") or text.startswith("["):
        return json.loads(text)

    if text.startswith("<"):
        return xml_to_dict(ET.fromstring(text))

    return {"raw": text}


def xml_to_dict(element: ET.Element):
    children = list(element)
    text = (element.text or "").strip()

    if not children:
        return text

    result = {}
    for child in children:
        key = strip_namespace(child.tag)
        value = xml_to_dict(child)

        if key in result:
            if not isinstance(result[key], list):
                result[key] = [result[key]]
            result[key].append(value)
        else:
            result[key] = value

    if text:
        result["_text"] = text

    return result


def strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def flatten_items(data) -> List[Dict[str, object]]:
    items = []

    def walk(value):
        if isinstance(value, dict):
            if looks_like_record(value):
                items.append(value)

            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(data)
    return items


def looks_like_record(value: Dict[str, object]) -> bool:
    if len(value) < 2:
        return False

    normalized_keys = {str(key).lower() for key in value.keys()}
    if normalized_keys & TEXT_KEYS:
        return True

    return any(isinstance(item, (str, int, float)) for item in value.values())


def is_error_record(record: Dict[str, object]) -> bool:
    code = str(record.get("code") or record.get("resultCode") or "").strip()
    message = str(record.get("msg") or record.get("resultMsg") or "").strip().upper()

    if code and code not in ["200", "00", "0"]:
        return True

    return "ERROR" in message or "INVALID" in message or "SERVICE_KEY" in message


def record_to_text(record: Dict[str, object]) -> str:
    parts = []

    for key, value in record.items():
        if isinstance(value, (dict, list)):
            continue

        text = compact_text(value, max_chars=300)
        if text:
            parts.append(f"{key}: {text}")

    return "\n".join(parts)


def summarize_records(title: str, records: List[Dict[str, object]], max_items: int = 5) -> str:
    if not records:
        return f"{title}: 검색 결과 없음"

    lines = [title]
    for index, record in enumerate(records[:max_items], start=1):
        text = record_to_text(record)
        if text:
            lines.append(f"{index}. {text}")

    return "\n\n".join(lines)


def safe_request(endpoint_key: str, params: Dict[str, str] = None) -> List[Dict[str, object]]:
    try:
        data = request_endpoint(endpoint_key, params=params)
    except (
        HTTPError,
        URLError,
        TimeoutError,
        ValueError,
        json.JSONDecodeError,
        ET.ParseError,
        ProviderUnavailableError,
    ) as exc:
        return [{
            "error": f"{endpoint_key} 조회 실패: {type(exc).__name__}: {exc}",
        }]

    return flatten_items(data)


def search_complaint_bigdata_documents(query: str, display: int = DEFAULT_DISPLAY) -> List[Dict[str, object]]:
    """
    국민권익위 민원빅데이터 API 결과를 main.py의 RAG 결과와 합치기 쉬운 문서 목록으로 변환한다.
    법적 근거가 아니라 유사 민원/연관어/분류 보강 자료로만 사용한다.
    """
    keyword = normalize_keyword(query)
    if not keyword:
        return []

    dates = default_date_range(days=365)
    searchword = compact_text(query, max_chars=95)
    target = "pttn,dfpt,saeol"

    lookup_params = {
        "similar_cases": {
            "searchword": searchword,
            "startPos": "1",
            "retCount": str(display),
            "target": "qna,qna_origin",
            "minScore": "1",
        },
        "related_words": {
            "searchword": keyword,
            "dateFrom": dates["startDate"],
            "dateTo": dates["endDate"],
            "resultCount": str(display),
            "target": target,
            "omitDuplicate": "true",
        },
        "top_keywords": {
            "dateFrom": dates["startDate"],
            "dateTo": dates["endDate"],
            "resultCount": str(display),
            "target": target,
        },
    }

    lookups = [
        ("similar_cases", "국민권익위 민원빅데이터 유사사례"),
        ("related_words", "국민권익위 민원빅데이터 연관어"),
        ("top_keywords", "국민권익위 민원빅데이터 핵심 키워드"),
    ]

    documents = []
    for endpoint_key, title in lookups:
        records = safe_request(endpoint_key, params=lookup_params[endpoint_key])

        if records and "error" in records[0]:
            continue
        elif records and is_error_record(records[0]):
            continue
        if not records:
            continue
        content = summarize_records(title, records, max_items=display)

        documents.append({
            "title": title,
            "source_type": "COMPLAINT_BIGDATA_API",
            "source_name": endpoint_key,
            "content": content,
            "matched_terms": [keyword],
        })

    return documents


def debug_complaint_bigdata_requests(query: str, display: int = DEFAULT_DISPLAY) -> List[Dict[str, object]]:
    keyword = normalize_keyword(query)
    dates = default_date_range(days=365)
    searchword = compact_text(query, max_chars=95)
    lookup_params = {
        "similar_cases": {
            "searchword": searchword,
            "startPos": "1",
            "retCount": str(display),
            "target": "qna,qna_origin",
            "minScore": "1",
        },
        "related_words": {
            "searchword": keyword,
            "dateFrom": dates["startDate"],
            "dateTo": dates["endDate"],
            "resultCount": str(display),
            "target": "pttn,dfpt,saeol",
            "omitDuplicate": "true",
        },
        "top_keywords": {
            "dateFrom": dates["startDate"],
            "dateTo": dates["endDate"],
            "resultCount": str(display),
            "target": "pttn,dfpt,saeol",
        },
    }

    debug_rows = []
    for endpoint_key in ["similar_cases", "related_words", "top_keywords"]:
        records = safe_request(endpoint_key, params=lookup_params[endpoint_key])
        debug_rows.append({
            "endpoint": endpoint_key,
            "records": records,
        })

    return debug_rows


def main():
    query = " ".join(sys.argv[1:]).strip() or "소음 마이크 데시벨"
    print("[국민권익위 민원빅데이터 API 검색 테스트]")
    print(f"- 검색어: {query}")

    documents = search_complaint_bigdata_documents(query)
    if not documents:
        print("- 검색 결과 없음 또는 API 조회 실패")
        for row in debug_complaint_bigdata_requests(query):
            records = row["records"]
            if records and "error" in records[0]:
                print(f"- {row['endpoint']}: {records[0]['error']}")
            else:
                print(f"- {row['endpoint']}: records={len(records)}")
        return

    for document in documents:
        print(f"\n[{document['title']}]")
        print(document["content"])


if __name__ == "__main__":
    main()
