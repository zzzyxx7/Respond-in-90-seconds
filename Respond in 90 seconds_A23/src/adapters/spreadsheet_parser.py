from __future__ import annotations

import csv
import logging
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Tuple
from xml.etree import ElementTree as ET

from src.adapters.base import BaseParser

logger = logging.getLogger(__name__)

try:
    import pandas as pd
except Exception:  # pragma: no cover - optional dependency
    pd = None


_XML_NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
_REL_NS = {"r": "http://schemas.openxmlformats.org/package/2006/relationships"}


def _col_ref_to_index(cell_ref: str) -> int:
    letters = []
    for ch in cell_ref:
        if ch.isalpha():
            letters.append(ch.upper())
        else:
            break
    idx = 0
    for ch in letters:
        idx = idx * 26 + (ord(ch) - ord("A") + 1)
    return max(1, idx) - 1


def _cell_text_from_inline(cell: ET.Element) -> str:
    texts = []
    for node in cell.findall(".//x:is//x:t", _XML_NS):
        if node.text:
            texts.append(node.text)
    return "".join(texts).strip()


def _normalize_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        if value.is_integer():
            return str(int(value))
        return format(value, "g")
    text = str(value).strip()
    if text.lower() == "none":
        return ""
    return text


def _trim_matrix(rows: List[List[str]]) -> List[List[str]]:
    non_empty_rows = [row for row in rows if any(cell.strip() for cell in row)]
    if not non_empty_rows:
        return []

    max_col = 0
    for row in non_empty_rows:
        for i, cell in enumerate(row):
            if cell.strip():
                max_col = max(max_col, i + 1)
    return [row[:max_col] for row in non_empty_rows]


def _make_unique_headers(row: List[str]) -> List[str]:
    headers = []
    seen: Dict[str, int] = {}
    for idx, raw in enumerate(row, start=1):
        base = (raw or "").strip() or f"column_{idx}"
        count = seen.get(base, 0)
        seen[base] = count + 1
        headers.append(base if count == 0 else f"{base}_{count + 1}")
    return headers


def _matrix_to_records(rows: List[List[str]]) -> Tuple[List[str], List[Dict[str, str]]]:
    if not rows:
        return [], []
    headers = _make_unique_headers(rows[0])
    records: List[Dict[str, str]] = []
    for row in rows[1:]:
        padded = list(row) + [""] * max(0, len(headers) - len(row))
        record = {headers[i]: _normalize_value(padded[i]) for i in range(len(headers))}
        if any(v for v in record.values()):
            records.append(record)
    return headers, records


def _build_sheet_summary(sheet_name: str, headers: List[str], records: List[Dict[str, str]], preview_rows: int = 12) -> str:
    lines = [f"工作表: {sheet_name}"]
    if headers:
        lines.append("列名: " + " | ".join(headers[:30]))
    if records:
        lines.append(f"记录数: {len(records)}")
        lines.append("前几行示例:")
        for idx, record in enumerate(records[:preview_rows], start=1):
            parts = []
            for key in headers[:12]:
                value = record.get(key, "")
                if value:
                    parts.append(f"{key}={value}")
            if parts:
                lines.append(f"{idx}. " + " ; ".join(parts))
    return "\n".join(lines)


