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

Palette v2 (tokens 2.0.0, vivid game style): saturated faces (pressable buttons,
category tiles, Əminlik 3) carry white text only where #FFFFFF passes 4.5:1; dark
theme text accents use light tones. Depth edges (`*Depth`, `cardDepth`) and the
neutral button border are decorative (the label identifies the control) and are
not checked. Category slots are named `category.<slot>.*`, gradients
`gradient.<name>.*`; text on a gradient is checked against both stops.

## Summary

Pairs checked: 276, failures: 0.

| Theme | Lowest text pair (needs 4.5) | Lowest non-text pair (needs 3.0) |
| --- | ---: | ---: |
| light | 4.59 | 3.17 |
| dark | 4.98 | 3.68 |

## Full tables

### Light theme

| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| onPrimary | primary | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | text on primary (filled button, badge) |
| onPrimaryContainer | primaryContainer | #1C0A63 | #E7E1FF | 13.05 | 4.5 | pass | text on primaryContainer |
| onSecondary | secondary | #FFFFFF | #0068C9 | 5.49 | 4.5 | pass | text on secondary (filled button, badge) |
| onSecondaryContainer | secondaryContainer | #002A55 | #DCEBFF | 11.88 | 4.5 | pass | text on secondaryContainer |
| onTertiary | tertiary | #FFFFFF | #C2187A | 5.67 | 4.5 | pass | text on tertiary (filled button, badge) |
| onTertiaryContainer | tertiaryContainer | #3E0026 | #FFD9EC | 13.29 | 4.5 | pass | text on tertiaryContainer |
| onError | error | #FFFFFF | #B71C1C | 6.57 | 4.5 | pass | text on error (filled button, badge) |
| onErrorContainer | errorContainer | #410002 | #FFDAD6 | 13.26 | 4.5 | pass | text on errorContainer |
| onBackground | background | #1A1530 | #F7F5FF | 16.30 | 4.5 | pass | body text on background |
| onSurfaceVariant | surfaceVariant | #4A4463 | #E4DFF7 | 7.05 | 4.5 | pass | text on surfaceVariant |
| onSurface | surface | #1A1530 | #F7F5FF | 16.30 | 4.5 | pass | primary text |
| onSurfaceVariant | surface | #4A4463 | #F7F5FF | 8.47 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceDim | #1A1530 | #D9D3EE | 12.15 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceDim | #4A4463 | #D9D3EE | 6.31 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceBright | #1A1530 | #F7F5FF | 16.30 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceBright | #4A4463 | #F7F5FF | 8.47 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLowest | #1A1530 | #FFFFFF | 17.59 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLowest | #4A4463 | #FFFFFF | 9.14 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLow | #1A1530 | #F1EEFF | 15.42 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLow | #4A4463 | #F1EEFF | 8.01 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainer | #1A1530 | #EBE7FB | 14.54 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainer | #4A4463 | #EBE7FB | 7.55 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHigh | #1A1530 | #E4DFF7 | 13.57 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHigh | #4A4463 | #E4DFF7 | 7.05 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHighest | #1A1530 | #DCD6F2 | 12.52 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHighest | #4A4463 | #DCD6F2 | 6.50 | 4.5 | pass | secondary text, supporting text, icons |
| primary | surface | #5334E6 | #F7F5FF | 6.48 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerLow | #5334E6 | #F1EEFF | 6.13 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainer | #5334E6 | #EBE7FB | 5.78 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHigh | #5334E6 | #E4DFF7 | 5.40 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHighest | #5334E6 | #DCD6F2 | 4.98 | 4.5 | pass | text button, link, selected label |
| error | surface | #B71C1C | #F7F5FF | 6.09 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerLow | #B71C1C | #F1EEFF | 5.76 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerHigh | #B71C1C | #E4DFF7 | 5.07 | 4.5 | pass | field error text, destructive text button |
| inverseOnSurface | inverseSurface | #F3F0FF | #2E2945 | 12.32 | 4.5 | pass | snackbar text |
| inversePrimary | inverseSurface | #C0B3FF | #2E2945 | 7.33 | 4.5 | pass | snackbar action |
| outline | surface | #7A7394 | #F7F5FF | 4.13 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerLow | #7A7394 | #F1EEFF | 3.91 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerHigh | #7A7394 | #E4DFF7 | 3.44 | 3.0 | pass | text-field and option-card border |
| primary | surface | #5334E6 | #F7F5FF | 6.48 | 3.0 | pass | filled button / focus ring boundary |
| error | surface | #B71C1C | #F7F5FF | 6.09 | 3.0 | pass | error field border, error icon |
| onCorrect | correct | #FFFFFF | #0E7445 | 5.83 | 4.5 | pass | text/icon on solid correct badge |
| onCorrectContainer | correctContainer | #0B3B25 | #D8F8E7 | 11.10 | 4.5 | pass | option card text, correct state |
| onWrong | wrong | #FFFFFF | #C8213A | 5.62 | 4.5 | pass | text/icon on solid wrong badge |
| onWrongContainer | wrongContainer | #5C0010 | #FFE1E5 | 11.73 | 4.5 | pass | option card text, wrong state |
| onPendingContainer | pendingContainer | #1C0A63 | #E7E1FF | 13.05 | 4.5 | pass | option card text, selected/pending |
| onConfidence1Container | confidence1Container | #0B3566 | #E1F0FF | 10.56 | 4.5 | pass | Əminlik 1 chip label |
| onConfidence2Container | confidence2Container | #1C0A63 | #B9AAFF | 8.06 | 4.5 | pass | Əminlik 2 chip label |
| onConfidence3Container | confidence3Container | #FFFFFF | #4B2BD9 | 7.93 | 4.5 | pass | Əminlik 3 chip label |
| onBannerInfoContainer | bannerInfoContainer | #002A55 | #DCEBFF | 11.88 | 4.5 | pass | info banner text |
| onBannerWarningContainer | bannerWarningContainer | #3D1C00 | #FFE3C2 | 12.47 | 4.5 | pass | offline/warning banner text |
| timerNormal | surface | #5334E6 | #F7F5FF | 6.48 | 4.5 | pass | timer digits (normal) |
| timerWarning | surface | #B54A00 | #F7F5FF | 4.93 | 4.5 | pass | timer digits (last 5 s) |
| timerNormal | timerTrack | #5334E6 | #E4DFF7 | 5.40 | 3.0 | pass | timer arc vs track |
| timerWarning | timerTrack | #B54A00 | #E4DFF7 | 4.11 | 3.0 | pass | timer arc vs track (last 5 s) |
| correct | surface | #0E7445 | #F7F5FF | 5.40 | 4.5 | pass | 'Düzgün cavab' label, check icon |
| correct | surfaceContainerLow | #0E7445 | #F1EEFF | 5.11 | 4.5 | pass | review card: correct option label |
| wrong | surface | #C8213A | #F7F5FF | 5.21 | 4.5 | pass | wrong label, cross icon |
| wrong | surfaceContainerLow | #C8213A | #F1EEFF | 4.93 | 4.5 | pass | review card: your answer label |
| correct | correctContainer | #0E7445 | #D8F8E7 | 5.14 | 3.0 | pass | check icon / border on correct card |
| wrong | wrongContainer | #C8213A | #FFE1E5 | 4.59 | 3.0 | pass | cross icon / border on wrong card |
| confidenceSelectedOutline | surface | #1A1530 | #F7F5FF | 16.30 | 3.0 | pass | selected Əminlik chip ring vs page |
| confidence3Container | surface | #4B2BD9 | #F7F5FF | 7.35 | 3.0 | pass | Əminlik 3 chip boundary vs page |
| confidenceSelectedOutline | surfaceContainerHigh | #1A1530 | #E4DFF7 | 13.57 | 3.0 | pass | duel: selected Əminlik chip ring on the confidence panel |
| confidence3Container | surfaceContainerHigh | #4B2BD9 | #E4DFF7 | 6.12 | 3.0 | pass | duel: Əminlik 3 chip boundary on the confidence panel |
| outline | surfaceContainerHighest | #7A7394 | #DCD6F2 | 3.17 | 3.0 | pass | duel: neutral reveal panel border (timeout / missed) |
| tertiary | surface | #C2187A | #F7F5FF | 5.25 | 3.0 | pass | duel: opponent avatar accent ring (intro, result) |
| tertiary | surfaceContainerLow | #C2187A | #F1EEFF | 4.97 | 3.0 | pass | duel: opponent accent ring in the player panel |
| primary | surfaceContainerLow | #5334E6 | #F1EEFF | 6.13 | 3.0 | pass | duel: own accent ring in the player panel, calibration bar |
| onSurfaceVariant | timerTrack | #4A4463 | #E4DFF7 | 7.05 | 3.0 | pass | duel: timer ring in the result phase / frozen after own answer |
| timerNormal | surfaceContainerLow | #5334E6 | #F1EEFF | 6.13 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #0E7445 | #EBE7FB | 4.82 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #C8213A | #EBE7FB | 4.65 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #4A4463 | #EBE7FB | 7.55 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #0E7445 | #D8F8E7 | 5.14 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #C8213A | #FFE1E5 | 4.59 | 4.5 | pass | duel reveal: points text on a wrong player panel |
| onSurface | surface | #1A1530 | #F7F5FF | 16.30 | 4.5 | pass | web: body text and headings |
| onSurfaceVariant | surface | #4A4463 | #F7F5FF | 8.47 | 4.5 | pass | web: meta line, nav links, footer |
| primary | surface | #5334E6 | #F7F5FF | 6.48 | 4.5 | pass | web: underlined links |
| onSurface | surfaceContainer | #1A1530 | #EBE7FB | 14.54 | 4.5 | pass | web: nav link on hover |
| primary | surfaceContainerLow | #5334E6 | #F1EEFF | 6.13 | 4.5 | pass | web: link card title, table-of-contents links |
| onSurfaceVariant | surfaceContainerLow | #4A4463 | #F1EEFF | 8.01 | 4.5 | pass | web: link card supporting text |
| onPrimaryContainer | primaryContainer | #1C0A63 | #E7E1FF | 13.05 | 4.5 | pass | web: current nav pill, callout |
| onBannerWarningContainer | bannerWarningContainer | #3D1C00 | #FFE3C2 | 12.47 | 4.5 | pass | web: 'Mətn hazırlanır' placeholder box |
| onPrimary | primary | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | web: skip link |
| primary | surface | #5334E6 | #F7F5FF | 6.48 | 3.0 | pass | web: 3px focus ring |
| onPrimary | primary | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | store: app name on the feature graphic |
| tertiaryContainer | primary | #FFD9EC | #5334E6 | 5.46 | 3.0 | pass | store: back bubble of the mark on the feature graphic |
| primaryContainer | primary | #E7E1FF | #5334E6 | 5.53 | 3.0 | pass | store: decorative option bars on the feature graphic |
| onPressablePrimary | pressablePrimary | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | pressable primary button label |
| onPressableNeutral | pressableNeutral | #4426D6 | #FFFFFF | 8.42 | 4.5 | pass | pressable neutral (secondary) button label |
| onPressableDestructive | pressableDestructive | #FFFFFF | #B71C1C | 6.57 | 4.5 | pass | pressable destructive button label |
| onPressableDuel | pressableDuel | #FFFFFF | #C2187A | 5.67 | 4.5 | pass | duel CTA button label (home duel card, play again) |
| onSurface | pressableNeutral | #1A1530 | #FFFFFF | 17.59 | 4.5 | pass | option card text on the white game card |
| onSurfaceVariant | pressableNeutral | #4A4463 | #FFFFFF | 9.14 | 4.5 | pass | supporting text on white game cards |
| primary | surfaceContainerLowest | #5334E6 | #FFFFFF | 7.00 | 4.5 | pass | option letter / link on a white game card |
| onPrimaryContainer | primaryContainer | #1C0A63 | #E7E1FF | 13.05 | 4.5 | pass | default option letter badge |
| primary | pendingContainer | #5334E6 | #E7E1FF | 5.53 | 3.0 | pass | selected option 2dp border vs its container |
| onConfidence1Container | confidence1Container | #0B3566 | #E1F0FF | 10.56 | 3.0 | pass | Əminlik 1 signal bars |
| onConfidence3Container | confidence3Container | #FFFFFF | #4B2BD9 | 7.93 | 3.0 | pass | Əminlik 3 signal bars |
| onTertiaryContainer | tertiaryContainer | #3E0026 | #FFD9EC | 13.29 | 4.5 | pass | round-7 pill, opponent badge, Bot badge text + icon (components.md §32) |
| gradient.duelHero.on | gradient.duelHero.start | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | duel hero card text, gradient start |
| gradient.duelHero.on | gradient.duelHero.end | #FFFFFF | #C2187A | 5.67 | 4.5 | pass | duel hero card text, gradient end |
| gradient.resultWin.on | gradient.resultWin.start | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | win result hero text, gradient start |
| gradient.resultWin.on | gradient.resultWin.end | #FFFFFF | #0068C9 | 5.49 | 4.5 | pass | win result hero text, gradient end |
| gradient.dailyHero.on | gradient.dailyHero.start | #FFFFFF | #0068C9 | 5.49 | 4.5 | pass | daily card / daily result hero text, gradient start |
| gradient.dailyHero.on | gradient.dailyHero.end | #FFFFFF | #00796B | 5.32 | 4.5 | pass | daily card / daily result hero text, gradient end |
| streak | surfaceContainerLowest | #C2410C | #FFFFFF | 5.18 | 4.5 | pass | streak flame icon on white game cards (profile streak card, progress card) |
| streak | surface | #C2410C | #F7F5FF | 4.80 | 3.0 | pass | streak flame icon on the page |
| streak | streakContainer | #C2410C | #FFE4D4 | 4.27 | 3.0 | pass | filled flame in the active streak badge (home header) |
| onStreakContainer | streakContainer | #4A1500 | #FFE4D4 | 12.37 | 4.5 | pass | streak number in the active streak badge, streak hint banner, '+1' chip |
| onSurface | surfaceContainerHigh | #1A1530 | #E4DFF7 | 13.57 | 4.5 | pass | streak number in the inactive streak badge |
| onSurfaceVariant | surfaceContainerHigh | #4A4463 | #E4DFF7 | 7.05 | 3.0 | pass | outlined flame in the inactive streak badge |
| primary | surfaceContainerHighest | #5334E6 | #DCD6F2 | 4.98 | 3.0 | pass | XP bar fill vs track (result card, profile, home mini bar, category rows) |
| onPrimaryContainer | primaryContainer | #1C0A63 | #E7E1FF | 13.05 | 4.5 | pass | level chip (home header), level-up strip |
| onPressablePrimary | pressablePrimary | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | level badge number (profile level card) |
| onSecondaryContainer | secondaryContainer | #002A55 | #DCEBFF | 11.88 | 4.5 | pass | streak freeze row / freeze-used banner |
| secondary | surfaceContainerLowest | #0068C9 | #FFFFFF | 5.49 | 3.0 | pass | streak freeze shield icon on a white card |
| correct | surfaceContainerLowest | #0E7445 | #FFFFFF | 5.83 | 3.0 | pass | daily result square (correct) vs white share card |
| wrong | surfaceContainerLowest | #C8213A | #FFFFFF | 5.62 | 3.0 | pass | daily result square (wrong) vs white share card |
| outline | surfaceContainerLowest | #7A7394 | #FFFFFF | 4.46 | 3.0 | pass | daily result square (no answer) border vs white share card |
| onSurfaceVariant | surfaceContainerHighest | #4A4463 | #DCD6F2 | 6.50 | 4.5 | pass | dash glyph in the no-answer square, notification card supporting text |
| category.violet.onFace | category.violet.face | #FFFFFF | #6A3DE8 | 6.09 | 4.5 | pass | category tile name + count, slot violet |
| category.violet.onContainer | category.violet.container | #2A0F7A | #ECE5FF | 11.89 | 4.5 | pass | category chip / quiz top bar tint, slot violet |
| category.blue.onFace | category.blue.face | #FFFFFF | #1565D8 | 5.41 | 4.5 | pass | category tile name + count, slot blue |
| category.blue.onContainer | category.blue.container | #00285F | #DDEBFF | 11.85 | 4.5 | pass | category chip / quiz top bar tint, slot blue |
| category.teal.onFace | category.teal.face | #FFFFFF | #00796B | 5.32 | 4.5 | pass | category tile name + count, slot teal |
| category.teal.onContainer | category.teal.container | #00332D | #D2F5EF | 11.94 | 4.5 | pass | category chip / quiz top bar tint, slot teal |
| category.green.onFace | category.green.face | #FFFFFF | #2E7D32 | 5.13 | 4.5 | pass | category tile name + count, slot green |
| category.green.onContainer | category.green.container | #0E3A12 | #DCF5D9 | 11.09 | 4.5 | pass | category chip / quiz top bar tint, slot green |
| category.orange.onFace | category.orange.face | #FFFFFF | #C2410C | 5.18 | 4.5 | pass | category tile name + count, slot orange |
| category.orange.onContainer | category.orange.container | #4A1500 | #FFE4D4 | 12.37 | 4.5 | pass | category chip / quiz top bar tint, slot orange |
| category.rose.onFace | category.rose.face | #FFFFFF | #C62850 | 5.50 | 4.5 | pass | category tile name + count, slot rose |
| category.rose.onContainer | category.rose.container | #4F0018 | #FFE0E7 | 12.68 | 4.5 | pass | category chip / quiz top bar tint, slot rose |
| category.pink.onFace | category.pink.face | #FFFFFF | #B0237F | 6.19 | 4.5 | pass | category tile name + count, slot pink |
| category.pink.onContainer | category.pink.container | #45002D | #FFDDF1 | 13.06 | 4.5 | pass | category chip / quiz top bar tint, slot pink |
| category.ocean.onFace | category.ocean.face | #FFFFFF | #0B6E99 | 5.67 | 4.5 | pass | category tile name + count, slot ocean |
| category.ocean.onContainer | category.ocean.container | #00314A | #D6EFFA | 11.43 | 4.5 | pass | category chip / quiz top bar tint, slot ocean |

