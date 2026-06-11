from __future__ import annotations

import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List
from urllib.parse import urlencode
from urllib.request import Request, urlopen

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    def load_dotenv(*_args, **_kwargs):
        return False

from data.API.provider_runtime import execute_provider_call


LAW_API_BASE_URL = "https://www.law.go.kr/DRF/lawSearch.do"
LAW_SERVICE_BASE_URL = "https://www.law.go.kr/DRF/lawService.do"
LAW_SITE_BASE_URL = "https://www.law.go.kr"
DEFAULT_MUNICIPALITY = "아산시"

API_DIR = Path(__file__).resolve().parent
BASE_DIR = API_DIR.parents[1]
ENV_PATH = BASE_DIR / ".env"
CACHE_DIR = BASE_DIR / ".cache"
LAW_DETAIL_CACHE_FILE = CACHE_DIR / "law_api_details.json"
DETAIL_MAX_CHARS = 2500

TARGETS = {
    "law": "law",
    "ordinance": "ordin",
}

SERVICE_TARGETS = {
    "LAW_API": "law",
    "ORDINANCE_API": "ordin",
}

QUERY_PROFILES = [
    {
        "terms": ["불법주정차", "주정차", "주차", "견인", "교통", "보호구역"],
        "law_queries": ["도로교통법", "주차장법"],
        "ordinance_queries": ["아산시 주차", "아산시 견인", "아산시 교통안전"],
    },
    {
        "terms": ["도로 파손", "도로파손", "포트홀", "보도", "가로등", "도로"],
        "law_queries": ["도로법"],
        "ordinance_queries": ["아산시 도로", "아산시 도로점용", "아산시 도로복구"],
    },
    {
        "terms": ["쓰레기", "폐기물", "생활폐기물", "무단투기", "재활용"],
        "law_queries": ["폐기물관리법"],
        "ordinance_queries": ["아산시 폐기물", "아산시 생활폐기물", "아산시 재활용"],
    },
    {
        "terms": ["소음", "진동", "공사 소음", "생활소음"],
        "law_queries": ["소음ㆍ진동관리법"],
        "ordinance_queries": ["아산시 소음", "아산시 생활소음"],
    },
    {
        "terms": ["화장실", "공중화장실", "위생", "식품", "감염병", "방역"],
        "law_queries": ["공중화장실 등에 관한 법률", "공중위생관리법", "식품위생법", "감염병의 예방 및 관리에 관한 법률"],
        "ordinance_queries": ["아산시 공중화장실", "아산시 공중위생", "아산시 식품위생"],
    },
    {
        "terms": ["공원", "녹지", "가로수", "놀이터", "어린이놀이시설"],
        "law_queries": ["도시공원 및 녹지 등에 관한 법률", "어린이놀이시설 안전관리법"],
        "ordinance_queries": ["아산시 도시공원", "아산시 녹지", "아산시 가로수"],
    },
    {
        "terms": ["하수도", "상수도", "누수", "배수", "맨홀"],
        "law_queries": ["하수도법", "수도법"],
        "ordinance_queries": ["아산시 하수도", "아산시 수도"],
    },
    {
        "terms": ["건축", "건물", "불법건축", "공동주택", "주택", "균열"],
        "law_queries": ["건축법", "건축물관리법", "주택법", "공동주택관리법"],
        "ordinance_queries": ["아산시 건축", "아산시 공동주택", "아산시 주택"],
    },
    {
        "terms": ["악취", "오염", "대기", "먼지", "비산먼지", "환경"],
        "law_queries": ["악취방지법", "대기환경보전법", "물환경보전법"],
        "ordinance_queries": ["아산시 악취", "아산시 환경"],
    },
    {
        "terms": ["동물", "반려견", "가축", "축산", "가축분뇨"],
        "law_queries": ["동물보호법", "가축분뇨의 관리 및 이용에 관한 법률", "가축전염병 예방법"],
        "ordinance_queries": ["아산시 동물보호", "아산시 축산", "아산시 가축분뇨"],
    },
    {
        "terms": ["장애인", "노인", "복지", "교통약자", "편의시설"],
        "law_queries": ["장애인복지법", "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률", "교통약자의 이동편의 증진법"],
        "ordinance_queries": ["아산시 장애인", "아산시 노인", "아산시 교통약자"],
    },
    {
        "terms": ["재난", "안전", "위험", "화학물질", "위험물", "시설물"],
        "law_queries": ["재난 및 안전관리 기본법", "화학물질관리법", "위험물안전관리법", "시설물의 안전 및 유지관리에 관한 특별법"],
        "ordinance_queries": ["아산시 재난", "아산시 안전관리", "아산시 시설물"],
    },
    {
        "terms": ["민원", "처리기간", "정보공개", "개인정보", "행정절차", "이의신청"],
        "law_queries": ["민원 처리에 관한 법률", "행정절차법", "개인정보 보호법", "공공기관의 정보공개에 관한 법률"],
        "ordinance_queries": ["아산시 민원", "아산시 행정정보공개", "아산시 개인정보"],
    },
]


