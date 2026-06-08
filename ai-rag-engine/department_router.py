"""Auxiliary department recommendation from approved local datasets.

This module is intentionally deterministic. It can use reviewed Saeol history,
Asan complaint manuals, and department-history workbooks as routing evidence,
but it never treats those records as legal authority.
"""

from __future__ import annotations

import csv
import json
import os
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree as ET
from zipfile import BadZipFile, ZipFile

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    def load_dotenv() -> bool:
        return False


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_SAEOL_DIR = BASE_DIR / "data" / "saeol"
DEFAULT_MANUAL_DIR = BASE_DIR / "data" / "minwon_manuals"

NS = {
    "a": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}

TOKEN_RE = re.compile(r"[0-9A-Za-z가-힣]+")
HEADER_TITLE = ("민원명", "제목", "title", "subject")
HEADER_SUMMARY = ("민원요지", "민원내용", "내용", "summary", "content", "body")
HEADER_DEPARTMENT = ("처리부서", "담당부서", "부서", "department", "dept")
HEADER_REGION = ("민원발생지역", "지역", "위치", "주소", "region", "location")

STOPWORDS = {
    "민원",
    "요청",
    "처리",
    "확인",
    "관련",
    "부서",
    "아산시",
    "충청남도",
    "합니다",
    "주세요",
}

SYNONYM_TERMS = {
    "불법주정차": ["주정차", "주차", "차량", "견인", "교통"],
    "주정차": ["불법주정차", "주차", "차량", "교통"],
    "포트홀": ["도로", "파손", "보수", "보도"],
    "도로파손": ["도로", "포트홀", "보수", "보도"],
    "쓰레기": ["폐기물", "무단투기", "청소", "자원순환"],
    "무단투기": ["쓰레기", "폐기물", "청소", "자원순환"],
    "소음": ["진동", "확성기", "공사소음", "환경"],
    "악취": ["대기", "오염", "환경", "위생"],
    "현수막": ["광고물", "옥외광고", "도시관리"],
}

FALLBACK_DEPARTMENTS = (
    (("주정차", "주차", "견인", "차량", "교통"), "교통행정과"),
    (("도로", "포트홀", "보도", "파손", "맨홀"), "도로관리과"),
    (("쓰레기", "폐기물", "무단투기", "청소", "재활용"), "자원순환과"),
    (("소음", "악취", "대기", "수질", "오염", "환경"), "환경보전과"),
    (("건축", "주택", "아파트", "공동주택", "불법건축"), "건축과"),
    (("공원", "녹지", "가로수", "놀이터"), "공원녹지과"),
    (("상수도", "하수도", "수도", "단수", "하수", "맨홀"), "상하수도과"),
    (("보건", "위생", "식품", "방역", "감염병"), "보건행정과"),
    (("동물", "유기견", "반려견", "축산", "가축"), "축산과"),
    (("현수막", "광고물", "노점", "적치물"), "도시관리과"),
    (("장애인", "노인", "복지", "교통약자"), "사회복지과"),
    (("재난", "안전", "위험", "화학", "폭발"), "안전총괄과"),
)


@dataclass(frozen=True)
class DepartmentRecord:
    source_type: str
    source_file: str
    title: str
    summary: str
    department: str
    region: str = ""
    year_sheet: str = ""
    weight: int = 1

    @property
    def search_text(self) -> str:
        return " ".join((self.title, self.summary, self.region, self.department))


def configured_paths(env_name: str, default: Path | None = None) -> list[Path]:
    configured = os.getenv(env_name, "").strip()
    raw_paths = [configured] if configured else ([str(default)] if default else [])
    paths: list[Path] = []
    for raw in raw_paths:
        if not raw:
            continue
        path = Path(raw)
        if not path.is_absolute():
            path = BASE_DIR / path
        paths.append(path)
    return paths


def approved_for_auxiliary_use() -> bool:
    value = os.getenv("DEPARTMENT_HISTORY_APPROVED_FOR_AUXILIARY_USE", "").strip().lower()
    return value in {"1", "true", "yes"}


