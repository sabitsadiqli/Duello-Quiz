#!/usr/bin/env python3
"""Measure Azerbaijani UI strings with the fonts the app ships (stdlib only).

Reads cmap (format 4 / 12), hmtx, hhea and head of the static TTF instances in
android/core/designsystem/src/main/res/font and sums advance widths (kerning
ignored, so results are slightly conservative). Letter spacing from tokens.json
is added per character.

Usage:
  python3 design/tools/text_width.py ROLE "text" ["text" ...]
  python3 design/tools/text_width.py --glyphs "№⅓½⅔"      # coverage check
  python3 design/tools/text_width.py --file strings.tsv    # ROLE<TAB>budget<TAB>text

ROLE is a typography role from tokens.json (titleMedium, labelLarge, overline,
headlineSmall, numeralLarge ...). Output: width at font scale 1.0 / 1.3 in dp.
"""
import json
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "android/core/designsystem/src/main/res/font"
TOKENS = ROOT / "design/tokens.json"

FILES = {
    ("brand", 500): "plusjakartasans_medium.ttf",
    ("brand", 600): "plusjakartasans_semibold.ttf",
    ("brand", 700): "plusjakartasans_bold.ttf",
    ("brand", 800): "plusjakartasans_extrabold.ttf",
    ("display", 400): "fraunces_regular.ttf",
    ("display", 600): "fraunces_semibold.ttf",
}


class Font:
    def __init__(self, path):
        data = path.read_bytes()
        num = struct.unpack(">H", data[4:6])[0]
        tables = {}
        for i in range(num):
            tag, _, off, length = struct.unpack(">4sIII", data[12 + 16 * i: 28 + 16 * i])
            tables[tag.decode()] = (off, length)
        self.upm = struct.unpack(">H", data[tables["head"][0] + 18: tables["head"][0] + 20])[0]
        n_hm = struct.unpack(">H", data[tables["hhea"][0] + 34: tables["hhea"][0] + 36])[0]
        off = tables["hmtx"][0]
        self.adv = [struct.unpack(">H", data[off + 4 * i: off + 4 * i + 2])[0] for i in range(n_hm)]
        self.cmap = self._cmap(data, tables["cmap"][0])

    @staticmethod
    def _cmap(data, base):
        n = struct.unpack(">H", data[base + 2: base + 4])[0]
        best = None
        for i in range(n):
            pid, eid, off = struct.unpack(">HHI", data[base + 4 + 8 * i: base + 12 + 8 * i])
            fmt = struct.unpack(">H", data[base + off: base + off + 2])[0]
            if fmt == 12 or (fmt == 4 and best is None):
                best = (fmt, base + off)
        fmt, o = best
        m = {}
        if fmt == 4:
            segx2 = struct.unpack(">H", data[o + 6: o + 8])[0]
            seg = segx2 // 2
            ends = struct.unpack(f">{seg}H", data[o + 14: o + 14 + segx2])
            starts = struct.unpack(f">{seg}H", data[o + 16 + segx2: o + 16 + 2 * segx2])
            deltas = struct.unpack(f">{seg}h", data[o + 16 + 2 * segx2: o + 16 + 3 * segx2])
            ro_base = o + 16 + 3 * segx2
            ros = struct.unpack(f">{seg}H", data[ro_base: ro_base + segx2])
            for s in range(seg):
                for c in range(starts[s], ends[s] + 1):
                    if c == 0xFFFF:
                        continue
                    if ros[s] == 0:
                        g = (c + deltas[s]) & 0xFFFF
                    else:
                        a = ro_base + 2 * s + ros[s] + 2 * (c - starts[s])
                        g = struct.unpack(">H", data[a: a + 2])[0]
                        if g:
                            g = (g + deltas[s]) & 0xFFFF
                    if g:
                        m[c] = g
        else:
            groups = struct.unpack(">I", data[o + 12: o + 16])[0]
            for i in range(groups):
                sc, ec, sg = struct.unpack(">III", data[o + 16 + 12 * i: o + 28 + 12 * i])
                for c in range(sc, ec + 1):
                    m[c] = sg + c - sc
        return m

    def width(self, text, size, spacing):
        total = 0.0
        missing = []
        for ch in text:
            g = self.cmap.get(ord(ch))
            if g is None:
                missing.append(ch)
                g = 0
            a = self.adv[g] if g < len(self.adv) else self.adv[-1]
            total += a / self.upm * size + spacing
        return total, missing


def role(name):
    t = json.loads(TOKENS.read_text(encoding="utf-8"))["typography"]
    r = t["scale"].get(name) or t["custom"].get(name)
    if r is None:
        sys.exit(f"unknown role {name}")
    weight = r["fontWeight"]
    key = (r["family"], weight)
    if key not in FILES:  # nearest instance
        key = min((k for k in FILES if k[0] == r["family"]), key=lambda k: abs(k[1] - weight))
    return Font(FONT_DIR / FILES[key]), r["fontSize"], r.get("letterSpacing", 0)


def main(argv):
    if argv and argv[0] == "--glyphs":
        for (fam, w), f in FILES.items():
            font = Font(FONT_DIR / f)
            miss = [c for c in argv[1] if ord(c) not in font.cmap]
            print(f"{f}: missing {' '.join(f'U+{ord(c):04X}' for c in miss) or 'none'}")
        return
    rows = []
    if argv and argv[0] == "--file":
        for line in pathlib.Path(argv[1]).read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                r, budget, text = line.split("\t")
                rows.append((r, budget, text))
    else:
        rows = [(argv[0], "", t) for t in argv[1:]]
    for r, budget, text in rows:
        font, size, sp = role(r)
        w, miss = font.width(text, size, sp)
        flag = f"  MISSING {''.join(miss)}" if miss else ""
        b = f" ({budget})" if budget else ""
        fit = ""
        if budget:
            bb = float(budget)
            fit = "  fits" if w * 1.3 <= bb else ("  wraps/tight at 1.3" if w <= bb else "  OVER at 1.0")
        print(f"{r:14} {w:6.0f} / {w * 1.3:4.0f}dp{b}{fit}  {text}{flag}")


if __name__ == "__main__":
    main(sys.argv[1:])
