#!/usr/bin/env python3
"""WCAG 2.1 contrast check for design/tokens.json.

Usage:  python3 design/tools/contrast.py            # prints the Markdown tables
        python3 design/tools/contrast.py --report > design/contrast-report.md
        python3 design/tools/contrast.py --check    # exit 1 if any pair fails

Every pair is (foreground, background, minimum ratio, usage).
4.5 = normal text, 3.0 = large text (>= 24sp, or >= 18.5sp bold) and
non-text UI parts (icons, borders, focus rings, timer arc).
Add a pair here whenever a spec puts a new colour combination on screen.
"""
import json
import pathlib
import sys

TOKENS = pathlib.Path(__file__).resolve().parent.parent / "tokens.json"

TEXT = 4.5
UI = 3.0

SURFACES = [
    "surface", "surfaceDim", "surfaceBright", "surfaceContainerLowest",
    "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh",
    "surfaceContainerHighest",
]

PAIRS = []
# Core Material 3 on*/container pairs
for role in ("primary", "secondary", "tertiary", "error"):
    cap = role[0].upper() + role[1:]
    PAIRS.append((f"on{cap}", role, TEXT, f"text on {role} (filled button, badge)"))
    PAIRS.append((f"on{cap}Container", f"{role}Container", TEXT, f"text on {role}Container"))
PAIRS.append(("onBackground", "background", TEXT, "body text on background"))
PAIRS.append(("onSurfaceVariant", "surfaceVariant", TEXT, "text on surfaceVariant"))
for s in SURFACES:
    PAIRS.append(("onSurface", s, TEXT, "primary text"))
    PAIRS.append(("onSurfaceVariant", s, TEXT, "secondary text, supporting text, icons"))
for s in ("surface", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh",
          "surfaceContainerHighest"):
    PAIRS.append(("primary", s, TEXT, "text button, link, selected label"))
for s in ("surface", "surfaceContainerLow", "surfaceContainerHigh"):
    PAIRS.append(("error", s, TEXT, "field error text, destructive text button"))
PAIRS.append(("inverseOnSurface", "inverseSurface", TEXT, "snackbar text"))
PAIRS.append(("inversePrimary", "inverseSurface", TEXT, "snackbar action"))
# Non-text UI
for s in ("surface", "surfaceContainerLow", "surfaceContainerHigh"):
    PAIRS.append(("outline", s, UI, "text-field and option-card border"))
PAIRS.append(("primary", "surface", UI, "filled button / focus ring boundary"))
PAIRS.append(("error", "surface", UI, "error field border, error icon"))

# Game roles
GAME = [
    ("onCorrect", "correct", TEXT, "text/icon on solid correct badge"),
    ("onCorrectContainer", "correctContainer", TEXT, "option card text, correct state"),
    ("onWrong", "wrong", TEXT, "text/icon on solid wrong badge"),
    ("onWrongContainer", "wrongContainer", TEXT, "option card text, wrong state"),
    ("onPendingContainer", "pendingContainer", TEXT, "option card text, selected/pending"),
    ("onConfidence1Container", "confidence1Container", TEXT, "Əminlik 1 chip label"),
    ("onConfidence2Container", "confidence2Container", TEXT, "Əminlik 2 chip label"),
    ("onConfidence3Container", "confidence3Container", TEXT, "Əminlik 3 chip label"),
    ("onBannerInfoContainer", "bannerInfoContainer", TEXT, "info banner text"),
    ("onBannerWarningContainer", "bannerWarningContainer", TEXT, "offline/warning banner text"),
    ("timerNormal", "surface", TEXT, "timer digits (normal)"),
    ("timerWarning", "surface", TEXT, "timer digits (last 5 s)"),
    ("timerNormal", "timerTrack", UI, "timer arc vs track"),
    ("timerWarning", "timerTrack", UI, "timer arc vs track (last 5 s)"),
    ("correct", "surface", TEXT, "'Düzgün cavab' label, check icon"),
    ("correct", "surfaceContainerLow", TEXT, "review card: correct option label"),
    ("wrong", "surface", TEXT, "wrong label, cross icon"),
    ("wrong", "surfaceContainerLow", TEXT, "review card: your answer label"),
    ("correct", "correctContainer", UI, "check icon / border on correct card"),
    ("wrong", "wrongContainer", UI, "cross icon / border on wrong card"),
    ("confidenceSelectedOutline", "surface", UI, "selected Əminlik chip ring vs page"),
    ("confidence3Container", "surface", UI, "Əminlik 3 chip boundary vs page"),
    # Duel and profile (design/screens/duel-*.md, profile.md)
    ("confidenceSelectedOutline", "surfaceContainerHigh", UI, "duel: selected Əminlik chip ring on the confidence panel"),
    ("confidence3Container", "surfaceContainerHigh", UI, "duel: Əminlik 3 chip boundary on the confidence panel"),
    ("outline", "surfaceContainerHighest", UI, "duel: neutral reveal panel border (timeout / missed)"),
    ("tertiary", "surface", UI, "duel: opponent avatar accent ring (intro, result)"),
    ("tertiary", "surfaceContainerLow", UI, "duel: opponent accent ring in the player panel"),
    ("primary", "surfaceContainerLow", UI, "duel: own accent ring in the player panel, calibration bar"),
    ("onSurfaceVariant", "timerTrack", UI, "duel: timer ring in the result phase / frozen after own answer"),
    ("timerNormal", "surfaceContainerLow", TEXT, "duel: search countdown digits on the search card"),
    ("correct", "surfaceContainer", TEXT, "duel result / profile: positive rating change on the rating card"),
    ("wrong", "surfaceContainer", TEXT, "duel result / profile: negative rating change on the rating card"),
    ("onSurfaceVariant", "surfaceContainer", TEXT, "duel result / profile: zero rating change, captions on the rating card"),
    ("correct", "correctContainer", TEXT, "duel reveal: points text on a correct player panel"),
    ("wrong", "wrongContainer", TEXT, "duel reveal: points text on a wrong player panel"),
    # Web legal pages (design/screens/web-legal.md, docs/legal/legal.css)
    ("onSurface", "surface", TEXT, "web: body text and headings"),
    ("onSurfaceVariant", "surface", TEXT, "web: meta line, nav links, footer"),
    ("primary", "surface", TEXT, "web: underlined links"),
    ("onSurface", "surfaceContainer", TEXT, "web: nav link on hover"),
    ("primary", "surfaceContainerLow", TEXT, "web: link card title, table-of-contents links"),
    ("onSurfaceVariant", "surfaceContainerLow", TEXT, "web: link card supporting text"),
    ("onPrimaryContainer", "primaryContainer", TEXT, "web: current nav pill, callout"),
    ("onBannerWarningContainer", "bannerWarningContainer", TEXT, "web: 'Mətn hazırlanır' placeholder box"),
    ("onPrimary", "primary", TEXT, "web: skip link"),
    ("primary", "surface", UI, "web: 3px focus ring"),
    # Play Store graphics (design/store/, light colours only on export)
    ("onPrimary", "primary", TEXT, "store: app name on the feature graphic"),
    ("tertiaryContainer", "primary", UI, "store: back bubble of the mark on the feature graphic"),
    ("primaryContainer", "primary", UI, "store: decorative option bars on the feature graphic"),
]


