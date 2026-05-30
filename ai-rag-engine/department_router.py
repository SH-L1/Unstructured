import os
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Dict, List
from xml.etree import ElementTree as ET
from zipfile import ZipFile

from dotenv import load_dotenv


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_HISTORY_FILE = BASE_DIR / "data" / "department_history" / "complaint_department_history.xlsx"

NS = {
    "a": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}

STOPWORDS = {
    "민원",
    "관련",
    "요청",
    "확인",
    "처리",
    "부탁",
    "합니다",
    "해주세요",
    "관련된",
    "아산시",
    "충청남도",
    "때문에",
    "어렵습니다",
}

SYNONYM_TERMS = {
    "불법주차": ["주차", "주정차", "정차", "견인", "교통", "도로교통"],
    "불법주정차": ["주차", "주정차", "정차", "견인", "교통", "도로교통"],
    "주차": ["주정차", "정차", "견인", "교통"],
    "우회전": ["교통", "통행", "도로교통", "차량"],
    "도로": ["도로", "교통", "건설"],
    "포트홀": ["도로", "파손", "보수", "교통"],
    "쓰레기": ["폐기물", "생활폐기물", "환경", "청소"],
    "폐기물": ["쓰레기", "생활폐기물", "환경", "청소"],
    "악취": ["냄새", "환경", "위생"],
    "소음": ["환경", "생활소음", "공사"],
}

FALLBACK_DEPARTMENTS = [
    {
        "terms": ["불법주차", "불법주정차", "주차", "주정차", "견인"],
        "department": "교통행정과",
    },
    {
        "terms": ["도로", "포트홀", "도로파손", "도로 파손", "보도블록"],
        "department": "도로관리과",
    },
    {
        "terms": ["쓰레기", "폐기물", "폐가구", "무단투기", "불법투기", "악취"],
        "department": "자원순환과",
    },
]


def get_history_file() -> Path:
    load_dotenv()
    configured = os.getenv("DEPARTMENT_HISTORY_XLSX", "").strip()

    if configured:
        path = Path(configured)
        if not path.is_absolute():
            path = BASE_DIR / path
        return path

    return DEFAULT_HISTORY_FILE


def column_number(cell_ref: str) -> int:
    match = re.match(r"([A-Z]+)", cell_ref)
    if not match:
        return 1

    number = 0
    for char in match.group(1):
        number = number * 26 + ord(char) - 64
    return number


def load_shared_strings(zip_file: ZipFile) -> List[str]:
    if "xl/sharedStrings.xml" not in zip_file.namelist():
        return []

    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    shared_strings = []

    for item in root.findall("a:si", NS):
        parts = []
        for text in item.findall(".//a:t", NS):
            parts.append(text.text or "")
        shared_strings.append("".join(parts))

    return shared_strings


def read_cell_value(cell: ET.Element, shared_strings: List[str]) -> str:
    cell_type = cell.attrib.get("t")

    if cell_type == "inlineStr":
        inline = cell.find("a:is/a:t", NS)
        return inline.text.strip() if inline is not None and inline.text else ""

    value = cell.find("a:v", NS)
    if value is None or value.text is None:
        return ""

    raw_value = value.text.strip()

    if cell_type == "s" and raw_value.isdigit():
        index = int(raw_value)
        if index < len(shared_strings):
            return shared_strings[index].strip()

    return raw_value


def row_values(row: ET.Element, shared_strings: List[str]) -> List[str]:
    values = []
    last_column = 0

    for cell in row.findall("a:c", NS):
        current_column = column_number(cell.attrib.get("r", "A"))

        while last_column + 1 < current_column:
            values.append("")
            last_column += 1

        values.append(read_cell_value(cell, shared_strings))
        last_column = current_column

    return values


def workbook_sheet_paths(zip_file: ZipFile) -> Dict[str, str]:
    workbook = ET.fromstring(zip_file.read("xl/workbook.xml"))
    rels = ET.fromstring(zip_file.read("xl/_rels/workbook.xml.rels"))

    rel_map = {}
    for rel in rels.findall("rel:Relationship", NS):
        rel_id = rel.attrib.get("Id")
        target = rel.attrib.get("Target", "")
        if rel_id and target:
            rel_map[rel_id] = "xl/" + target.lstrip("/")

    sheet_paths = {}
    for sheet in workbook.findall(".//a:sheet", NS):
        name = sheet.attrib.get("name", "")
        rel_id = sheet.attrib.get(f"{{{NS['r']}}}id")
        path = rel_map.get(rel_id)
        if name and path:
            sheet_paths[name] = path

    return sheet_paths


