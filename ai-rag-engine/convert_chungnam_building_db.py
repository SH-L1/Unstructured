"""Convert Chungnam building DB text files into Asan address CSV.

The address-information building DB is distributed as pipe-delimited text.
This converter keeps only Asan-si rows and writes normalized columns consumed
by sync_spatial_sources.py. The source has address/building identifiers but no
trusted coordinates, so latitude/longitude are intentionally left empty.
"""

from __future__ import annotations

import csv
import os
from pathlib import Path

from dotenv import load_dotenv

from spatial_data_common import clean


load_dotenv()


DEFAULT_SOURCE_DIR = "data/spatial/asan_address"
DEFAULT_OUTPUT = "data/spatial/asan_address_points.csv"


def source_dir() -> Path:
    return Path(clean(os.getenv("BUILDING_DB_SOURCE_DIR")) or DEFAULT_SOURCE_DIR)


def output_file() -> Path:
    return Path(
        clean(os.getenv("BUILDING_DB_ADDRESS_CSV"))
        or clean(os.getenv("SPATIAL_ADDRESS_POINTS_CSV"))
        or DEFAULT_OUTPUT
    )


def open_text(path: Path):
    last_error: UnicodeDecodeError | None = None
    for encoding in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            handle = path.open("r", encoding=encoding, newline="")
            handle.readline()
            handle.seek(0)
            return handle
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"Could not decode building DB file: {path}") from last_error


def text_at(parts: list[str], index: int) -> str:
    if index >= len(parts):
        return ""
    return parts[index].strip()


def number_text(value: str) -> str:
    value = value.strip()
    if not value:
        return ""
    try:
        return str(int(value))
    except ValueError:
        return value


def join_nonempty(*values: str) -> str:
    return " ".join(value for value in values if value)


def build_road_address(parts: list[str]) -> str:
    sido = text_at(parts, 1)
    sigungu = text_at(parts, 2)
    road_name = text_at(parts, 9)
    underground = text_at(parts, 10)
    building_main = number_text(text_at(parts, 11))
    building_sub = number_text(text_at(parts, 12))
    if not road_name or not building_main:
        return ""
    building_no = building_main if not building_sub or building_sub == "0" else f"{building_main}-{building_sub}"
    if underground == "1":
        building_no = f"지하 {building_no}"
    return join_nonempty(sido, sigungu, road_name, building_no)


def build_jibun_address(parts: list[str]) -> str:
    sido = text_at(parts, 1)
    sigungu = text_at(parts, 2)
    legal_dong = text_at(parts, 3)
    legal_ri = text_at(parts, 4)
    mountain = "산" if text_at(parts, 5) == "1" else ""
    lot_main = number_text(text_at(parts, 6))
    lot_sub = number_text(text_at(parts, 7))
    if not lot_main:
        return ""
    lot_no = lot_main if not lot_sub or lot_sub == "0" else f"{lot_main}-{lot_sub}"
    return join_nonempty(sido, sigungu, legal_dong, legal_ri, mountain + lot_no)


def normalize_building(parts: list[str]) -> dict[str, str]:
    return {
        "road_address": build_road_address(parts),
        "jibun_address": build_jibun_address(parts),
        "building_name": text_at(parts, 25),
        "building_id": text_at(parts, 15),
        "road_name": text_at(parts, 9),
        "admin_code": text_at(parts, 17),
        "legal_dong_code": text_at(parts, 0),
        "admin_name": text_at(parts, 18),
        "legal_dong_name": join_nonempty(text_at(parts, 3), text_at(parts, 4)),
        "latitude": "",
        "longitude": "",
    }


def is_asan(parts: list[str]) -> bool:
    legal_code = text_at(parts, 0)
    sigungu = text_at(parts, 2)
    return legal_code.startswith("44200") or sigungu == "아산시" or "아산" in sigungu


def convert() -> tuple[Path, int]:
    source = source_dir() / "build_chungnam.txt"
    if not source.exists():
        raise FileNotFoundError(f"Missing Chungnam building DB file: {source}")
    target = output_file()
    target.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with open_text(source) as handle, target.open("w", encoding="utf-8-sig", newline="") as output:
        fieldnames = [
            "road_address",
            "jibun_address",
            "building_name",
            "building_id",
            "road_name",
            "admin_code",
            "legal_dong_code",
            "admin_name",
            "legal_dong_name",
            "latitude",
            "longitude",
        ]
        writer = csv.DictWriter(output, fieldnames=fieldnames)
        writer.writeheader()
        for line in handle:
            parts = line.rstrip("\n\r").split("|")
            if not is_asan(parts):
                continue
            writer.writerow(normalize_building(parts))
            count += 1
    return target, count


if __name__ == "__main__":
    output, rows = convert()
    print(f"asan_address_points: rows={rows} output={output}")
