# Bundled fonts (design tokens 3.1.0)

Both fonts are under the SIL Open Font License 1.1 (`<font>/OFL.txt`, copied unmodified
from github.com/google/fonts). No Reserved Font Name is declared in either file. The
licence text ships in the APK as the assets `licenses/fraunces/OFL.txt` and
`licenses/plusjakartasans/OFL.txt` (`licenseAssets` task in `../build.gradle.kts`) and is
shown on the licences screen.

| Font | Source (google/fonts `main`, 2026-11-17) | Upstream | Designers |
| --- | --- | --- | --- |
| Fraunces | `ofl/fraunces/Fraunces[SOFT,WONK,opsz,wght].ttf` (sha256 `177ff6c0…958abbb`) | github.com/undercasetype/Fraunces @ `d6d38578` | Undercase Type, Phaedra Charles, Flavia Zimbardi |
| Plus Jakarta Sans | `ofl/plusjakartasans/PlusJakartaSans[wght].ttf` (sha256 `89b3fb38…9ff68bc8`) | github.com/tokotype/PlusJakartaSans @ `163c006f` | Tokotype |

## Static instances in `src/main/res/font/`

minSdk is 24 and font variation axes need API 26, so only static instances are bundled
(`design/tokens.json` `typography.fontFamily.*.instances`):

| File | Axes |
| --- | --- |
| `fraunces_regular.ttf` | opsz 48, wght 400, SOFT 50, WONK 0 |
| `fraunces_semibold.ttf` | opsz 72, wght 600, SOFT 50, WONK 0 |
| `plusjakartasans_medium.ttf` | wght 500 |
| `plusjakartasans_semibold.ttf` | wght 600 |
| `plusjakartasans_bold.ttf` | wght 700 |
| `plusjakartasans_extrabold.ttf` | wght 800 |

Made with fontTools 4.60.2 by `build_fonts.py` (run it from a venv with `fonttools`,
`python -I build_fonts.py <dir with the two variable TTFs and nothing else> <out dir>`):
`varLib.instancer.instantiateVariableFont` at the axis values above, `OS/2.usWeightClass`
set to the weight, then `fontTools.subset` to U+0020–007E, U+00A0–024F (Latin-1, Latin
Extended-A/B) plus U+0259, U+2026, U+2212, U+2192, U+00B7, U+2013, U+2014, U+00AB,
U+00BB, U+2116 (№, tokens 3.1.0, both families) and, for Plus Jakarta Sans only, U+2153 /
U+2154 (⅓ ⅔, self-assessment marks; Fraunces has no such glyphs, so fractions are never set
in the serif), keeping the default layout features plus `tnum`, `case`, `locl`; no hinting.
Result: ≈ 47 KB per Fraunces file, ≈ 34 KB per Plus Jakarta Sans file.

Coverage: the script reads the `cmap` of every produced file and fails the check line
when any of `Ə ə Ğ ğ I ı İ i Ö ö Ü ü Ç ç Ş ş`, U+2212, U+00B7, U+2026, U+2116 or U+00BD (and
U+2153 / U+2154 for the sans) is missing; all six files pass (regenerated 2026-11-21 from the
same upstream files, hashes above re-checked). Plus Jakarta Sans keeps `tnum` (tabular figures); Fraunces has no
`tnum`, so it is used for static numbers only (`tokens.json`).
