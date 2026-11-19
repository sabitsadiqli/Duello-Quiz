#!/usr/bin/env python3
"""Generate the Duello Quiz v3 brand SVGs (OQ-D37, design/brand.md).

Usage:  python3 design/tools/brand_assets.py      (needs fontTools: pip install fonttools)

Writes design/brand/*.svg (mark, launcher layers, splash, notification icon),
design/store/feature-graphic.svg, docs/legal/favicon.svg and the inline marks in
docs/legal/*.html. Text and the '?' are converted to outlines from the static
fonts the app bundles (read only): android/core/designsystem/src/main/res/font.
Edit the geometry here, never the generated SVGs by hand.
"""
import math
import pathlib
import re

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
FONTS = REPO / "android/core/designsystem/src/main/res/font"

PAPER, INK, NAR = "#F5EFE3", "#1E1B16", "#A82D25"
PAPER_D, INK_D, NAR_D = "#17150F", "#EFE7D6", "#EC7B6F"  # dark: surface, onSurface, primary
ON_SURFACE_VARIANT = "#5F574C"

# ---- Mark geometry, 108-unit design space -------------------------------------
# Front bubble ("the question", ink) with a brush-curved tail to the lower left.
FRONT = ("M38,28 H54 A12,12 0 0 1 66,40 V52 A12,12 0 0 1 54,64 H41 "
         "C37.5,69 32,73.5 25,76 C27.5,71 26,66 26,60 V40 A12,12 0 0 1 38,28 Z")
# Back bubble ("the other player", nar): only its visible part, i.e. the bubble
# minus the front bubble grown by a 3-unit gap. Pre-computed so the gap is real
# transparency (works on any background, in VectorDrawable and in the monochrome layer).
BACK = ("M69,38 H77 A11,11 0 0 1 88,49 V70 C88,76 86.5,81 89,86 "
        "C82,83.5 76.5,79 73,74 H61 A11,11 0 0 1 50.75,67 H54 A15,15 0 0 0 69,52 Z")
# Extreme points used for the safe-zone check (tail tips, 45-degree corner points).
EXTREMES = [(25, 76), (89, 86), (38 - 12 / math.sqrt(2), 40 - 12 / math.sqrt(2)),
            (54 + 12 / math.sqrt(2), 40 - 12 / math.sqrt(2)),
            (77 + 11 / math.sqrt(2), 49 - 11 / math.sqrt(2)), (69, 38), (88, 70), (26, 40), (61, 74)]
BBOX = (25, 28, 89, 86)  # x0, y0, x1, y1
Q_HEIGHT = 21.0          # question mark cap height in design units
Q_CENTER = (46.0, 46.5)  # optical centre of the front bubble body


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


class Font:
    def __init__(self, name):
        self.tt = TTFont(FONTS / f"{name}.ttf")
        self.gs = self.tt.getGlyphSet()
        self.cmap = self.tt.getBestCmap()
        self.upm = self.tt["head"].unitsPerEm

    def glyph(self, ch):
        return self.cmap[ord(ch)]

    def width(self, text, size, tracking=0.0):
        w = sum(self.gs[self.glyph(c)].width for c in text) * size / self.upm
        return w + tracking * size * (len(text) - 1)

    def path(self, text, x, y, size, tracking=0.0):
        pen = SVGPathPen(self.gs, ntos=fmt)
        s = size / self.upm
        cx = x
        for c in text:
            g = self.glyph(c)
            self.gs[g].draw(TransformPen(pen, (s, 0, 0, -s, cx, y)))
            cx += self.gs[g].width * s + tracking * size
        return pen.getCommands()


FR_SEMI = Font("fraunces_semibold")
PJS_SEMI = Font("plusjakartasans_semibold")


def question_mark():
    g = FR_SEMI.glyph("?")
    bp = BoundsPen(FR_SEMI.gs)
    FR_SEMI.gs[g].draw(bp)
    x0, y0, x1, y1 = bp.bounds
    s = Q_HEIGHT / (y1 - y0)
    # centre the glyph bbox on Q_CENTER (y flipped)
    tx = Q_CENTER[0] - (x0 + x1) / 2 * s
    ty = Q_CENTER[1] + (y0 + y1) / 2 * s
    pen = SVGPathPen(FR_SEMI.gs, ntos=fmt)
    FR_SEMI.gs[g].draw(TransformPen(pen, (s, 0, 0, -s, tx, ty)))
    return pen.getCommands()


