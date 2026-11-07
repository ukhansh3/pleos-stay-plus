"""Verify synthetic DOCX files produced by the production Android editor.
Requires python-docx and Pillow. No credentials, real documents or server calls.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import zipfile
import xml.etree.ElementTree as ET

from docx import Document
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "output" / "main"
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

def package(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        parts = {name: z.read(name) for name in z.namelist()}
        assert len(parts) == len(z.namelist())
    for name, data in parts.items():
        if name.endswith((".xml", ".rels")):
            ET.fromstring(data)
    return parts

def image_pixel(document, shape):
    rid = shape._inline.graphic.graphicData.pic.blipFill.blip.get("{" + R + "}embed")
    return Image.open(io.BytesIO(document.part.related_parts[rid].blob)).convert("RGB").getpixel((0, 0))

def verify():
    edited = OUT / "edited.docx"
    external = OUT / "external-edited.docx"
    main_parts = package(edited)
    external_parts = package(external)
    source = ROOT / "android/app/src/androidTest/assets/work/poi-generated-fixture.docx"
    original_parts = package(source)
    d = Document(edited)
    assert d.paragraphs[0].text == "한글 업무 문서"
    assert any("한글" in r.text and r.bold for r in d.paragraphs[0].runs)
    assert any("업무" in r.text and r.bold for r in d.paragraphs[0].runs)
    run = next(r for r in d.paragraphs[0].runs if "한글" in r.text)
    assert run.font.size.pt == 22
    assert str(run.font.color.rgb) == "1234AB"
    assert run._element.rPr.rFonts.get("{" + W + "}eastAsia") == "Noto Serif CJK KR"
    assert int(d.paragraphs[0].alignment) == 1
    assert d.tables[0].cell(0, 0).text == "표 수정"
    assert d.tables[0].cell(0, 0)._tc[0].tag == "{" + W + "}tcPr"
    assert len(d.inline_shapes) == 2
    assert d.inline_shapes[0].width == 240 * 9525
    assert image_pixel(d, d.inline_shapes[0]) == (0, 0, 255)
    assert image_pixel(d, d.inline_shapes[1]) == (0, 255, 0)

    e = Document(external)
    assert e.paragraphs[0].text == "한국어 원본"
    assert any(p.text == "보존 문단" for p in e.paragraphs)
    r = next(r for r in e.paragraphs[0].runs if "한국어" in r.text)
    assert r.bold and r.italic and r.underline
    assert r.font.size.pt == 20
    assert str(r.font.color.rgb) == "AB1234"
    assert r._element.rPr.rFonts.get("{" + W + "}eastAsia") == "Noto Sans CJK KR"
    assert e.tables[0].cell(0, 0).text == "기존 표 수정"
    assert e.inline_shapes[0].width == 260 * 9525
    assert image_pixel(e, e.inline_shapes[0]) == (255, 0, 0)
    preserved = [name for name in original_parts if name != "word/document.xml"]
    assert set(original_parts) == set(external_parts)
    assert all(original_parts[name] == external_parts[name] for name in preserved)
    report = {
        "result": "PASS",
        "writer": "production custom OOXML engine on Pleos API34",
        "independent_reader": "python-docx / ElementTree / zipfile / Pillow",
        "checks": ["ZIP/XML", "Korean", "range formatting", "font/size/color/alignment",
                   "table content/property order", "image pixels/size",
                   "externally-authored import", "all unmodified external parts byte-identical"],
        "preserved_external_part_count": len(preserved),
        "files": {p.name: {"bytes": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
                  for p in (edited, external)},
        "limit": "Synthetic fixtures only; not Microsoft Word visual-layout or real pairing E2E."
    }
    (OUT / "independent-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pull", action="store_true", help="Read only synthetic test outputs from the debuggable app")
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--user", default="10")
    parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"))
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.pull:
        for name in ("edited.docx", "external-edited.docx"):
            data = subprocess.run([args.adb, "-s", args.serial, "exec-out", "run-as",
                                   "com.pleosstay.app", "--user", args.user, "cat",
                                   "cache/docx-verification/" + name], check=True, capture_output=True).stdout
            assert data.startswith(b"PK")
            (OUT / name).write_bytes(data)
    verify()
