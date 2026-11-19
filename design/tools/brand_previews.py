#!/usr/bin/env python3
"""Render the brand review PNGs into design/brand/previews/ (macOS only).

Usage:  python3 design/tools/brand_previews.py    (needs Pillow; uses qlmanage, i.e. WebKit)

Previews are for review, not release assets: export the Play icon and the
feature graphic from the SVGs (design/brand.md, design/store/listing-az.md).
"""
import pathlib
import re
import shutil
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
OUT = REPO / "design/brand/previews"
OUT.mkdir(parents=True, exist_ok=True)
TMP = pathlib.Path(tempfile.mkdtemp())


def inner(path):
    """Return the inner markup and viewBox of an SVG file."""
    t = (REPO / path).read_text()
    vb = re.search(r'viewBox="([^"]+)"', t).group(1)
    body = re.sub(r"^<svg[^>]*>", "", t.strip()).rsplit("</svg>", 1)[0]
    body = re.sub(r"<title>.*?</title>", "", body)
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    return vb, body


def nest(path, x, y, w, h):
    vb, body = inner(path)
    return f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="{vb}">{body}</svg>'


def render(name, size, content):
    """Render a square SVG canvas of `size` px; returns a PIL image."""
    f = TMP / f"{name}.svg"
    f.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
                 f'viewBox="0 0 {size} {size}">{content}</svg>')
    subprocess.run(["qlmanage", "-t", "-s", str(size), "-o", str(TMP), str(f)],
                   check=True, capture_output=True)
    return Image.open(TMP / f"{name}.svg.png").convert("RGB")


def save(img, name, colors=64):
    p = OUT / name
    img.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(p, optimize=True)
    print(name, img.size, p.stat().st_size, "bytes")


FG = "design/brand/ic-launcher-foreground.svg"
BG = "design/brand/ic-launcher-background.svg"
MONO = "design/brand/ic-launcher-monochrome.svg"


def adaptive(x, y, px, shape, mono=None):
    """Adaptive icon drawn at px size: 72/108 of the layers are visible, cut by `shape`."""
    full = px * 108 / 72
    off = (full - px) / 2
    cid = f"c{x}_{y}_{shape}"
    if shape == "circle":
        clip = f'<circle cx="{x + px / 2}" cy="{y + px / 2}" r="{px / 2}"/>'
    elif shape == "squircle":
        r = px / 2
        cx, cy = x + r, y + r
        pts = []
        import math
        for i in range(73):
            t = 2 * math.pi * i / 72
            c, s = math.cos(t), math.sin(t)
            pts.append(f"{cx + r * (abs(c) ** 0.5) * (1 if c >= 0 else -1):.2f},{cy + r * (abs(s) ** 0.5) * (1 if s >= 0 else -1):.2f}")
        clip = f'<polygon points="{" ".join(pts)}"/>'
    else:  # rounded square
        clip = f'<rect x="{x}" y="{y}" width="{px}" height="{px}" rx="{px * 0.18}"/>'
    if mono:
        bg_col, fg_col = mono
        layers = (f'<rect x="{x - off}" y="{y - off}" width="{full}" height="{full}" fill="{bg_col}"/>'
                  + nest(MONO, x - off, y - off, full, full).replace("#000000", fg_col))
    else:
        layers = nest(BG, x - off, y - off, full, full) + nest(FG, x - off, y - off, full, full)
    return f'<defs><clipPath id="{cid}">{clip}</clipPath></defs><g clip-path="url(#{cid})">{layers}</g>'


def label(img, items, font_size=13):
    d = ImageDraw.Draw(img)
    try:
        f = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", font_size)
    except OSError:
        f = ImageFont.load_default()
    for (x, y, text, col) in items:
        d.text((x, y), text, fill=col, font=f)
    return img


