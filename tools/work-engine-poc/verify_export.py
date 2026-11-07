"""Read-only independent checks for this PoC's synthetic PDF export, not arbitrary files."""
import argparse
import json
from pathlib import Path
import pypdf
from pypdf import PdfReader


def verify(directory: Path) -> dict:
    reader = PdfReader(directory / "edited.pdf")
    texts = ["".join(page.extract_text().split()) for page in reader.pages]
    expected = ["ORIGINAL_BODY_3", "EDITED_BODY_ONE", "NEW_PAGE_FOUR"]
    image_colors = []
    for page in reader.pages:
        image_colors.append([
            image.image.convert("RGB").getcolors(maxcolors=10000)
            for image in page.images
        ])
    checks = {
        "three_pages": len(reader.pages) == 3,
        "page_order": len(texts) == 3 and all(token in text for token, text in zip(expected, texts)),
        "old_body_absent": "ORIGINAL_BODY_1" not in "".join(texts),
        "deleted_page_absent": "ORIGINAL_BODY_2" not in "".join(texts),
        "replaced_image_blue": len(image_colors) > 1 and any(
            colors and all(color[2] > 240 and color[0] < 10 and color[1] < 10 for _, color in colors)
            for colors in image_colors[1]
        ),
    }
    return {"pypdf": pypdf.__version__, "fixtureOnly": True, "checks": checks,
            "passed": all(checks.values()), "imageColors": image_colors}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    result = verify(args.directory)
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["passed"] else 1)
