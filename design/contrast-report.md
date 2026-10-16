# Contrast report

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

## Summary

Pairs checked: 152, failures: 0.

| Theme | Lowest text pair (needs 4.5) | Lowest non-text pair (needs 3.0) |
| --- | ---: | ---: |
| light | 5.02 | 3.48 |
| dark | 5.02 | 3.90 |

## Full tables

### Light theme

| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| onPrimary | primary | #FFFFFF | #4355B9 | 6.48 | 4.5 | pass | text on primary (filled button, badge) |
| onPrimaryContainer | primaryContainer | #00105C | #DEE0FF | 13.20 | 4.5 | pass | text on primaryContainer |
| onSecondary | secondary | #FFFFFF | #5B5D72 | 6.45 | 4.5 | pass | text on secondary (filled button, badge) |
| onSecondaryContainer | secondaryContainer | #181A2C | #E0E1F9 | 13.33 | 4.5 | pass | text on secondaryContainer |
| onTertiary | tertiary | #FFFFFF | #7A5368 | 6.43 | 4.5 | pass | text on tertiary (filled button, badge) |
| onTertiaryContainer | tertiaryContainer | #2F1124 | #FFD8EA | 13.26 | 4.5 | pass | text on tertiaryContainer |
| onError | error | #FFFFFF | #BA1A1A | 6.46 | 4.5 | pass | text on error (filled button, badge) |
| onErrorContainer | errorContainer | #410002 | #FFDAD6 | 13.26 | 4.5 | pass | text on errorContainer |
| onBackground | background | #1B1B21 | #FBF8FF | 16.30 | 4.5 | pass | body text on background |
| onSurfaceVariant | surfaceVariant | #46464F | #E3E1EC | 7.22 | 4.5 | pass | text on surfaceVariant |
| onSurface | surface | #1B1B21 | #FBF8FF | 16.30 | 4.5 | pass | primary text |
| onSurfaceVariant | surface | #46464F | #FBF8FF | 8.88 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceDim | #1B1B21 | #DBD9E0 | 12.25 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceDim | #46464F | #DBD9E0 | 6.67 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceBright | #1B1B21 | #FBF8FF | 16.30 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceBright | #46464F | #FBF8FF | 8.88 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLowest | #1B1B21 | #FFFFFF | 17.14 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLowest | #46464F | #FFFFFF | 9.34 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLow | #1B1B21 | #F5F2FA | 15.48 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLow | #46464F | #F5F2FA | 8.43 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainer | #1B1B21 | #EFEDF4 | 14.76 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainer | #46464F | #EFEDF4 | 8.04 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHigh | #1B1B21 | #E9E7EF | 13.99 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHigh | #46464F | #E9E7EF | 7.62 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHighest | #1B1B21 | #E4E1E9 | 13.26 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHighest | #46464F | #E4E1E9 | 7.22 | 4.5 | pass | secondary text, supporting text, icons |
| primary | surface | #4355B9 | #FBF8FF | 6.17 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerLow | #4355B9 | #F5F2FA | 5.86 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainer | #4355B9 | #EFEDF4 | 5.59 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHigh | #4355B9 | #E9E7EF | 5.29 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHighest | #4355B9 | #E4E1E9 | 5.02 | 4.5 | pass | text button, link, selected label |
| error | surface | #BA1A1A | #FBF8FF | 6.14 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerLow | #BA1A1A | #F5F2FA | 5.83 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerHigh | #BA1A1A | #E9E7EF | 5.27 | 4.5 | pass | field error text, destructive text button |
| inverseOnSurface | inverseSurface | #F2EFF7 | #303036 | 11.53 | 4.5 | pass | snackbar text |
| inversePrimary | inverseSurface | #BAC3FF | #303036 | 7.70 | 4.5 | pass | snackbar action |
| outline | surface | #767680 | #FBF8FF | 4.27 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerLow | #767680 | #F5F2FA | 4.06 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerHigh | #767680 | #E9E7EF | 3.67 | 3.0 | pass | text-field and option-card border |
| primary | surface | #4355B9 | #FBF8FF | 6.17 | 3.0 | pass | filled button / focus ring boundary |
| error | surface | #BA1A1A | #FBF8FF | 6.14 | 3.0 | pass | error field border, error icon |
| onCorrect | correct | #FFFFFF | #1E6B30 | 6.56 | 4.5 | pass | text/icon on solid correct badge |
| onCorrectContainer | correctContainer | #002108 | #B7F1BC | 13.37 | 4.5 | pass | option card text, correct state |
| onWrong | wrong | #FFFFFF | #B3261E | 6.54 | 4.5 | pass | text/icon on solid wrong badge |
| onWrongContainer | wrongContainer | #410E0B | #F9DEDC | 12.77 | 4.5 | pass | option card text, wrong state |
| onPendingContainer | pendingContainer | #00105C | #DEE0FF | 13.20 | 4.5 | pass | option card text, selected/pending |
| onConfidence1Container | confidence1Container | #181A2C | #E0E1F9 | 13.33 | 4.5 | pass | Əminlik 1 chip label |
| onConfidence2Container | confidence2Container | #00105C | #BAC3FF | 10.04 | 4.5 | pass | Əminlik 2 chip label |
| onConfidence3Container | confidence3Container | #FFFFFF | #293CA0 | 9.34 | 4.5 | pass | Əminlik 3 chip label |
| onBannerInfoContainer | bannerInfoContainer | #181A2C | #E0E1F9 | 13.33 | 4.5 | pass | info banner text |
| onBannerWarningContainer | bannerWarningContainer | #311300 | #FFDCC6 | 13.32 | 4.5 | pass | offline/warning banner text |
| timerNormal | surface | #4355B9 | #FBF8FF | 6.17 | 4.5 | pass | timer digits (normal) |
| timerWarning | surface | #9C4300 | #FBF8FF | 6.21 | 4.5 | pass | timer digits (last 5 s) |
| timerNormal | timerTrack | #4355B9 | #E4E1E9 | 5.02 | 3.0 | pass | timer arc vs track |
| timerWarning | timerTrack | #9C4300 | #E4E1E9 | 5.05 | 3.0 | pass | timer arc vs track (last 5 s) |
| correct | surface | #1E6B30 | #FBF8FF | 6.24 | 4.5 | pass | 'Düzgün cavab' label, check icon |
| correct | surfaceContainerLow | #1E6B30 | #F5F2FA | 5.92 | 4.5 | pass | review card: correct option label |
| wrong | surface | #B3261E | #FBF8FF | 6.22 | 4.5 | pass | wrong label, cross icon |
| wrong | surfaceContainerLow | #B3261E | #F5F2FA | 5.90 | 4.5 | pass | review card: your answer label |
| correct | correctContainer | #1E6B30 | #B7F1BC | 5.10 | 3.0 | pass | check icon / border on correct card |
| wrong | wrongContainer | #B3261E | #F9DEDC | 5.14 | 3.0 | pass | cross icon / border on wrong card |
| confidenceSelectedOutline | surface | #00105C | #FBF8FF | 16.26 | 3.0 | pass | selected Əminlik chip ring vs page |
| confidence3Container | surface | #293CA0 | #FBF8FF | 8.88 | 3.0 | pass | Əminlik 3 chip boundary vs page |
| confidenceSelectedOutline | surfaceContainerHigh | #00105C | #E9E7EF | 13.95 | 3.0 | pass | duel: selected Əminlik chip ring on the confidence panel |
| confidence3Container | surfaceContainerHigh | #293CA0 | #E9E7EF | 7.62 | 3.0 | pass | duel: Əminlik 3 chip boundary on the confidence panel |
| outline | surfaceContainerHighest | #767680 | #E4E1E9 | 3.48 | 3.0 | pass | duel: neutral reveal panel border (timeout / missed) |
| tertiary | surface | #7A5368 | #FBF8FF | 6.12 | 3.0 | pass | duel: opponent avatar accent ring (intro, result) |
| tertiary | surfaceContainerLow | #7A5368 | #F5F2FA | 5.81 | 3.0 | pass | duel: opponent accent ring in the player panel |
| primary | surfaceContainerLow | #4355B9 | #F5F2FA | 5.86 | 3.0 | pass | duel: own accent ring in the player panel, calibration bar |
| onSurfaceVariant | timerTrack | #46464F | #E4E1E9 | 7.22 | 3.0 | pass | duel: timer ring in the result phase / frozen after own answer |
| timerNormal | surfaceContainerLow | #4355B9 | #F5F2FA | 5.86 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #1E6B30 | #EFEDF4 | 5.65 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #B3261E | #EFEDF4 | 5.63 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #46464F | #EFEDF4 | 8.04 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #1E6B30 | #B7F1BC | 5.10 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #B3261E | #F9DEDC | 5.14 | 4.5 | pass | duel reveal: points text on a wrong player panel |