def iter_existing_files(paths: Iterable[Path], suffixes: set[str] | None = None) -> Iterable[Path]:
    for path in paths:
        if not path.exists():
            continue
        if path.is_file():
            if suffixes is None or path.suffix.lower() in suffixes:
                yield path
            continue
        for child in path.rglob("*"):
            if child.is_file() and (suffixes is None or child.suffix.lower() in suffixes):
                yield child


def read_text_file(path: Path) -> str:
    last_error: Exception | None = None
    for encoding in ("utf-8-sig", "utf-8", "cp949", "euc-kr"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"Could not decode {path}") from last_error


def load_shared_strings(zip_file: ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in zip_file.namelist():
        return []
    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    values: list[str] = []
    for item in root.findall("a:si", NS):
        values.append("".join(text.text or "" for text in item.findall(".//a:t", NS)).strip())
    return values


def column_number(cell_ref: str) -> int:
    match = re.match(r"([A-Z]+)", cell_ref)
    if not match:
        return 1
    number = 0
    for char in match.group(1):
        number = number * 26 + ord(char) - 64
    return number


def read_cell_value(cell: ET.Element, shared_strings: list[str]) -> str:
    cell_type = cell.attrib.get("t")
    if cell_type == "inlineStr":
        inline = cell.find("a:is/a:t", NS)
        return inline.text.strip() if inline is not None and inline.text else ""
    value = cell.find("a:v", NS)
    if value is None or value.text is None:
        return ""
    raw = value.text.strip()
    if cell_type == "s" and raw.isdigit() and int(raw) < len(shared_strings):
        return shared_strings[int(raw)]
    return raw


def row_values(row: ET.Element, shared_strings: list[str]) -> list[str]:
    values: list[str] = []
    last_column = 0
    for cell in row.findall("a:c", NS):
        current_column = column_number(cell.attrib.get("r", "A"))
        while last_column + 1 < current_column:
            values.append("")
            last_column += 1
        values.append(read_cell_value(cell, shared_strings))
        last_column = current_column
    return values


def workbook_sheet_paths(zip_file: ZipFile) -> dict[str, str]:
    workbook = ET.fromstring(zip_file.read("xl/workbook.xml"))
    rels = ET.fromstring(zip_file.read("xl/_rels/workbook.xml.rels"))
    rel_map = {
        rel.attrib.get("Id"): "xl/" + rel.attrib.get("Target", "").lstrip("/")
        for rel in rels.findall("rel:Relationship", NS)
        if rel.attrib.get("Id") and rel.attrib.get("Target")
    }
    sheet_paths: dict[str, str] = {}
    for sheet in workbook.findall(".//a:sheet", NS):
        name = sheet.attrib.get("name", "")
        rel_id = sheet.attrib.get(f"{{{NS['r']}}}id")
        path = rel_map.get(rel_id)
        if name and path:
            sheet_paths[name] = path
    return sheet_paths


def find_header(headers: list[str], candidates: tuple[str, ...]) -> str:
    normalized = {header.strip().casefold(): header for header in headers}
    for candidate in candidates:
        if candidate.casefold() in normalized:
            return normalized[candidate.casefold()]
    return ""


def load_department_history_file(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        raise FileNotFoundError(f"Department history file does not exist: {path}")
    records: list[dict[str, str]] = []
    with ZipFile(path) as zip_file:
        shared_strings = load_shared_strings(zip_file)
        for sheet_name, sheet_path in workbook_sheet_paths(zip_file).items():
            root = ET.fromstring(zip_file.read(sheet_path))
            header: list[str] | None = None
            header_map: dict[str, str] = {}
            for row in root.findall(".//a:row", NS):
                values = row_values(row, shared_strings)
                if not any(values):
                    continue
                if header is None:
                    title_key = find_header(values, HEADER_TITLE)
                    summary_key = find_header(values, HEADER_SUMMARY)
                    department_key = find_header(values, HEADER_DEPARTMENT)
                    if department_key and (title_key or summary_key):
                        header = values
                        header_map = {
                            "title": title_key,
                            "summary": summary_key,
                            "department": department_key,
                            "region": find_header(values, HEADER_REGION),
                        }
                    continue
                row_data = dict(zip(header, values))
                title = row_data.get(header_map["title"], "").strip() if header_map["title"] else ""
                summary = row_data.get(header_map["summary"], "").strip() if header_map["summary"] else ""
                department = row_data.get(header_map["department"], "").strip()
                if department and (title or summary):
                    records.append({
                        "source_file": path.name,
                        "year_sheet": sheet_name,
                        "title": title,
                        "summary": summary,
                        "region": row_data.get(header_map["region"], "").strip() if header_map["region"] else "",
                        "department": department,
                        "search_text": f"{title} {summary}",
                    })
    return records


def load_department_history(path: Path | None = None) -> list[dict[str, str]]:
    if not approved_for_auxiliary_use():
        if path is not None:
            raise RuntimeError("Historical complaint data is not approved for auxiliary use")
        return []
    paths = [path] if path else list(iter_existing_files(configured_paths("DEPARTMENT_HISTORY_XLSX"), {".xlsx"}))
    records: list[dict[str, str]] = []
    for history_file in paths:
        records.extend(load_department_history_file(history_file))
    return records


def department_from_manual_path(path: Path) -> str:
    text = " ".join(part for part in [path.stem, *path.parts[-4:-1]] if part)
    match = re.search(r"\(([^)]+과|[^)]+팀|[^)]+담당관|[^)]+센터)\)", text)
    if match:
        return match.group(1).strip()
    for token in TOKEN_RE.findall(text):
        if token.endswith(("과", "팀", "담당관", "센터")) and len(token) >= 3:
            return token
    return ""


def load_manual_records() -> list[DepartmentRecord]:
    paths = configured_paths("MINWON_MANUAL_DIR", DEFAULT_MANUAL_DIR)
    records: list[DepartmentRecord] = []
    for path in iter_existing_files(paths, {".hwp", ".hwpx", ".pdf", ".txt", ".md", ".docx"}):
        department = department_from_manual_path(path)
        if not department:
            continue
        records.append(DepartmentRecord(
            source_type="MINWON_MANUAL",
            source_file=path.name,
            title=path.stem,
            summary=" ".join(path.parts[-4:]),
            department=department,
            weight=3,
        ))
    return records


def records_from_mapping(item: dict[str, object], source_file: str, source_type: str) -> DepartmentRecord | None:
    department = first_value(item, HEADER_DEPARTMENT)
    title = first_value(item, HEADER_TITLE)
    summary = first_value(item, HEADER_SUMMARY)
    region = first_value(item, HEADER_REGION)
    if not department or not (title or summary):
        return None
    return DepartmentRecord(
        source_type=source_type,
        source_file=source_file,
        title=title,
        summary=summary,
        department=department,
        region=region,
        weight=4 if source_type == "SAEOL_PUBLIC_COMPLAINT" else 2,
    )


def first_value(item: dict[str, object], candidates: tuple[str, ...]) -> str:
    normalized = {str(key).strip().casefold(): value for key, value in item.items()}
    for candidate in candidates:
        value = normalized.get(candidate.casefold())
        if value is not None:
            return str(value).strip()
    return ""


def load_saeol_records() -> list[DepartmentRecord]:
    paths = configured_paths("SAEOL_PUBLIC_COMPLAINTS_FILE", DEFAULT_SAEOL_DIR)
    records: list[DepartmentRecord] = []
    for path in iter_existing_files(paths, {".json", ".jsonl", ".csv", ".txt"}):
        try:
            if path.suffix.lower() == ".json":
                payload = json.loads(read_text_file(path))
                items = payload if isinstance(payload, list) else [payload]
                for item in items:
                    if isinstance(item, dict):
                        record = records_from_mapping(item, path.name, "SAEOL_PUBLIC_COMPLAINT")
                        if record:
                            records.append(record)
            elif path.suffix.lower() == ".jsonl":
                for line in read_text_file(path).splitlines():
                    if not line.strip():
                        continue
                    item = json.loads(line)
                    if isinstance(item, dict):
                        record = records_from_mapping(item, path.name, "SAEOL_PUBLIC_COMPLAINT")
                        if record:
                            records.append(record)
            elif path.suffix.lower() == ".csv":
                with path.open("r", encoding="utf-8-sig", newline="") as handle:
                    for item in csv.DictReader(handle):
                        record = records_from_mapping(dict(item), path.name, "SAEOL_PUBLIC_COMPLAINT")
                        if record:
                            records.append(record)
            else:
                text = read_text_file(path)
                for index, block in enumerate(re.split(r"\n\s*\n", text), start=1):
                    department_match = re.search(r"(?:처리부서|담당부서)\s*[:：]\s*([^\n\r]+)", block)
                    if not department_match:
                        continue
                    department = department_match.group(1).strip()
                    title_match = re.search(r"(?:제목|민원명)\s*[:：]\s*([^\n\r]+)", block)
                    records.append(DepartmentRecord(
                        source_type="SAEOL_PUBLIC_COMPLAINT",
                        source_file=f"{path.name}#{index}",
                        title=title_match.group(1).strip() if title_match else block[:80],
                        summary=block[:1000],
                        department=department,
                        weight=4,
                    ))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError, csv.Error):
            continue
    return records


def load_auxiliary_records() -> list[DepartmentRecord]:
    records = [
        DepartmentRecord(
            source_type="DEPARTMENT_HISTORY",
            source_file=item.get("source_file", ""),
            year_sheet=item.get("year_sheet", ""),
            title=item.get("title", ""),
            summary=item.get("summary", ""),
            region=item.get("region", ""),
            department=item.get("department", ""),
            weight=5,
        )
        for item in load_department_history()
    ]
    records.extend(load_saeol_records())
    records.extend(load_manual_records())
    return records


def tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    for token in TOKEN_RE.findall(text or ""):
        token = token.casefold().strip()
        if len(token) < 2 or token in STOPWORDS:
            continue
        tokens.append(token)
        tokens.extend(SYNONYM_TERMS.get(token, []))
        for key, synonyms in SYNONYM_TERMS.items():
            if key in token:
                tokens.extend(synonyms)
    return list(dict.fromkeys(tokens))


def count_matches(query_tokens: list[str], record: DepartmentRecord | dict[str, str]) -> int:
    target = record.search_text if isinstance(record, DepartmentRecord) else record.get("search_text", "")
    target = target.casefold()
    return sum(1 for token in query_tokens if token.casefold() in target)


def fallback_department(complaint_text: str) -> str:
    normalized = complaint_text.casefold()
    for terms, department in FALLBACK_DEPARTMENTS:
        if any(term in normalized for term in terms):
            return department
    return ""


def recommend_department(complaint_text: str, top_k: int = 5) -> dict[str, object]:
    query_tokens = tokenize(complaint_text)
    ranked: list[dict[str, object]] = []
    for record in load_auxiliary_records():
        match_count = count_matches(query_tokens, record)
        if match_count <= 0:
            continue
        ranked.append({
            "department": record.department,
            "source_file": record.source_file,
            "year_sheet": record.year_sheet,
            "source_type": record.source_type,
            "match_count": match_count,
            "weighted_score": match_count * record.weight,
        })
    ranked.sort(key=lambda item: (int(item["weighted_score"]), int(item["match_count"])), reverse=True)
    top_matches = ranked[:top_k]
    department_counter: Counter[str] = Counter()
    for match in top_matches:
        department_counter[str(match["department"])] += int(match["weighted_score"])
    best_score = int(top_matches[0]["weighted_score"]) if top_matches else 0
    if department_counter and best_score >= 3:
        recommended_department = department_counter.most_common(1)[0][0]
        source = "auxiliary_data"
    else:
        recommended_department = fallback_department(complaint_text)
        source = "keyword_fallback" if recommended_department else ""
    return {
        "recommended_department": recommended_department,
        "recommendation_source": source,
        "top_matches": [
            {
                "department": item["department"],
                "source_file": item["source_file"],
                "year_sheet": item["year_sheet"],
                "source_type": item["source_type"],
                "match_count": item["match_count"],
            }
            for item in top_matches
        ],
    }


def main() -> None:
    load_dotenv()
    complaint_text = " ".join(sys.argv[1:]).strip() or "배방읍 도로에 포트홀이 생겨 보수가 필요합니다"
    result = recommend_department(complaint_text)
    print(json.dumps(result, ensure_ascii=False, indent=2))


load_dotenv()


if __name__ == "__main__":
    main()