Q = question_mark()


def mark_group(front, back, glyph, transform=""):
    t = f' transform="{transform}"' if transform else ""
    return (f'  <g{t}>\n'
            f'    <path d="{BACK}" fill="{back}"/>\n'
            f'    <path d="{FRONT}" fill="{front}"/>\n'
            f'    <path d="{Q}" fill="{glyph}"/>\n'
            f'  </g>\n')


def _cubic(p0, p1, p2, p3, n=24):
    for i in range(n + 1):
        u = i / n
        a, b, c, d = (1 - u) ** 3, 3 * u * (1 - u) ** 2, 3 * u * u * (1 - u), u ** 3
        yield (a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1])


def _arc(cx, cy, r, a0, a1, n=24):
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        yield (cx + r * math.cos(a), cy + r * math.sin(a))


# Outline samples of both bubbles (outer edges only) for the safe-zone fit.
OUTLINE = [*_arc(38, 40, 12, 180, 270), *_arc(54, 40, 12, 270, 360), *_arc(54, 52, 12, 0, 90),
           *_cubic((41, 64), (37.5, 69), (32, 73.5), (25, 76)), *_cubic((25, 76), (27.5, 71), (26, 66), (26, 60)),
           *_arc(77, 49, 11, 270, 360), (69, 38), (88, 70),
           *_cubic((88, 70), (88, 76), (86.5, 81), (89, 86)), *_cubic((89, 86), (82, 83.5), (76.5, 79), (73, 74)),
           *_arc(61, 63, 11, 90, 159)]
SAFE_RADIUS = 31.5  # 33-unit safe zone minus 1.5 units of air


def _fit():
    best = None
    for i in range(0, 161):
        for j in range(0, 161):
            cx, cy = 45 + i * 0.125, 45 + j * 0.125
            r = max(math.hypot(x - cx, y - cy) for x, y in OUTLINE)
            if best is None or r < best[0]:
                best = (r, cx, cy)
    return best


FIT_R, FIT_CX, FIT_CY = _fit()
LAUNCH_SCALE = round(SAFE_RADIUS / FIT_R, 3)


def launcher_transform():
    # scale around the centre of the smallest enclosing circle, move it to (54, 54)
    s = LAUNCH_SCALE
    return f"translate({fmt(54 - FIT_CX * s)} {fmt(54 - FIT_CY * s)}) scale({s})", FIT_CX, FIT_CY


def safe_zone_report():
    return FIT_R * LAUNCH_SCALE


def svg(w, h, vb, title, body, comment=""):
    c = f"  <!--\n{comment}\n  -->\n" if comment else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}">\n'
            f'  <title>{title}</title>\n{c}{body}</svg>\n')


def write(rel, text):
    p = REPO / rel
    p.write_text(text)
    print("wrote", rel, len(text), "bytes")


