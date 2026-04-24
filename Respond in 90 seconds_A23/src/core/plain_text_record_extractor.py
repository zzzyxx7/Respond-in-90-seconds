import re
from typing import Any, Dict, List, Optional


_TEXT_FIELD_HINTS = ("城市", "名称", "地区", "单位", "公司", "企业")
_NUMERIC_TYPES = {
    "number",
    "money",
    "percentage",
    "population",
    "area",
    "weight",
    "date",
}
_SPECIAL_CITY_NAMES = (
    "北京",
    "上海",
    "天津",
    "重庆",
    "香港",
    "澳门",
)


def _strip_units(field_name: str) -> str:
    return re.sub(r"[（(].*?[）)]", "", str(field_name or "")).strip()


def _normalize_label(field_name: str) -> str:
    clean = _strip_units(field_name)
    clean = clean.replace(" ", "").replace("\u3000", "")
    return clean.lower()


def _extract_city_like_name(text: str) -> str:
    prefix = str(text or "").strip()[:40]
    if not prefix:
        return ""

    for name in _SPECIAL_CITY_NAMES:
        if prefix.startswith(name):
            return name

    m = re.match(
        r"^([一-龥]{2,12}(?:市|州|盟|区|县))",
        prefix,
    )
    if m:
        return m.group(1)

    m = re.match(
        r"^([一-龥]{2,8}?)(?:\s*GDP|\s*以|\s*凭借|\s*在|\s*，|\s*实现|\s*作为|\s*位列|\s*稳居|\s*紧随其后)",
        prefix,
    )
    if m:
        return m.group(1)

    return ""


def _iter_text_paragraphs(raw_text: str) -> List[str]:
    lines = [str(line).strip() for line in str(raw_text or "").splitlines() if str(line).strip()]
    if not lines:
        return []

    merged: List[str] = []
    buffer = ""
    for line in lines:
        if not buffer:
            buffer = line
        else:
            if buffer.endswith(("。", "！", "？", ".", "；", ";")):
                merged.append(buffer)
                buffer = line
            else:
                buffer += line
    if buffer:
        merged.append(buffer)
    return merged


def _build_field_patterns(field_name: str) -> List[re.Pattern[str]]:
    normalized = _normalize_label(field_name)
    patterns: List[re.Pattern[str]] = []

    if "gdp总量" in normalized:
        patterns.extend(
            [
                re.compile(r"GDP\s*总量(?:达到|达|为)?\s*([0-9,]+(?:\.[0-9]+)?)\s*亿元", re.I),
                re.compile(r"以\s*([0-9,]+(?:\.[0-9]+)?)\s*亿元的\s*GDP", re.I),
                re.compile(r"([0-9,]+(?:\.[0-9]+)?)\s*亿元\s*GDP\s*总量", re.I),
            ]
        )
    elif "常住人口" in normalized:
        patterns.extend(
            [
                re.compile(r"常住人口(?:达)?\s*([0-9,]+(?:\.[0-9]+)?)\s*万"),
                re.compile(r"([0-9,]+(?:\.[0-9]+)?)\s*万常住人口"),
                re.compile(r"人口(?:严控至|达|约|为)?\s*([0-9,]+(?:\.[0-9]+)?)\s*万"),
                re.compile(r"([0-9,]+(?:\.[0-9]+)?)\s*万(?:的)?(?:庞大)?人口"),
                re.compile(r"([0-9,]+(?:\.[0-9]+)?)\s*万人口"),
            ]
        )
    elif "人均gdp" in normalized:
        patterns.extend(
            [
                re.compile(r"人均\s*GDP(?:高达|达|为)?\s*([0-9,]+(?:\.[0-9]+)?)\s*元", re.I),
                re.compile(r"人均\W*GDP\W*(?:高达|达|为)?\W*([0-9,]+(?:\.[0-9]+)?)\s*元", re.I),
                re.compile(r"实现\s*([0-9,]+(?:\.[0-9]+)?)\s*元的惊人人均\s*GDP", re.I),
                re.compile(r"([0-9,]+(?:\.[0-9]+)?)\s*元(?:的)?(?:高位|惊人)?人均\s*GDP", re.I),
            ]
        )
    elif "一般公共预算收入" in normalized:
        patterns.extend(
            [
                re.compile(
                    r"一般公共预算收入(?:突破|跃升至|大幅增长至|达到|达|为)?\s*([0-9,]+(?:\.[0-9]+)?)\s*亿元"
                ),
            ]
        )

    clean = _strip_units(field_name)
    if clean:
        escaped = re.escape(clean).replace(r"\ ", r"\s*")
        patterns.append(
            re.compile(
                rf"{escaped}(?:[^0-9\n]{{0,12}})([0-9,]+(?:\.[0-9]+)?)",
                re.I,
            )
        )
    return patterns


def _extract_field_value(text: str, field_name: str) -> str:
    for pattern in _build_field_patterns(field_name):
        match = pattern.search(text)
        if match:
            return match.group(1).replace(",", "").strip()
    return ""


def extract_plain_text_records(profile: dict, loaded_bundle: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    if not isinstance(profile, dict):
        return None
    if profile.get("task_mode") != "table_records":
        return None
    if profile.get("template_mode") != "excel_table":
        return None

    fields = [f for f in (profile.get("fields") or []) if isinstance(f, dict) and f.get("name")]
    if len(fields) < 3:
        return None

    text_fields = [
        f for f in fields
        if str(f.get("type", "text")).lower() == "text"
        or any(h in str(f.get("name", "")) for h in _TEXT_FIELD_HINTS)
    ]
    numeric_fields = [
        f for f in fields
        if str(f.get("type", "")).lower() in _NUMERIC_TYPES and f not in text_fields
    ]
    if not text_fields or len(numeric_fields) < 2:
        return None

    key_field = text_fields[0]["name"]
    required_hits = min(len(numeric_fields), 3)

    paragraphs: List[str] = []
    for doc in loaded_bundle.get("documents") or []:
        if not isinstance(doc, dict):
            continue
        if doc.get("tables_dataframes") or doc.get("tables"):
            continue
        raw_text = str(doc.get("text", "") or "")
        for line in _iter_text_paragraphs(raw_text):
            line = str(line).strip()
            if len(line) >= 20:
                paragraphs.append(line)

    if not paragraphs:
        return None

    rows_by_key: Dict[str, Dict[str, str]] = {}
    for paragraph in paragraphs:
        entity_name = _extract_city_like_name(paragraph)
        if not entity_name:
            continue

        row = {f["name"]: "" for f in fields}
        row[key_field] = entity_name

        hit_count = 0
        for field in numeric_fields:
            field_name = field["name"]
            value = _extract_field_value(paragraph, field_name)
            row[field_name] = value
            if value:
                hit_count += 1

        if hit_count < required_hits:
            continue

        existing = rows_by_key.get(entity_name)
        if not existing:
            rows_by_key[entity_name] = row
            continue

        existing_score = sum(1 for v in existing.values() if str(v).strip())
        row_score = sum(1 for v in row.values() if str(v).strip())
        if row_score > existing_score:
            rows_by_key[entity_name] = row

    records = list(rows_by_key.values())
    if len(records) < 10:
        return None
    return {"records": records, "_internal_route": "plain_text_metrics"}