### Dark theme

| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| onPrimary | primary | #08218A | #BAC3FF | 7.74 | 4.5 | pass | text on primary (filled button, badge) |
| onPrimaryContainer | primaryContainer | #DEE0FF | #293CA0 | 7.21 | 4.5 | pass | text on primaryContainer |
| onSecondary | secondary | #2D2F42 | #C4C5DD | 7.75 | 4.5 | pass | text on secondary (filled button, badge) |
| onSecondaryContainer | secondaryContainer | #E0E1F9 | #434659 | 7.22 | 4.5 | pass | text on secondaryContainer |
| onTertiary | tertiary | #47263A | #EAB9D2 | 7.70 | 4.5 | pass | text on tertiary (filled button, badge) |
| onTertiaryContainer | tertiaryContainer | #FFD8EA | #603C51 | 7.19 | 4.5 | pass | text on tertiaryContainer |
| onError | error | #690005 | #FFB4AB | 7.72 | 4.5 | pass | text on error (filled button, badge) |
| onErrorContainer | errorContainer | #FFDAD6 | #93000A | 7.24 | 4.5 | pass | text on errorContainer |
| onBackground | background | #E4E1E9 | #131318 | 14.33 | 4.5 | pass | body text on background |
| onSurfaceVariant | surfaceVariant | #C6C5D0 | #46464F | 5.47 | 4.5 | pass | text on surfaceVariant |
| onSurface | surface | #E4E1E9 | #131318 | 14.33 | 4.5 | pass | primary text |
| onSurfaceVariant | surface | #C6C5D0 | #131318 | 10.84 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceDim | #E4E1E9 | #131318 | 14.33 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceDim | #C6C5D0 | #131318 | 10.84 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceBright | #E4E1E9 | #39393F | 8.87 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceBright | #C6C5D0 | #39393F | 6.72 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLowest | #E4E1E9 | #0E0E13 | 14.89 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLowest | #C6C5D0 | #0E0E13 | 11.27 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLow | #E4E1E9 | #1B1B21 | 13.26 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLow | #C6C5D0 | #1B1B21 | 10.04 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainer | #E4E1E9 | #1F1F25 | 12.68 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainer | #C6C5D0 | #1F1F25 | 9.60 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHigh | #E4E1E9 | #2A292F | 11.15 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHigh | #C6C5D0 | #2A292F | 8.44 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHighest | #E4E1E9 | #35343A | 9.54 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHighest | #C6C5D0 | #35343A | 7.22 | 4.5 | pass | secondary text, supporting text, icons |
| primary | surface | #BAC3FF | #131318 | 10.88 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerLow | #BAC3FF | #1B1B21 | 10.07 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainer | #BAC3FF | #1F1F25 | 9.63 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHigh | #BAC3FF | #2A292F | 8.47 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHighest | #BAC3FF | #35343A | 7.24 | 4.5 | pass | text button, link, selected label |
| error | surface | #FFB4AB | #131318 | 10.91 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerLow | #FFB4AB | #1B1B21 | 10.09 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerHigh | #FFB4AB | #2A292F | 8.49 | 4.5 | pass | field error text, destructive text button |
| inverseOnSurface | inverseSurface | #303036 | #E4E1E9 | 10.14 | 4.5 | pass | snackbar text |
| inversePrimary | inverseSurface | #4355B9 | #E4E1E9 | 5.02 | 4.5 | pass | snackbar action |
| outline | surface | #90909A | #131318 | 5.86 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerLow | #90909A | #1B1B21 | 5.42 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerHigh | #90909A | #2A292F | 4.56 | 3.0 | pass | text-field and option-card border |
| primary | surface | #BAC3FF | #131318 | 10.88 | 3.0 | pass | filled button / focus ring boundary |
| error | surface | #FFB4AB | #131318 | 10.91 | 3.0 | pass | error field border, error icon |
| onCorrect | correct | #00391A | #8DD898 | 7.75 | 4.5 | pass | text/icon on solid correct badge |
| onCorrectContainer | correctContainer | #B7F1BC | #06531F | 7.21 | 4.5 | pass | option card text, correct state |
| onWrong | wrong | #601410 | #F2B8B5 | 7.66 | 4.5 | pass | text/icon on solid wrong badge |
| onWrongContainer | wrongContainer | #F9DEDC | #8C1D18 | 7.17 | 4.5 | pass | option card text, wrong state |
| onPendingContainer | pendingContainer | #DEE0FF | #293CA0 | 7.21 | 4.5 | pass | option card text, selected/pending |
| onConfidence1Container | confidence1Container | #E0E1F9 | #434659 | 7.22 | 4.5 | pass | Əminlik 1 chip label |
| onConfidence2Container | confidence2Container | #DEE0FF | #293CA0 | 7.21 | 4.5 | pass | Əminlik 2 chip label |
| onConfidence3Container | confidence3Container | #00105C | #BAC3FF | 10.04 | 4.5 | pass | Əminlik 3 chip label |
| onBannerInfoContainer | bannerInfoContainer | #E0E1F9 | #434659 | 7.22 | 4.5 | pass | info banner text |
| onBannerWarningContainer | bannerWarningContainer | #FFDCC6 | #723400 | 7.39 | 4.5 | pass | offline/warning banner text |
| timerNormal | surface | #BAC3FF | #131318 | 10.88 | 4.5 | pass | timer digits (normal) |
| timerWarning | surface | #FFB68A | #131318 | 10.86 | 4.5 | pass | timer digits (last 5 s) |
| timerNormal | timerTrack | #BAC3FF | #35343A | 7.24 | 3.0 | pass | timer arc vs track |
| timerWarning | timerTrack | #FFB68A | #35343A | 7.23 | 3.0 | pass | timer arc vs track (last 5 s) |
| correct | surface | #8DD898 | #131318 | 10.94 | 4.5 | pass | 'Düzgün cavab' label, check icon |
| correct | surfaceContainerLow | #8DD898 | #1B1B21 | 10.13 | 4.5 | pass | review card: correct option label |
| wrong | surface | #F2B8B5 | #131318 | 10.85 | 4.5 | pass | wrong label, cross icon |
| wrong | surfaceContainerLow | #F2B8B5 | #1B1B21 | 10.04 | 4.5 | pass | review card: your answer label |
| correct | correctContainer | #8DD898 | #06531F | 5.48 | 3.0 | pass | check icon / border on correct card |
| wrong | wrongContainer | #F2B8B5 | #8C1D18 | 5.34 | 3.0 | pass | cross icon / border on wrong card |
| confidenceSelectedOutline | surface | #FFFFFF | #131318 | 18.52 | 3.0 | pass | selected Əminlik chip ring vs page |
| confidence3Container | surface | #BAC3FF | #131318 | 10.88 | 3.0 | pass | Əminlik 3 chip boundary vs page |
| confidenceSelectedOutline | surfaceContainerHigh | #FFFFFF | #2A292F | 14.42 | 3.0 | pass | duel: selected Əminlik chip ring on the confidence panel |
| confidence3Container | surfaceContainerHigh | #BAC3FF | #2A292F | 8.47 | 3.0 | pass | duel: Əminlik 3 chip boundary on the confidence panel |
| outline | surfaceContainerHighest | #90909A | #35343A | 3.90 | 3.0 | pass | duel: neutral reveal panel border (timeout / missed) |
| tertiary | surface | #EAB9D2 | #131318 | 10.91 | 3.0 | pass | duel: opponent avatar accent ring (intro, result) |
| tertiary | surfaceContainerLow | #EAB9D2 | #1B1B21 | 10.09 | 3.0 | pass | duel: opponent accent ring in the player panel |
| primary | surfaceContainerLow | #BAC3FF | #1B1B21 | 10.07 | 3.0 | pass | duel: own accent ring in the player panel, calibration bar |
| onSurfaceVariant | timerTrack | #C6C5D0 | #35343A | 7.22 | 3.0 | pass | duel: timer ring in the result phase / frozen after own answer |
| timerNormal | surfaceContainerLow | #BAC3FF | #1B1B21 | 10.07 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #8DD898 | #1F1F25 | 9.69 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #F2B8B5 | #1F1F25 | 9.60 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #C6C5D0 | #1F1F25 | 9.60 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #8DD898 | #06531F | 5.48 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #F2B8B5 | #8C1D18 | 5.34 | 4.5 | pass | duel reveal: points text on a wrong player panel |