def main():
    lt, _, _ = launcher_transform()
    print("safe-zone max radius:", round(safe_zone_report(), 2), "(limit 33)")

    # In-app marks: square viewBox around the shapes
    cx, cy = (BBOX[0] + BBOX[2]) / 2, (BBOX[1] + BBOX[3]) / 2
    side = 66
    vb = f"{fmt(cx - side / 2)} {fmt(cy - side / 2)} {side} {side}"
    common = ("    Duello Quiz v3 mark (OQ-D37, design/brand.md). Two speech bubbles: the ink one\n"
              "    asks the question (Fraunces SemiBold '?' as outlines), the nar one behind it is\n"
              "    the other player. The gap between them is real transparency, so the mark sits on\n"
              "    any surface. No masks, no gradients: imports 1:1 as a VectorDrawable.")
    write("design/brand/logo-mark-light.svg", svg(
        96, 96, vb, "Duello Quiz mark, light theme (on scheme.surface)",
        mark_group(INK, NAR, PAPER),
        common + f"\n    Light: front scheme.onSurface {INK}, back scheme.primary {NAR}, glyph scheme.surface {PAPER}."))
    write("design/brand/logo-mark-dark.svg", svg(
        96, 96, vb, "Duello Quiz mark, dark theme (on scheme.surface)",
        mark_group(INK_D, NAR_D, PAPER_D),
        common + f"\n    Dark: front scheme.onSurface {INK_D}, back scheme.primary {NAR_D}, glyph scheme.surface {PAPER_D}."))

    # Adaptive icon layers
    write("design/brand/ic-launcher-background.svg", svg(
        108, 108, "0 0 108 108", "Adaptive icon background (paper)",
        f'  <rect width="108" height="108" fill="{PAPER}"/>\n',
        f"    Solid paper, scheme.surface light {PAPER}, in both system themes (brand.md v3)."))
    write("design/brand/ic-launcher-foreground.svg", svg(
        108, 108, "0 0 108 108", "Adaptive icon foreground",
        mark_group(INK, NAR, PAPER, lt),
        f"    Mark scaled {LAUNCH_SCALE} around its centre; farthest point "
        f"{safe_zone_report():.1f} units from (54,54), inside the 33-unit (66dp) safe zone.\n"
        f"    Colours fixed (not themed): ink {INK}, nar {NAR}, glyph = paper {PAPER}."))
    mono_body = (f'  <g transform="{lt}">\n'
                 f'    <path d="{BACK}" fill="#000000"/>\n'
                 f'    <path d="{FRONT} {Q}" fill="#000000" fill-rule="evenodd"/>\n'
                 f'  </g>\n')
    write("design/brand/ic-launcher-monochrome.svg", svg(
        108, 108, "0 0 108 108", "Adaptive icon monochrome (themed icons, Android 13+)", mono_body,
        "    Single colour, only alpha matters (the system tints it). The '?' is a hole\n"
        "    (fill-rule evenodd = android:fillType=\"evenOdd\"), the gap between the bubbles is empty.\n"
        "    Same geometry and scale as the foreground."))
    full_body = f'  <rect width="108" height="108" fill="{PAPER}"/>\n' + mark_group(INK, NAR, PAPER, lt)
    write("design/brand/app-icon-full.svg", svg(
        512, 512, "0 0 108 108", "Duello Quiz app icon, full bleed (Play Store 512 x 512 export)", full_body,
        "    Background layer + foreground layer composited. Play applies the corner mask; export square."))
    write("docs/legal/favicon.svg", svg(
        512, 512, "0 0 108 108", "Duello Quiz", full_body,
        "    Copy of design/brand/app-icon-full.svg (v3, OQ-D37)."))
    # Dark splash icon (Android 12+ windowSplashScreenAnimatedIcon in values-night)
    write("design/brand/splash-icon-dark.svg", svg(
        108, 108, "0 0 108 108", "Splash icon, dark theme (drawable-night)",
        mark_group(INK_D, NAR_D, PAPER_D, lt),
        "    Same geometry as ic-launcher-foreground.svg with the dark-theme colours, for the\n"
        "    Android 12+ splash on the ink background (the light foreground would vanish there)."))
    # Notification small icon: 24dp, white silhouette
    s = 22 / (BBOX[2] - BBOX[0])
    tx = 12 - cx * s
    ty = 12 - cy * s
    stat_body = (f'  <g transform="translate({fmt(tx)} {fmt(ty)}) scale({fmt(s)})">\n'
                 f'    <path d="{BACK}" fill="#FFFFFF"/>\n'
                 f'    <path d="{FRONT} {Q}" fill="#FFFFFF" fill-rule="evenodd"/>\n'
                 f'  </g>\n')
    write("design/brand/ic-stat-duello.svg", svg(
        24, 24, "0 0 24 24", "Notification small icon (ic_stat_duello)", stat_body,
        "    White silhouette on transparent, 24 x 24dp, 1dp padding; Android tints it with\n"
        "    the notification accent (brand.md v3, notifications.md)."))

    feature_graphic()
    legal_mark()