def main():
    # 1) Launcher at 192 px (circle) on a light and a dark home screen, plus 48 px
    W = 560
    c = (f'<rect width="{W}" height="{W}" fill="#E9E6EE"/><rect x="{W/2}" width="{W/2}" height="{W}" fill="#1F1F23"/>'
         + adaptive(44, 40, 192, "circle") + adaptive(W / 2 + 44, 40, 192, "circle")
         + adaptive(44, 280, 48, "circle") + adaptive(112, 280, 48, "squircle") + adaptive(180, 280, 48, "rounded")
         + adaptive(W / 2 + 44, 280, 48, "circle") + adaptive(W / 2 + 112, 280, 48, "squircle") + adaptive(W / 2 + 180, 280, 48, "rounded")
         + adaptive(44, 380, 96, "squircle") + adaptive(W / 2 + 44, 380, 96, "squircle"))
    img = render("launcher", W, c).crop((0, 0, W, 500))
    label(img, [(44, 248, "192 px, circle", "#1E1B16"), (W // 2 + 44, 248, "192 px, circle", "#EFE7D6"),
                (44, 338, "48 px: circle, squircle, rounded", "#1E1B16"), (W // 2 + 44, 338, "48 px: circle, squircle, rounded", "#EFE7D6"),
                (150, 420, "96 px squircle", "#1E1B16"), (W // 2 + 150, 420, "96 px squircle", "#EFE7D6")])
    save(img, "launcher-light-dark.png")

    # 2) Masks + safe zone diagnostic + themed (monochrome) icons
    W = 640
    full = 180
    diag = (nest(BG, 20, 20, full, full) + nest(FG, 20, 20, full, full)
            + f'<rect x="{20 + full / 6}" y="{20 + full / 6}" width="{full * 2 / 3}" height="{full * 2 / 3}" fill="none" stroke="#2E5A78" stroke-width="1.5" stroke-dasharray="5 4"/>'
            + f'<circle cx="{20 + full / 2}" cy="{20 + full / 2}" r="{full * 33 / 108}" fill="none" stroke="#2E5A78" stroke-width="1.5"/>')
    c = (f'<rect width="{W}" height="{W}" fill="#FCF9F3"/>' + diag
         + adaptive(230, 40, 120, "circle") + adaptive(370, 40, 120, "squircle") + adaptive(510, 40, 120, "rounded")
         + adaptive(20, 260, 120, "circle", mono=("#D8E2FF", "#001A41")) + adaptive(160, 260, 120, "squircle", mono=("#D8E2FF", "#001A41"))
         + f'<rect x="300" y="240" width="340" height="190" fill="#1B1B1F"/>'
         + adaptive(330, 260, 120, "circle", mono=("#2F3033", "#ADC6FF")) + adaptive(480, 260, 120, "squircle", mono=("#2F3033", "#ADC6FF")))
    img = render("masks", W, c).crop((0, 0, W, 430))
    label(img, [(20, 204, "layers 108dp: dashed = 72dp visible, circle = 66dp safe zone", "#1E1B16"),
                (230, 170, "circle / squircle / rounded square", "#1E1B16"),
                (20, 392, "themed icon (monochrome), light", "#1E1B16"), (330, 392, "themed icon, dark", "#EFE7D6")])
    save(img, "adaptive-masks-themed.png")

    # 3) Logo marks on paper and ink + notification small icon
    W = 480
    c = (f'<rect width="{W}" height="{W}" fill="#F5EFE3"/><rect x="{W/2}" width="{W/2}" height="{W}" fill="#17150F"/>'
         + nest("design/brand/logo-mark-light.svg", 40, 30, 160, 160)
         + nest("design/brand/logo-mark-dark.svg", W / 2 + 40, 30, 160, 160)
         + nest("design/brand/logo-mark-light.svg", 40, 210, 24, 24) + nest("design/brand/logo-mark-light.svg", 76, 204, 36, 36)
         + nest("design/brand/logo-mark-dark.svg", W / 2 + 40, 210, 24, 24) + nest("design/brand/logo-mark-dark.svg", W / 2 + 76, 204, 36, 36)
         + f'<rect x="40" y="262" width="400" height="44" rx="22" fill="#E7E7EC"/>'
         + nest("design/brand/ic-stat-duello.svg", 56, 272, 24, 24).replace("#FFFFFF", "#A82D25")
         + f'<rect x="40" y="316" width="400" height="44" rx="22" fill="#2B2C30"/>'
         + nest("design/brand/ic-stat-duello.svg", 56, 326, 24, 24).replace("#FFFFFF", "#EC7B6F")
         + f'<rect x="40" y="370" width="400" height="30" fill="#000000"/>'
         + nest("design/brand/ic-stat-duello.svg", 50, 377, 16, 16))
    img = render("marks", W, c).crop((0, 0, W, 410))
    label(img, [(130, 214, "24 / 36 dp", "#5F574C"), (W // 2 + 130, 214, "24 / 36 dp", "#B5AB97"),
                (90, 276, "notification, light shade (accent #A82D25)", "#1E1B16"),
                (90, 330, "notification, dark shade (accent #EC7B6F)", "#EFE7D6"),
                (76, 379, "status bar 24dp (white)", "#FFFFFF")], 12)
    save(img, "logo-marks-notification.png")

    # 4) Feature graphic 1024 x 500
    c = nest("design/store/feature-graphic.svg", 0, 0, 1024, 500)
    img = render("feature", 1024, c).crop((0, 0, 1024, 500))
    save(img, "feature-graphic.png", colors=48)


def legal_pages():
    """docs/legal/account-deletion.html in light and dark (dark forced by rewriting the media query)."""
    shots = []
    for mode in ("light", "dark"):
        d = TMP / f"web-{mode}"
        d.mkdir()
        for f in ("account-deletion.html", "favicon.svg"):
            shutil.copy(REPO / "docs/legal" / f, d / f)
        css = (REPO / "docs/legal/legal.css").read_text()
        if mode == "dark":
            css = css.replace("@media (prefers-color-scheme: dark)", "@media all")
        (d / "legal.css").write_text(css)
        subprocess.run(["qlmanage", "-t", "-s", "900", "-o", str(d), str(d / "account-deletion.html")],
                       check=True, capture_output=True)
        shots.append(Image.open(d / "account-deletion.html.png").convert("RGB").crop((150, 0, 750, 700)))
    img = Image.new("RGB", (1200, 700))
    img.paste(shots[0], (0, 0))
    img.paste(shots[1], (600, 0))
    save(img.resize((960, 560), Image.LANCZOS), "legal-pages-light-dark.png", colors=48)


if __name__ == "__main__":
    main()
    legal_pages()
