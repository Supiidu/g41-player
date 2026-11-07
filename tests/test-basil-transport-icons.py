#!/usr/bin/env python3
"""Static checks for exact user-provided Basil prev/next vectors.

The paths are compiled into Canvas geometry so Xposed/SystemUI does not need
the module's APK resource context. This test checks basic path fidelity and
that existing media actions and play/pause remain untouched.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
icons = (root / "app/src/main/java/dev/g41/glassplayer/BasilTransportIcons.java").read_text()
glass = (root / "app/src/main/java/dev/g41/glassplayer/GlassOverlay.java").read_text()


def svg_counts(kind):
    svg = ET.parse(root / f"design/icons/basil_skip-{kind}-outline.svg").getroot()
    paths = [item.attrib["d"] for item in svg.iter() if item.tag.endswith("path")]
    commands = re.findall(r"[MLCVZ]", " ".join(paths))
    return {command: commands.count(command) for command in ("M", "L", "C", "V", "Z")}


def code_counts(method):
    code = icons.split(f"private static Path {method}Path()")[1].split("return p;")[0]
    return {
        "M": code.count("p.moveTo("),
        "L": code.count("p.lineTo("),
        "C": code.count("p.cubicTo("),
        "Z": code.count("p.close();"),
    }


checks = {}
for kind, method in (("prev", "previous"), ("next", "next")):
    svg = svg_counts(kind)
    java = code_counts(method)
    checks[f"{kind}: every cubic contour retained"] = java["C"] == svg["C"]
    checks[f"{kind}: all move commands retained"] = java["M"] == svg["M"]
    checks[f"{kind}: vertical SVG strokes converted to lineTo"] = (
        java["L"] == svg["L"] + svg["V"]
    )
    checks[f"{kind}: all closed or implicit contours supported"] = (
        java["Z"] >= svg["Z"]
    )

checks["evenodd fills preserve outlined Basil cutouts"] = (
    icons.count("p.setFillType(Path.FillType.EVEN_ODD)") == 2
)
checks["drawn in the 53-square original SVG viewbox"] = (
    "private static final float VIEWBOX = 53f;" in icons
    and "canvas.scale(scale, scale);" in icons
)
checks["Monet control color still drives the paint"] = (
    "p.setColor(controlsColor);" in glass
    and "BasilTransportIcons.draw(c, p, kind == 0, cx, cy, d(48));" in glass
)
checks["media action mappings unchanged"] = (
    "mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)" in glass
    and "mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)" in glass
    and "c.skipToPrevious();" in glass
    and "c.skipToNext();" in glass
)
checks["play/pause glyph unchanged"] = (
    "if (kind == 1) {" in glass
    and "c.drawCircle(cx,cy,d(13),p);" in glass
)

for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
print(f"{sum(checks.values())}/{len(checks)} checks passed")
if not all(checks.values()):
    raise SystemExit(1)
print("NOTE: source checks only; Android build and Moto G41 validation still required.")