def get_law_api_key() -> str:
    load_dotenv(ENV_PATH)
    api_key = os.getenv("LAW_API_OC", "").strip()
    if not api_key:
        raise ValueError("LAW_API_OC is required for the National Law Information Center Open API")
    return api_key


def unique_values(values: List[str]) -> List[str]:
    result: List[str] = []
    for value in values:
        value = value.strip()
        if value and value not in result:
            result.append(value)
    return result


def remove_municipality(query: str) -> str:
    return query.replace(DEFAULT_MUNICIPALITY, "").strip()


def ensure_municipality_query(query: str) -> str:
    if DEFAULT_MUNICIPALITY in query:
        return query.strip()
    return f"{DEFAULT_MUNICIPALITY} {query.strip()}".strip()


def build_search_plan(query: str) -> Dict[str, List[str]]:
    normalized_query = re.sub(r"\s+", " ", query).strip()
    law_queries: List[str] = []
    ordinance_queries: List[str] = []

    for profile in QUERY_PROFILES:
        if any(term in normalized_query for term in profile["terms"]):
            law_queries.extend(profile["law_queries"])
            ordinance_queries.extend(profile["ordinance_queries"])

    if not law_queries:
        law_query = remove_municipality(normalized_query)
        if law_query:
            law_queries.append(law_query)

    if not ordinance_queries:
        ordinance_queries.append(ensure_municipality_query(normalized_query))

    return {
        "law_queries": unique_values(law_queries),
        "ordinance_queries": unique_values(ordinance_queries),
    }


def request_law_search(query: str, target: str, display: int = 5) -> ET.Element:
    if target not in TARGETS:
        raise ValueError(f"Unsupported law search target: {target}")
    params = {
        "OC": get_law_api_key(),
        "target": TARGETS[target],
        "query": query,
        "display": str(display),
        "type": "XML",
    }
    request = Request(
        f"{LAW_API_BASE_URL}?{urlencode(params)}",
        headers={"User-Agent": "egov-rag-complaint/1.0"},
    )
    body = execute_provider_call("law-api", 50, lambda: read_response(request)).value
    return ET.fromstring(body)


def request_law_detail(law_id: str, source_type: str) -> ET.Element:
    target = SERVICE_TARGETS.get(source_type)
    if not target:
        raise ValueError(f"Unsupported law detail source_type: {source_type}")
    id_param = "MST" if source_type == "LAW_API" else "ID"
    params = {
        "OC": get_law_api_key(),
        "target": target,
        id_param: law_id,
        "type": "XML",
    }
    request = Request(
        f"{LAW_SERVICE_BASE_URL}?{urlencode(params)}",
        headers={"User-Agent": "egov-rag-complaint/1.0"},
    )
    body = execute_provider_call("law-api", 100, lambda: read_response(request)).value
    return ET.fromstring(body)


def read_response(request: Request) -> bytes:
    timeout = float(os.getenv("PUBLIC_API_TIMEOUT_SECONDS", "10"))
    with urlopen(request, timeout=timeout) as response:
        return response.read()


def child_text(element: ET.Element, names: List[str]) -> str:
    direct = {child.tag.rsplit("}", 1)[-1]: child for child in list(element)}
    for name in names:
        child = direct.get(name)
        if child is not None and child.text:
            return child.text.strip()
    return ""


def mask_open_api_key(text: str) -> str:
    return re.sub(r"OC=[^&]+", "OC=***", text)


def format_detail_link(detail_link: str, mask_key: bool = True) -> str:
    if not detail_link:
        return ""
    url = detail_link if detail_link.startswith(("http://", "https://")) else f"{LAW_SITE_BASE_URL}{detail_link}"
    return mask_open_api_key(url) if mask_key else url


def compact_text(text: str, max_chars: int = DETAIL_MAX_CHARS) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) <= max_chars:
        return text
    return text[:max_chars].rstrip() + " ..."


def element_text_content(root: ET.Element) -> str:
    return compact_text(" ".join(text.strip() for text in root.itertext() if text.strip()))