def feature_graphic():
    W, H = 1024, 500
    # mark: design bbox 63 x 48 units, scale 4.1
    ms = 4.1
    mw, mh = (BBOX[2] - BBOX[0]) * ms, (BBOX[3] - BBOX[1]) * ms
    mx = 96
    my = (H - mh) / 2 - 6
    mark_t = f"translate({fmt(mx - BBOX[0] * ms)} {fmt(my - BBOX[1] * ms)}) scale({ms})"

    tx = mx + mw + 64
    name = "Duello Quiz"
    size = 100
    name_w = FR_SEMI.width(name, size, -0.01)
    tag = "Azərbaycan dilində bilik oyunu"
    tsize = 34
    tag_w = PJS_SEMI.width(tag, tsize)
    pre = "Azərbaycan dilində "
    word = "bilik"
    w0 = PJS_SEMI.width(pre, tsize)
    w1 = PJS_SEMI.width(word, tsize)
    base1 = 248
    base2 = base1 + 70
    print(f"feature graphic: name {name_w:.0f}px from x {tx:.0f} -> {tx + name_w:.0f}; tagline {tag_w:.0f}px -> {tx + tag_w:.0f}")
    assert tx + max(name_w, tag_w) <= 974, "text leaves the 924 x 400 safe box"

    # squiggle under "bilik": wavelength ~ 11dp x 1.6, amplitude 3.6, stroke 4
    sx0, sx1 = tx + w0 - 3, tx + w0 + w1 + 3
    sy = base2 + 13
    wl, amp = 16.0, 3.6
    n = max(2, round((sx1 - sx0) / (wl / 2)))
    step = (sx1 - sx0) / n
    jitter = [0.0, 0.5, -0.3, 0.4, -0.5, 0.2, -0.2, 0.3, 0.1, -0.4]
    d = f"M{fmt(sx0)},{fmt(sy)}"
    for i in range(n):
        x = sx0 + step * (i + 1)
        a = (amp + jitter[i % len(jitter)]) * (-1 if i % 2 == 0 else 1)
        d += f" Q{fmt(x - step / 2)},{fmt(sy + a * 2)} {fmt(x)},{fmt(sy)}"

    body = (f'  <rect width="{W}" height="{H}" fill="{PAPER}"/>\n'
            + mark_group(INK, NAR, PAPER, mark_t)
            + f'  <path d="{FR_SEMI.path(name, tx, base1, size, -0.01)}" fill="{INK}"/>\n'
            + f'  <path d="{PJS_SEMI.path(tag, tx, base2, tsize)}" fill="{ON_SURFACE_VARIANT}"/>\n'
            + f'  <path d="{d}" fill="none" stroke="{NAR}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>\n')
    comment = (
        "    v3 (OQ-D37). Spec: design/store/listing-az.md \"Graphics\". Light tokens only:\n"
        f"    paper scheme.surface {PAPER}; mark = design/brand/logo-mark-light.svg x {ms};\n"
        f"    \"Duello Quiz\" Fraunces SemiBold (opsz 72, SOFT 50) {size}px scheme.onSurface {INK};\n"
        f"    tagline (listing full description, line 1) Plus Jakarta Sans SemiBold {tsize}px\n"
        f"    scheme.onSurfaceVariant {ON_SURFACE_VARIANT}; squiggle under \"bilik\" scheme.primary {NAR}\n"
        "    (components.md §36 shape, scaled). All text is already converted to outlines\n"
        "    (no font needed to export; advance widths, no kerning). Content stays inside the\n"
        "    central 924 x 400 box (x 50-974, y 50-450). No gradients, no alpha.")
    write("design/store/feature-graphic.svg",
          svg(W, H, f"0 0 {W} {H}", "Duello Quiz feature graphic (Google Play, 1024 x 500)", body, comment))


def legal_mark():
    """Replace the inline brand mark in docs/legal/*.html (colours come from legal.css classes)."""
    cx, cy = (BBOX[0] + BBOX[2]) / 2, (BBOX[1] + BBOX[3]) / 2
    vb = f"{fmt(cx - 33)} {fmt(cy - 33)} 66 66"
    lines = [f'<svg viewBox="{vb}" aria-hidden="true" focusable="false">',
             f'  <path class="mk-back" d="{BACK}"/>',
             f'  <path class="mk-front" d="{FRONT}"/>',
             f'  <path class="mk-glyph" d="{Q}"/>',
             '</svg>']
    pattern = re.compile(r'( *)<svg viewBox="[^"]*" aria-hidden="true" focusable="false">\n(?:.*\n)*?\1</svg>')
    for page in sorted((REPO / "docs/legal").glob("*.html")):
        text = page.read_text()
        new_text, n = pattern.subn(lambda m: m.group(1) + ("\n" + m.group(1)).join(lines), text)
        if new_text != text:
            page.write_text(new_text)
        print(f"docs/legal/{page.name}: {n} inline mark(s)")


if __name__ == "__main__":
    main()