def load_department_history(path: Path = None) -> List[Dict[str, str]]:
    path = path or get_history_file()

    if not path.exists():
        raise FileNotFoundError(f"민원-부서 이력 엑셀 파일이 없습니다: {path}")

    records = []

    with ZipFile(path) as zip_file:
        shared_strings = load_shared_strings(zip_file)
        sheet_paths = workbook_sheet_paths(zip_file)

        for sheet_name, sheet_path in sheet_paths.items():
            root = ET.fromstring(zip_file.read(sheet_path))
            header = None

            for row in root.findall(".//a:row", NS):
                values = row_values(row, shared_strings)

                if not values:
                    continue

                if "민원명" in values and "민원요지" in values and "처리부서" in values:
                    header = values
                    continue

                if not header:
                    continue

                row_data = dict(zip(header, values))
                title = row_data.get("민원명", "").strip()
                summary = row_data.get("민원요지", "").strip()
                department = row_data.get("처리부서", "").strip()

                if not department or not (title or summary):
                    continue

                records.append({
                    "year_sheet": sheet_name,
                    "title": title,
                    "summary": summary,
                    "region": row_data.get("민원발생지역", "").strip(),
                    "department": department,
                    "status": row_data.get("처리상태", "").strip(),
                    "search_text": f"{title} {summary} {row_data.get('민원발생지역', '')}".strip(),
                })

    return records


def tokenize(text: str) -> List[str]:
    normalized = re.sub(r"[^0-9A-Za-z가-힣]+", " ", text)
    tokens = []

    for token in normalized.split():
        token = token.strip()
        token = re.sub(r"(입니다|합니다|해주세요|하세요|되는|된다|라고|으로|에서|에게|에는|은|는|이|가|을|를|도|로)$", "", token)
        if len(token) < 2:
            continue
        if token in STOPWORDS:
            continue
        tokens.append(token)

    expanded_tokens = []
    for token in tokens:
        expanded_tokens.append(token)
        expanded_tokens.extend(SYNONYM_TERMS.get(token, []))

        for key, synonyms in SYNONYM_TERMS.items():
            if key in token:
                expanded_tokens.extend(synonyms)

    return list(dict.fromkeys(expanded_tokens))


def score_record(query_tokens: List[str], record: Dict[str, str]) -> int:
    target = record["search_text"]
    score = 0

    for token in query_tokens:
        if token in target:
            score += 1

    return score


def fallback_department(complaint_text: str) -> str:
    for item in FALLBACK_DEPARTMENTS:
        if any(term in complaint_text for term in item["terms"]):
            return item["department"]
    return ""


def recommend_department(complaint_text: str, top_k: int = 5) -> Dict[str, object]:
    records = load_department_history()
    query_tokens = tokenize(complaint_text)
    scored = []

    for record in records:
        score = score_record(query_tokens, record)
        if score <= 0:
            continue

        scored.append({
            **record,
            "score": score,
        })

    scored.sort(key=lambda item: item["score"], reverse=True)
    top_matches = scored[:top_k]
    department_counter = Counter()

    for match in top_matches:
        department_counter[match["department"]] += match["score"]

    recommended_department = ""
    if department_counter:
        recommended_department = department_counter.most_common(1)[0][0]
    else:
        recommended_department = fallback_department(complaint_text)

    return {
        "recommended_department": recommended_department,
        "recommendation_source": "history" if department_counter else "keyword_fallback" if recommended_department else "",
        "query_tokens": query_tokens,
        "top_matches": [
            {
                "score": item["score"],
                "department": item["department"],
                "title": item["title"],
                "summary": item["summary"][:160],
                "region": item["region"],
                "year_sheet": item["year_sheet"],
            }
            for item in top_matches
        ],
    }


def main() -> None:
    complaint_text = " ".join(sys.argv[1:]).strip()

    if not complaint_text:
        complaint_text = "탕정역 앞 불법주차 때문에 우회전이 어렵습니다."

    result = recommend_department(complaint_text)

    print("[과거 민원 기반 담당 부서 추천]")
    print(f"- 민원 본문: {complaint_text}")
    print(f"- 추천 부서: {result['recommended_department'] or '추천 결과 없음'}")
    print(f"- 검색 토큰: {', '.join(result['query_tokens'])}")
    print("\n[유사 과거 민원]")

    if not result["top_matches"]:
        print("- 유사 민원 없음")
        return

    for index, item in enumerate(result["top_matches"], start=1):
        print(f"{index}. score={item['score']} / {item['department']}")
        print(f"   - 민원명: {item['title']}")
        print(f"   - 요지: {item['summary']}")
        print(f"   - 지역: {item['region']} / 시트: {item['year_sheet']}")


if __name__ == "__main__":
    main()