def load_detail_cache() -> Dict[str, str]:
    if not LAW_DETAIL_CACHE_FILE.exists():
        return {}
    try:
        return json.loads(LAW_DETAIL_CACHE_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def save_detail_cache(cache: Dict[str, str]) -> None:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    LAW_DETAIL_CACHE_FILE.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")


def cache_key(item: Dict[str, str]) -> str:
    return f"{item.get('source_type', '')}:{item.get('law_id', '')}"


def fetch_detail_text(item: Dict[str, str], cache: Dict[str, str]) -> str:
    key = cache_key(item)
    if not key or key.endswith(":"):
        return ""
    if key in cache:
        return cache[key]
    root = request_law_detail(item["law_id"], item["source_type"])
    detail_text = element_text_content(root)
    cache[key] = detail_text
    save_detail_cache(cache)
    return detail_text


def enrich_items_with_details(items: List[Dict[str, str]]) -> List[Dict[str, str]]:
    cache = load_detail_cache()
    enriched = []
    for item in items:
        try:
            detail_text = fetch_detail_text(item, cache)
        except Exception as exc:
            print(f"[law-api] detail request failed ({item.get('title')}): {exc}")
            detail_text = ""
        if detail_text:
            item = {
                **item,
                "detail_text": detail_text,
                "content": f"{item.get('content', '')}\n\n본문 발췌:\n{detail_text}",
            }
        enriched.append(item)
    return enriched


def parse_law_items(root: ET.Element, source_type: str) -> List[Dict[str, str]]:
    item_tag = ".//law" if source_type == "LAW_API" else ".//ordin"
    items: List[Dict[str, str]] = []
    for item in root.findall(item_tag):
        title = child_text(item, ["법령명한글", "자치법규명", "조례명"])
        if not title:
            continue
        law_id = child_text(item, ["법령일련번호", "자치법규ID", "ID"])
        department = child_text(item, ["소관부처명", "소관부처", "지자체명"])
        detail_link = child_text(item, ["법령상세링크", "상세링크"])
        effective_date = child_text(item, ["시행일자", "공포일자"])
        content_parts = [
            f"문서명: {title}",
            f"문서 유형: {source_type}",
        ]
        if department:
            content_parts.append(f"소관/지자체: {department}")
        if effective_date:
            content_parts.append(f"시행/공포일자: {effective_date}")
        if detail_link:
            content_parts.append(f"상세링크: {format_detail_link(detail_link)}")
        items.append({
            "title": title,
            "source_type": source_type,
            "source_name": department,
            "content": "\n".join(content_parts),
            "law_id": law_id,
            "detail_link": detail_link,
            "effective_date": effective_date,
        })
    return items


def search_laws(query: str, display: int = 5) -> List[Dict[str, str]]:
    return parse_law_items(request_law_search(query=query, target="law", display=display), "LAW_API")


def search_ordinances(query: str, display: int = 5) -> List[Dict[str, str]]:
    return parse_law_items(request_law_search(query=query, target="ordinance", display=display), "ORDINANCE_API")


def dedupe_results(items: List[Dict[str, str]]) -> List[Dict[str, str]]:
    deduped: List[Dict[str, str]] = []
    seen = set()
    for item in items:
        key = (item.get("source_type"), item.get("law_id"), item.get("title"))
        if key in seen:
            continue
        seen.add(key)
        deduped.append(item)
    return deduped


def search_law_documents(query: str, display: int = 3, include_details: bool = True) -> List[Dict[str, str]]:
    results: List[Dict[str, str]] = []
    plan = build_search_plan(query)
    for law_query in plan["law_queries"]:
        try:
            results.extend(search_laws(law_query, display=display))
        except Exception as exc:
            print(f"[law-api] national law search failed ({law_query}): {exc}")
    for ordinance_query in plan["ordinance_queries"]:
        try:
            results.extend(search_ordinances(ordinance_query, display=display))
        except Exception as exc:
            print(f"[law-api] ordinance search failed ({ordinance_query}): {exc}")
    results = dedupe_results(results)
    return enrich_items_with_details(results) if include_details else results


def print_results(label: str, items: List[Dict[str, str]]) -> None:
    print(f"\n[{label}] {len(items)}건")
    if not items:
        print("- 검색 결과 없음")
        return
    for index, item in enumerate(items, start=1):
        print(f"{index}. {item['title']}")
        if item.get("source_name"):
            print(f"   - 소관/지자체: {item['source_name']}")
        if item.get("law_id"):
            print(f"   - ID: {item['law_id']}")
        if item.get("detail_link"):
            print(f"   - 링크: {format_detail_link(item['detail_link'])}")
        if item.get("detail_text"):
            print(f"   - 본문 발췌: {item['detail_text'][:160]}...")


def main() -> None:
    query = " ".join(sys.argv[1:]).strip() or "도로법"
    print("[국가법령정보 Open API 검색 테스트]")
    print(f"- 검색어: {query}")
    plan = build_search_plan(query)
    print(f"- 국가 법령 검색어: {', '.join(plan['law_queries'])}")
    print(f"- 자치법규 검색어: {', '.join(plan['ordinance_queries'])}")
    law_items = dedupe_results([item for law_query in plan["law_queries"] for item in search_laws(law_query)])
    ordinance_items = dedupe_results([item for ordinance_query in plan["ordinance_queries"] for item in search_ordinances(ordinance_query)])
    print_results("국가 법령", enrich_items_with_details(law_items))
    print_results("자치법규", enrich_items_with_details(ordinance_items))


if __name__ == "__main__":
    main()