### Dark theme

| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| onPrimary | primary | #23107A | #C0B3FF | 7.80 | 4.5 | pass | text on primary (filled button, badge) |
| onPrimaryContainer | primaryContainer | #EDE8FF | #4A2FD6 | 6.59 | 4.5 | pass | text on primaryContainer |
| onSecondary | secondary | #003060 | #9ECAFF | 7.76 | 4.5 | pass | text on secondary (filled button, badge) |
| onSecondaryContainer | secondaryContainer | #D6E8FF | #004A8F | 7.09 | 4.5 | pass | text on secondaryContainer |
| onTertiary | tertiary | #5C0038 | #FFADD6 | 8.07 | 4.5 | pass | text on tertiary (filled button, badge) |
| onTertiaryContainer | tertiaryContainer | #FFD9EC | #8A1059 | 7.13 | 4.5 | pass | text on tertiaryContainer |
| onError | error | #690005 | #FFB4AB | 7.72 | 4.5 | pass | text on error (filled button, badge) |
| onErrorContainer | errorContainer | #FFDAD6 | #93000A | 7.24 | 4.5 | pass | text on errorContainer |
| onBackground | background | #EDE9FF | #121027 | 15.68 | 4.5 | pass | body text on background |
| onSurfaceVariant | surfaceVariant | #C3BCE0 | #3A355C | 6.29 | 4.5 | pass | text on surfaceVariant |
| onSurface | surface | #EDE9FF | #121027 | 15.68 | 4.5 | pass | primary text |
| onSurfaceVariant | surface | #C3BCE0 | #121027 | 10.26 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceDim | #EDE9FF | #121027 | 15.68 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceDim | #C3BCE0 | #121027 | 10.26 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceBright | #EDE9FF | #3A355C | 9.60 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceBright | #C3BCE0 | #3A355C | 6.29 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLowest | #EDE9FF | #0C0A1C | 16.45 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLowest | #C3BCE0 | #0C0A1C | 10.77 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLow | #EDE9FF | #1A1733 | 14.58 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLow | #C3BCE0 | #1A1733 | 9.54 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainer | #EDE9FF | #201C3D | 13.67 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainer | #C3BCE0 | #201C3D | 8.95 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHigh | #EDE9FF | #2A2649 | 12.03 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHigh | #C3BCE0 | #2A2649 | 7.87 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHighest | #EDE9FF | #353057 | 10.34 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHighest | #C3BCE0 | #353057 | 6.77 | 4.5 | pass | secondary text, supporting text, icons |
| primary | surface | #C0B3FF | #121027 | 9.86 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerLow | #C0B3FF | #1A1733 | 9.17 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainer | #C0B3FF | #201C3D | 8.60 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHigh | #C0B3FF | #2A2649 | 7.56 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHighest | #C0B3FF | #353057 | 6.50 | 4.5 | pass | text button, link, selected label |
| error | surface | #FFB4AB | #121027 | 10.95 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerLow | #FFB4AB | #1A1733 | 10.19 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerHigh | #FFB4AB | #2A2649 | 8.40 | 4.5 | pass | field error text, destructive text button |
| inverseOnSurface | inverseSurface | #2E2945 | #EDE9FF | 11.65 | 4.5 | pass | snackbar text |
| inversePrimary | inverseSurface | #5334E6 | #EDE9FF | 5.90 | 4.5 | pass | snackbar action |
| outline | surface | #8F88AD | #121027 | 5.57 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerLow | #8F88AD | #1A1733 | 5.18 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerHigh | #8F88AD | #2A2649 | 4.28 | 3.0 | pass | text-field and option-card border |
| primary | surface | #C0B3FF | #121027 | 9.86 | 3.0 | pass | filled button / focus ring boundary |
| error | surface | #FFB4AB | #121027 | 10.95 | 3.0 | pass | error field border, error icon |
| onCorrect | correct | #00391F | #5FD39A | 7.02 | 4.5 | pass | text/icon on solid correct badge |
| onCorrectContainer | correctContainer | #B8F5D4 | #0E3B2A | 10.16 | 4.5 | pass | option card text, correct state |
| onWrong | wrong | #5C0010 | #FF8F9C | 6.61 | 4.5 | pass | text/icon on solid wrong badge |
| onWrongContainer | wrongContainer | #FFD9DD | #4A0E18 | 11.86 | 4.5 | pass | option card text, wrong state |
| onPendingContainer | pendingContainer | #EDE8FF | #4A2FD6 | 6.59 | 4.5 | pass | option card text, selected/pending |
| onConfidence1Container | confidence1Container | #D6EBFF | #1F3A5C | 9.45 | 4.5 | pass | Əminlik 1 chip label |
| onConfidence2Container | confidence2Container | #E7E1FF | #3A2A99 | 8.43 | 4.5 | pass | Əminlik 2 chip label |
| onConfidence3Container | confidence3Container | #1C0A63 | #C0B3FF | 8.75 | 4.5 | pass | Əminlik 3 chip label |
| onBannerInfoContainer | bannerInfoContainer | #D6E8FF | #004A8F | 7.09 | 4.5 | pass | info banner text |
| onBannerWarningContainer | bannerWarningContainer | #FFDDB8 | #6B3300 | 7.76 | 4.5 | pass | offline/warning banner text |
| timerNormal | surface | #C0B3FF | #121027 | 9.86 | 4.5 | pass | timer digits (normal) |
| timerWarning | surface | #FFB68A | #121027 | 10.90 | 4.5 | pass | timer digits (last 5 s) |
| timerNormal | timerTrack | #C0B3FF | #353057 | 6.50 | 3.0 | pass | timer arc vs track |
| timerWarning | timerTrack | #FFB68A | #353057 | 7.19 | 3.0 | pass | timer arc vs track (last 5 s) |
| correct | surface | #5FD39A | #121027 | 9.98 | 4.5 | pass | 'Düzgün cavab' label, check icon |
| correct | surfaceContainerLow | #5FD39A | #1A1733 | 9.28 | 4.5 | pass | review card: correct option label |
| wrong | surface | #FF8F9C | #121027 | 8.56 | 4.5 | pass | wrong label, cross icon |
| wrong | surfaceContainerLow | #FF8F9C | #1A1733 | 7.96 | 4.5 | pass | review card: your answer label |
| correct | correctContainer | #5FD39A | #0E3B2A | 6.72 | 3.0 | pass | check icon / border on correct card |
| wrong | wrongContainer | #FF8F9C | #4A0E18 | 7.07 | 3.0 | pass | cross icon / border on wrong card |
| confidenceSelectedOutline | surface | #FFFFFF | #121027 | 18.60 | 3.0 | pass | selected Əminlik chip ring vs page |
| confidence3Container | surface | #C0B3FF | #121027 | 9.86 | 3.0 | pass | Əminlik 3 chip boundary vs page |
| confidenceSelectedOutline | surfaceContainerHigh | #FFFFFF | #2A2649 | 14.27 | 3.0 | pass | duel: selected Əminlik chip ring on the confidence panel |
| confidence3Container | surfaceContainerHigh | #C0B3FF | #2A2649 | 7.56 | 3.0 | pass | duel: Əminlik 3 chip boundary on the confidence panel |
| outline | surfaceContainerHighest | #8F88AD | #353057 | 3.68 | 3.0 | pass | duel: neutral reveal panel border (timeout / missed) |
| tertiary | surface | #FFADD6 | #121027 | 10.81 | 3.0 | pass | duel: opponent avatar accent ring (intro, result) |
| tertiary | surfaceContainerLow | #FFADD6 | #1A1733 | 10.05 | 3.0 | pass | duel: opponent accent ring in the player panel |
| primary | surfaceContainerLow | #C0B3FF | #1A1733 | 9.17 | 3.0 | pass | duel: own accent ring in the player panel, calibration bar |
| onSurfaceVariant | timerTrack | #C3BCE0 | #353057 | 6.77 | 3.0 | pass | duel: timer ring in the result phase / frozen after own answer |
| timerNormal | surfaceContainerLow | #C0B3FF | #1A1733 | 9.17 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #5FD39A | #201C3D | 8.70 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #FF8F9C | #201C3D | 7.46 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #C3BCE0 | #201C3D | 8.95 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #5FD39A | #0E3B2A | 6.72 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #FF8F9C | #4A0E18 | 7.07 | 4.5 | pass | duel reveal: points text on a wrong player panel |
| onSurface | surface | #EDE9FF | #121027 | 15.68 | 4.5 | pass | web: body text and headings |
| onSurfaceVariant | surface | #C3BCE0 | #121027 | 10.26 | 4.5 | pass | web: meta line, nav links, footer |
| primary | surface | #C0B3FF | #121027 | 9.86 | 4.5 | pass | web: underlined links |
| onSurface | surfaceContainer | #EDE9FF | #201C3D | 13.67 | 4.5 | pass | web: nav link on hover |
| primary | surfaceContainerLow | #C0B3FF | #1A1733 | 9.17 | 4.5 | pass | web: link card title, table-of-contents links |
| onSurfaceVariant | surfaceContainerLow | #C3BCE0 | #1A1733 | 9.54 | 4.5 | pass | web: link card supporting text |
| onPrimaryContainer | primaryContainer | #EDE8FF | #4A2FD6 | 6.59 | 4.5 | pass | web: current nav pill, callout |
| onBannerWarningContainer | bannerWarningContainer | #FFDDB8 | #6B3300 | 7.76 | 4.5 | pass | web: 'Mətn hazırlanır' placeholder box |
| onPrimary | primary | #23107A | #C0B3FF | 7.80 | 4.5 | pass | web: skip link |
| primary | surface | #C0B3FF | #121027 | 9.86 | 3.0 | pass | web: 3px focus ring |
| onPrimary | primary | #23107A | #C0B3FF | 7.80 | 4.5 | pass | store: app name on the feature graphic |
| tertiaryContainer | primary | #8A1059 | #C0B3FF | 4.84 | 3.0 | pass | store: back bubble of the mark on the feature graphic |
| primaryContainer | primary | #4A2FD6 | #C0B3FF | 4.17 | 3.0 | pass | store: decorative option bars on the feature graphic |
| onPressablePrimary | pressablePrimary | #FFFFFF | #6A4DFF | 5.10 | 4.5 | pass | pressable primary button label |
| onPressableNeutral | pressableNeutral | #C0B3FF | #2A2649 | 7.56 | 4.5 | pass | pressable neutral (secondary) button label |
| onPressableDestructive | pressableDestructive | #FFFFFF | #D32F2F | 4.98 | 4.5 | pass | pressable destructive button label |
| onPressableDuel | pressableDuel | #FFFFFF | #C2187A | 5.67 | 4.5 | pass | duel CTA button label (home duel card, play again) |
| onSurface | pressableNeutral | #EDE9FF | #2A2649 | 12.03 | 4.5 | pass | option card text on the white game card |
| onSurfaceVariant | pressableNeutral | #C3BCE0 | #2A2649 | 7.87 | 4.5 | pass | supporting text on white game cards |
| primary | surfaceContainerLowest | #C0B3FF | #0C0A1C | 10.35 | 4.5 | pass | option letter / link on a white game card |
| onPrimaryContainer | primaryContainer | #EDE8FF | #4A2FD6 | 6.59 | 4.5 | pass | default option letter badge |
| primary | pendingContainer | #C0B3FF | #4A2FD6 | 4.17 | 3.0 | pass | selected option 2dp border vs its container |
| onConfidence1Container | confidence1Container | #D6EBFF | #1F3A5C | 9.45 | 3.0 | pass | Əminlik 1 signal bars |
| onConfidence3Container | confidence3Container | #1C0A63 | #C0B3FF | 8.75 | 3.0 | pass | Əminlik 3 signal bars |
| onTertiaryContainer | tertiaryContainer | #FFD9EC | #8A1059 | 7.13 | 4.5 | pass | round-7 pill, opponent badge, Bot badge text + icon (components.md §32) |
| gradient.duelHero.on | gradient.duelHero.start | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | duel hero card text, gradient start |
| gradient.duelHero.on | gradient.duelHero.end | #FFFFFF | #C2187A | 5.67 | 4.5 | pass | duel hero card text, gradient end |
| gradient.resultWin.on | gradient.resultWin.start | #FFFFFF | #5334E6 | 7.00 | 4.5 | pass | win result hero text, gradient start |
| gradient.resultWin.on | gradient.resultWin.end | #FFFFFF | #0068C9 | 5.49 | 4.5 | pass | win result hero text, gradient end |
| gradient.dailyHero.on | gradient.dailyHero.start | #FFFFFF | #0068C9 | 5.49 | 4.5 | pass | daily card / daily result hero text, gradient start |
| gradient.dailyHero.on | gradient.dailyHero.end | #FFFFFF | #00796B | 5.32 | 4.5 | pass | daily card / daily result hero text, gradient end |
| streak | surfaceContainerLowest | #FF9A5C | #0C0A1C | 9.32 | 4.5 | pass | streak flame icon on white game cards (profile streak card, progress card) |
| streak | surface | #FF9A5C | #121027 | 8.88 | 3.0 | pass | streak flame icon on the page |
| streak | streakContainer | #FF9A5C | #4D1F08 | 6.62 | 3.0 | pass | filled flame in the active streak badge (home header) |
| onStreakContainer | streakContainer | #FFDCC8 | #4D1F08 | 10.77 | 4.5 | pass | streak number in the active streak badge, streak hint banner, '+1' chip |
| onSurface | surfaceContainerHigh | #EDE9FF | #2A2649 | 12.03 | 4.5 | pass | streak number in the inactive streak badge |
| onSurfaceVariant | surfaceContainerHigh | #C3BCE0 | #2A2649 | 7.87 | 3.0 | pass | outlined flame in the inactive streak badge |
| primary | surfaceContainerHighest | #C0B3FF | #353057 | 6.50 | 3.0 | pass | XP bar fill vs track (result card, profile, home mini bar, category rows) |
| onPrimaryContainer | primaryContainer | #EDE8FF | #4A2FD6 | 6.59 | 4.5 | pass | level chip (home header), level-up strip |
| onPressablePrimary | pressablePrimary | #FFFFFF | #6A4DFF | 5.10 | 4.5 | pass | level badge number (profile level card) |
| onSecondaryContainer | secondaryContainer | #D6E8FF | #004A8F | 7.09 | 4.5 | pass | streak freeze row / freeze-used banner |
| secondary | surfaceContainerLowest | #9ECAFF | #0C0A1C | 11.48 | 3.0 | pass | streak freeze shield icon on a white card |
| correct | surfaceContainerLowest | #5FD39A | #0C0A1C | 10.48 | 3.0 | pass | daily result square (correct) vs white share card |
| wrong | surfaceContainerLowest | #FF8F9C | #0C0A1C | 8.98 | 3.0 | pass | daily result square (wrong) vs white share card |
| outline | surfaceContainerLowest | #8F88AD | #0C0A1C | 5.85 | 3.0 | pass | daily result square (no answer) border vs white share card |
| onSurfaceVariant | surfaceContainerHighest | #C3BCE0 | #353057 | 6.77 | 4.5 | pass | dash glyph in the no-answer square, notification card supporting text |
| category.violet.onFace | category.violet.face | #FFFFFF | #6A3DE8 | 6.09 | 4.5 | pass | category tile name + count, slot violet |
| category.violet.onContainer | category.violet.container | #E3DAFF | #33246E | 9.73 | 4.5 | pass | category chip / quiz top bar tint, slot violet |
| category.blue.onFace | category.blue.face | #FFFFFF | #1565D8 | 5.41 | 4.5 | pass | category tile name + count, slot blue |
| category.blue.onContainer | category.blue.container | #D6E6FF | #0E3466 | 9.77 | 4.5 | pass | category chip / quiz top bar tint, slot blue |
| category.teal.onFace | category.teal.face | #FFFFFF | #00796B | 5.32 | 4.5 | pass | category tile name + count, slot teal |
| category.teal.onContainer | category.teal.container | #C4F0E8 | #073B35 | 10.07 | 4.5 | pass | category chip / quiz top bar tint, slot teal |
| category.green.onFace | category.green.face | #FFFFFF | #2E7D32 | 5.13 | 4.5 | pass | category tile name + count, slot green |
| category.green.onContainer | category.green.container | #CDEFC9 | #163B19 | 10.03 | 4.5 | pass | category chip / quiz top bar tint, slot green |
| category.orange.onFace | category.orange.face | #FFFFFF | #C2410C | 5.18 | 4.5 | pass | category tile name + count, slot orange |
| category.orange.onContainer | category.orange.container | #FFDCC8 | #4D1F08 | 10.77 | 4.5 | pass | category chip / quiz top bar tint, slot orange |
| category.rose.onFace | category.rose.face | #FFFFFF | #C62850 | 5.50 | 4.5 | pass | category tile name + count, slot rose |
| category.rose.onContainer | category.rose.container | #FFD6E0 | #4D1222 | 11.19 | 4.5 | pass | category chip / quiz top bar tint, slot rose |
| category.pink.onFace | category.pink.face | #FFFFFF | #B0237F | 6.19 | 4.5 | pass | category tile name + count, slot pink |
| category.pink.onContainer | category.pink.container | #FFD3EC | #4A1037 | 11.11 | 4.5 | pass | category chip / quiz top bar tint, slot pink |
| category.ocean.onFace | category.ocean.face | #FFFFFF | #0B6E99 | 5.67 | 4.5 | pass | category tile name + count, slot ocean |
| category.ocean.onContainer | category.ocean.container | #CDEAF7 | #0A3447 | 10.47 | 4.5 | pass | category chip / quiz top bar tint, slot ocean |