def lum(hex_colour):
    h = hex_colour.lstrip("#")
    channels = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def resolve(theme, name):
    c = theme["scheme"].get(name) or theme["game"].get(name)
    if c is None:
        raise KeyError(name)
    return c


HEADER = """# Contrast report

Generated from `design/tokens.json` by `python3 design/tools/contrast.py --report`
(WCAG 2.1 relative luminance; ratio = (L1 + 0.05) / (L2 + 0.05)). Do not edit by
hand: change the tokens or the pair list in the script and regenerate.
`--check` exits non-zero on any failure (usable in CI later).

Thresholds:
- **4.5:1** for all text, including text in buttons, chips, banners, snackbars,
  option cards and the timer digits.
- **3.0:1** for non-text UI that carries meaning (icons, text-field and card
  borders, timer arc against its track, selected-chip ring) and large text.

Not checked on purpose:
- Disabled content (`onSurface` at 38 %): exempt under WCAG 1.4.3; never used for
  information the user must read.
- `outlineVariant`: decorative dividers only, never the only boundary of a control.
- State layers (pressed/hover overlays): transient.

Colour is never the only signal: correct/wrong option states also show a check or
cross icon and a text label for TalkBack; the timer warning also changes the arc
and pulses the digits; Əminlik chips always show the number 1, 2 or 3.

The palette uses Material 3 tonal steps (tone 40/90/10 in light, 80/30/90 in dark),
which keep text pairs above 4.5:1; this script confirms every pair actually used.
"""


def main():
    tokens = json.loads(TOKENS.read_text(encoding="utf-8"))
    failures = 0
    minimum = {}
    out = []
    for theme_name in ("light", "dark"):
        theme = tokens["color"][theme_name]
        out.append(f"\n### {theme_name.capitalize()} theme\n")
        out.append("| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |")
        out.append("| --- | --- | --- | --- | ---: | ---: | --- | --- |")
        minimum[theme_name] = {TEXT: 99.0, UI: 99.0}
        for fg, bg, need, usage in PAIRS + GAME:
            f, b = resolve(theme, fg), resolve(theme, bg)
            r = ratio(f, b)
            minimum[theme_name][need] = min(minimum[theme_name][need], r)
            ok = r >= need
            failures += not ok
            out.append(f"| {fg} | {bg} | {f} | {b} | {r:.2f} | {need} | {'pass' if ok else 'FAIL'} | {usage} |")
    if "--report" in sys.argv:
        print(HEADER)
    print("## Summary\n")
    print(f"Pairs checked: {2 * len(PAIRS + GAME)}, failures: {failures}.\n")
    print("| Theme | Lowest text pair (needs 4.5) | Lowest non-text pair (needs 3.0) |")
    print("| --- | ---: | ---: |")
    for t, m in minimum.items():
        print(f"| {t} | {m[TEXT]:.2f} | {m[UI]:.2f} |")
    print("\n## Full tables")
    print("\n".join(out))
    if "--check" in sys.argv and failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