class SpreadsheetParser(BaseParser):
    parser_type = "spreadsheet"

    def parse(self, path: Path) -> Dict[str, Any]:
        path = Path(path)
        result = {
            "parser_type": self.parser_type,
            "type": self.parser_type,
            "path": str(path),
            "file_name": path.name,
            "text": "",
            "paragraphs": [],
            "tables": [],
            "tables_dataframes": [],
            "chunks": [],
            "pages": 0,
            "warnings": [],
            "error": None,
        }

        try:
            suffix = path.suffix.lower()
            if suffix in (".xlsx", ".xlsm"):
                sheets = self._parse_xlsx(path)
            elif suffix == ".csv":
                sheets = self._parse_csv(path)
            else:
                raise ValueError(f"unsupported spreadsheet suffix: {suffix}")

            text_parts: List[str] = []
            for index, (sheet_name, rows) in enumerate(sheets):
                trimmed = _trim_matrix(rows)
                if not trimmed:
                    continue

                headers, records = _matrix_to_records(trimmed)
                summary = _build_sheet_summary(sheet_name, headers, records)
                text_parts.append(summary)
                result["paragraphs"].append(summary)
                result["chunks"].append({"type": "table", "text": summary})
                result["tables"].append(
                    {
                        "index": index,
                        "sheet_name": sheet_name,
                        "data": records,
                        "row_count": len(records),
                        "column_count": len(headers),
                        "markdown": summary,
                    }
                )
                if pd is not None and records:
                    result["tables_dataframes"].append(pd.DataFrame(records))

            result["text"] = "\n\n".join(text_parts)
            logger.info(
                "Spreadsheet 解析完成: %s | 工作表 %s 个 | 摘要长度 %s 字符",
                path.name,
                len(result["tables"]),
                len(result["text"]),
            )
        except Exception as e:
            msg = f"Spreadsheet 解析失败: {e}"
            result["error"] = msg
            result["warnings"].append(msg)
            logger.exception(msg)

        return result

    def _parse_csv(self, path: Path) -> List[Tuple[str, List[List[str]]]]:
        encodings = ("utf-8-sig", "utf-8", "gb18030", "gbk")
        last_error = None
        for enc in encodings:
            try:
                with path.open("r", encoding=enc, newline="") as f:
                    reader = csv.reader(f)
                    return [(path.stem, [[_normalize_value(cell) for cell in row] for row in reader])]
            except Exception as e:
                last_error = e
        raise last_error or ValueError("csv read failed")

    def _parse_xlsx(self, path: Path) -> List[Tuple[str, List[List[str]]]]:
        with zipfile.ZipFile(path, "r") as zf:
            shared_strings = self._load_shared_strings(zf)
            sheet_defs = self._load_sheet_defs(zf)
            sheets: List[Tuple[str, List[List[str]]]] = []
            for sheet_name, sheet_xml in sheet_defs:
                rows = self._load_sheet_rows(zf, sheet_xml, shared_strings)
                sheets.append((sheet_name, rows))
            return sheets

    def _load_shared_strings(self, zf: zipfile.ZipFile) -> List[str]:
        try:
            with zf.open("xl/sharedStrings.xml") as f:
                root = ET.parse(f).getroot()
        except KeyError:
            return []

        strings: List[str] = []
        for si in root.findall("x:si", _XML_NS):
            texts = []
            for node in si.findall(".//x:t", _XML_NS):
                if node.text:
                    texts.append(node.text)
            strings.append("".join(texts))
        return strings

    def _load_sheet_defs(self, zf: zipfile.ZipFile) -> List[Tuple[str, str]]:
        with zf.open("xl/workbook.xml") as f:
            workbook = ET.parse(f).getroot()
        with zf.open("xl/_rels/workbook.xml.rels") as f:
            rels = ET.parse(f).getroot()

        rel_map: Dict[str, str] = {}
        for rel in rels.findall("r:Relationship", _REL_NS):
            rel_id = rel.attrib.get("Id")
            target = rel.attrib.get("Target", "")
            if rel_id and target:
                rel_map[rel_id] = target.lstrip("/")

        sheet_defs: List[Tuple[str, str]] = []
        for sheet in workbook.findall("x:sheets/x:sheet", _XML_NS):
            name = sheet.attrib.get("name", "Sheet")
            rel_id = sheet.attrib.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
            target = rel_map.get(rel_id or "", "")
            if not target:
                continue
            sheet_path = target if target.startswith("xl/") else f"xl/{target}"
            sheet_defs.append((name, sheet_path))
        return sheet_defs

    def _load_sheet_rows(self, zf: zipfile.ZipFile, sheet_xml: str, shared_strings: List[str]) -> List[List[str]]:
        with zf.open(sheet_xml) as f:
            root = ET.parse(f).getroot()

        rows: List[List[str]] = []
        for row in root.findall(".//x:sheetData/x:row", _XML_NS):
            cells: Dict[int, str] = {}
            max_col = -1
            for cell in row.findall("x:c", _XML_NS):
                ref = cell.attrib.get("r", "")
                col_idx = _col_ref_to_index(ref) if ref else max_col + 1
                cell_type = cell.attrib.get("t", "")
                value = ""
                if cell_type == "inlineStr":
                    value = _cell_text_from_inline(cell)
                else:
                    value_node = cell.find("x:v", _XML_NS)
                    raw = value_node.text if value_node is not None else ""
                    if cell_type == "s":
                        try:
                            value = shared_strings[int(raw)]
                        except Exception:
                            value = raw or ""
                    elif cell_type == "b":
                        value = "TRUE" if raw == "1" else "FALSE"
                    else:
                        value = raw or ""
                cells[col_idx] = _normalize_value(value)
                max_col = max(max_col, col_idx)
            if max_col >= 0:
                rows.append([cells.get(i, "") for i in range(max_col + 1)])
        return rows
