# Nunito

- Source: https://github.com/google/fonts/tree/main/ofl/nunito — `Nunito[wght].ttf`
  (variable font, wght 200–1000; upstream https://github.com/googlefonts/nunito,
  commit `8c6a9bb9732545b9ed53f29ec5e1ab0ff53c4e6f` per `METADATA.pb`). Designers: Vernon
  Adams, Cyreal, Jacques Le Bailly.
- Licence: SIL Open Font License 1.1, see `OFL.txt` (copied unmodified from the same
  directory). No Reserved Font Name is declared.
- Bundled in `src/main/res/font/` as **static instances** of the variable font, one per weight
  used by `design/tokens.json`: 500 (`nunito_medium`), 600 (`nunito_semibold`), 700
  (`nunito_bold`), 800 (`nunito_extrabold`), 900 (`nunito_black`). minSdk is 24 and
  variable-font axes need API 26, so static files guarantee the right weight on every device.
- How the instances were made (fontTools 4.60):
  `fontTools.varLib.instancer.instantiateVariableFont(font, {"wght": W}, updateFontNames=True)`
  plus `OS/2.usWeightClass = W`. Glyph outlines are otherwise unchanged.
- Azerbaijani coverage checked against the cmap: `Ə ə Ğ ğ I ı İ i Ö ö Ü ü Ç ç Ş ş` and U+2212
  are all present.
- Nunito has no `tnum` OpenType feature, but its default digits are already fixed-width
  (all 600 units), so timers and scores do not jitter. `tabularFigures()` stays in place as a
  harmless hint for fonts that need it.
