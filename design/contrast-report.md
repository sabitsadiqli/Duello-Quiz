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

Palette v3 (tokens 3.0.0, "dəftər": paper, ink, one nar accent, saffron streak).
Not checked on purpose in v3: `game.cardBorder` / `outlineVariant` hairlines
(decorative, never the only boundary of a control), `game.streakFill` against the
page (the filled week dot is identified by its saffron ring and check glyph, both
checked), `game.navIndicator` against the bar (selection is also shown by the
filled icon, bold label and ink colour), and the sticker's rotation. The duel
card is `inverseSurface`; its CTA pill uses the page colours, checked against the
card.

## Summary

Pairs checked: 294, failures: 0.

| Theme | Lowest text pair (needs 4.5) | Lowest non-text pair (needs 3.0) |
| --- | ---: | ---: |
| light | 4.75 | 3.41 |
| dark | 4.55 | 3.31 |

## Full tables

### Light theme

| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| onPrimary | primary | #FFFFFF | #A82D25 | 6.85 | 4.5 | pass | text on primary (filled button, badge) |
| onPrimaryContainer | primaryContainer | #5C140E | #F5DCD3 | 10.29 | 4.5 | pass | text on primaryContainer |
| onSecondary | secondary | #FFFFFF | #2E5A78 | 7.37 | 4.5 | pass | text on secondary (filled button, badge) |
| onSecondaryContainer | secondaryContainer | #0F2D42 | #DCE7EE | 11.33 | 4.5 | pass | text on secondaryContainer |
| onTertiary | tertiary | #FFFFFF | #7B3F73 | 7.50 | 4.5 | pass | text on tertiary (filled button, badge) |
| onTertiaryContainer | tertiaryContainer | #3A1034 | #F0DDEA | 12.45 | 4.5 | pass | text on tertiaryContainer |
| onError | error | #FFFFFF | #A0241C | 7.60 | 4.5 | pass | text on error (filled button, badge) |
| onErrorContainer | errorContainer | #47100B | #F9DAD4 | 11.88 | 4.5 | pass | text on errorContainer |
| onBackground | background | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | body text on background |
| onSurfaceVariant | surfaceVariant | #5F574C | #E7DECC | 5.32 | 4.5 | pass | text on surfaceVariant |
| onSurface | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | primary text |
| onSurfaceVariant | surface | #5F574C | #F5EFE3 | 6.21 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceDim | #1E1B16 | #E3D9C7 | 12.27 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceDim | #5F574C | #E3D9C7 | 5.08 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceBright | #1E1B16 | #FAF6EE | 15.92 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceBright | #5F574C | #FAF6EE | 6.59 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLowest | #1E1B16 | #FCF9F3 | 16.33 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLowest | #5F574C | #FCF9F3 | 6.76 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLow | #1E1B16 | #F1EADD | 14.35 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLow | #5F574C | #F1EADD | 5.94 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainer | #1E1B16 | #EDE5D5 | 13.71 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainer | #5F574C | #EDE5D5 | 5.68 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHigh | #1E1B16 | #E7DECC | 12.85 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHigh | #5F574C | #E7DECC | 5.32 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHighest | #1E1B16 | #E0D6C3 | 11.91 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHighest | #5F574C | #E0D6C3 | 4.93 | 4.5 | pass | secondary text, supporting text, icons |
| primary | surface | #A82D25 | #F5EFE3 | 5.98 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerLow | #A82D25 | #F1EADD | 5.72 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainer | #A82D25 | #EDE5D5 | 5.47 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHigh | #A82D25 | #E7DECC | 5.12 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHighest | #A82D25 | #E0D6C3 | 4.75 | 4.5 | pass | text button, link, selected label |
| error | surface | #A0241C | #F5EFE3 | 6.64 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerLow | #A0241C | #F1EADD | 6.35 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerHigh | #A0241C | #E7DECC | 5.69 | 4.5 | pass | field error text, destructive text button |
| inverseOnSurface | inverseSurface | #F5EFE3 | #1E1B16 | 14.99 | 4.5 | pass | snackbar text |
| inversePrimary | inverseSurface | #EC7B6F | #1E1B16 | 6.24 | 4.5 | pass | snackbar action |
| outline | surface | #7A6F60 | #F5EFE3 | 4.29 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerLow | #7A6F60 | #F1EADD | 4.11 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerHigh | #7A6F60 | #E7DECC | 3.68 | 3.0 | pass | text-field and option-card border |
| primary | surface | #A82D25 | #F5EFE3 | 5.98 | 3.0 | pass | filled button / focus ring boundary |
| error | surface | #A0241C | #F5EFE3 | 6.64 | 3.0 | pass | error field border, error icon |
| onCorrect | correct | #FFFFFF | #2E6B3F | 6.38 | 4.5 | pass | text/icon on solid correct badge |
| onCorrectContainer | correctContainer | #143820 | #DDEBD8 | 10.49 | 4.5 | pass | option card text, correct state |
| onWrong | wrong | #FFFFFF | #A3262E | 7.31 | 4.5 | pass | text/icon on solid wrong badge |
| onWrongContainer | wrongContainer | #4E0F13 | #F7DBD9 | 11.48 | 4.5 | pass | option card text, wrong state |
| onPendingContainer | pendingContainer | #1E1B16 | #E7DECC | 12.85 | 4.5 | pass | option card text, selected/pending |
| onConfidence1Container | confidence1Container | #1E1B16 | #EDE5D5 | 13.71 | 4.5 | pass | Əminlik 1 chip label |
| onConfidence2Container | confidence2Container | #1E1B16 | #CFC2A9 | 9.76 | 4.5 | pass | Əminlik 2 chip label |
| onConfidence3Container | confidence3Container | #F5EFE3 | #1E1B16 | 14.99 | 4.5 | pass | Əminlik 3 chip label |
| onBannerInfoContainer | bannerInfoContainer | #0F2D42 | #DCE7EE | 11.33 | 4.5 | pass | info banner text |
| onBannerWarningContainer | bannerWarningContainer | #3D2A00 | #F3E2B8 | 10.71 | 4.5 | pass | offline/warning banner text |
| timerNormal | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | timer digits (normal) |
| timerWarning | surface | #A2530A | #F5EFE3 | 4.85 | 4.5 | pass | timer digits (last 5 s) |
| timerNormal | timerTrack | #1E1B16 | #E2D9C8 | 12.25 | 3.0 | pass | timer arc vs track |
| timerWarning | timerTrack | #A2530A | #E2D9C8 | 3.97 | 3.0 | pass | timer arc vs track (last 5 s) |
| correct | surface | #2E6B3F | #F5EFE3 | 5.57 | 4.5 | pass | 'Düzgün cavab' label, check icon |
| correct | surfaceContainerLow | #2E6B3F | #F1EADD | 5.33 | 4.5 | pass | review card: correct option label |
| wrong | surface | #A3262E | #F5EFE3 | 6.38 | 4.5 | pass | wrong label, cross icon |
| wrong | surfaceContainerLow | #A3262E | #F1EADD | 6.11 | 4.5 | pass | review card: your answer label |
| correct | correctContainer | #2E6B3F | #DDEBD8 | 5.15 | 3.0 | pass | check icon / border on correct card |
| wrong | wrongContainer | #A3262E | #F7DBD9 | 5.60 | 3.0 | pass | cross icon / border on wrong card |
| confidenceSelectedOutline | surface | #A82D25 | #F5EFE3 | 5.98 | 3.0 | pass | selected Əminlik chip ring vs page |
| confidence3Container | surface | #1E1B16 | #F5EFE3 | 14.99 | 3.0 | pass | Əminlik 3 chip boundary vs page |
| confidenceSelectedOutline | surfaceContainerHigh | #A82D25 | #E7DECC | 5.12 | 3.0 | pass | duel: selected Əminlik chip ring on the confidence panel |
| confidence3Container | surfaceContainerHigh | #1E1B16 | #E7DECC | 12.85 | 3.0 | pass | duel: Əminlik 3 chip boundary on the confidence panel |
| outline | surfaceContainerHighest | #7A6F60 | #E0D6C3 | 3.41 | 3.0 | pass | duel: neutral reveal panel border (timeout / missed) |
| tertiary | surface | #7B3F73 | #F5EFE3 | 6.55 | 3.0 | pass | duel: opponent avatar accent ring (intro, result) |
| tertiary | surfaceContainerLow | #7B3F73 | #F1EADD | 6.27 | 3.0 | pass | duel: opponent accent ring in the player panel |
| primary | surfaceContainerLow | #A82D25 | #F1EADD | 5.72 | 3.0 | pass | duel: own accent ring in the player panel, calibration bar |
| onSurfaceVariant | timerTrack | #5F574C | #E2D9C8 | 5.08 | 3.0 | pass | duel: timer ring in the result phase / frozen after own answer |
| timerNormal | surfaceContainerLow | #1E1B16 | #F1EADD | 14.35 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #2E6B3F | #EDE5D5 | 5.10 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #A3262E | #EDE5D5 | 5.83 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #5F574C | #EDE5D5 | 5.68 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #2E6B3F | #DDEBD8 | 5.15 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #A3262E | #F7DBD9 | 5.60 | 4.5 | pass | duel reveal: points text on a wrong player panel |
| onSurface | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | web: body text and headings |
| onSurfaceVariant | surface | #5F574C | #F5EFE3 | 6.21 | 4.5 | pass | web: meta line, nav links, footer |
| primary | surface | #A82D25 | #F5EFE3 | 5.98 | 4.5 | pass | web: underlined links |
| onSurface | surfaceContainer | #1E1B16 | #EDE5D5 | 13.71 | 4.5 | pass | web: nav link on hover |
| primary | surfaceContainerLow | #A82D25 | #F1EADD | 5.72 | 4.5 | pass | web: link card title, table-of-contents links |
| onSurfaceVariant | surfaceContainerLow | #5F574C | #F1EADD | 5.94 | 4.5 | pass | web: link card supporting text |
| onPrimaryContainer | primaryContainer | #5C140E | #F5DCD3 | 10.29 | 4.5 | pass | web: current nav pill, callout |
| onBannerWarningContainer | bannerWarningContainer | #3D2A00 | #F3E2B8 | 10.71 | 4.5 | pass | web: 'Mətn hazırlanır' placeholder box |
| onPrimary | primary | #FFFFFF | #A82D25 | 6.85 | 4.5 | pass | web: skip link |
| primary | surface | #A82D25 | #F5EFE3 | 5.98 | 3.0 | pass | web: 3px focus ring |
| onPrimary | primary | #FFFFFF | #A82D25 | 6.85 | 4.5 | pass | primary pill button label (components.md §1) |
| onSurface | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | secondary pill button label (transparent face on the page) |
| outline | surface | #7A6F60 | #F5EFE3 | 4.29 | 3.0 | pass | secondary pill button 1dp border |
| onError | error | #FFFFFF | #A0241C | 7.60 | 4.5 | pass | destructive pill button label |
| onSurface | surfaceContainerLowest | #1E1B16 | #FCF9F3 | 16.33 | 4.5 | pass | text on the lightest container (text field) |
| primary | surfaceContainerLowest | #A82D25 | #FCF9F3 | 6.52 | 4.5 | pass | link on the lightest container |
| onPrimaryContainer | primaryContainer | #5C140E | #F5DCD3 | 10.29 | 4.5 | pass | default option letter badge (v2) / level-up strip |
| onSurface | pendingContainer | #1E1B16 | #E7DECC | 12.85 | 3.0 | pass | selected option 2dp ink border vs its container |
| onPendingContainer | pendingContainer | #1E1B16 | #E7DECC | 12.85 | 4.5 | pass | selected option text |
| outline | surface | #7A6F60 | #F5EFE3 | 4.29 | 3.0 | pass | option card 1dp border vs page |
| onSurface | surfaceContainerHigh | #1E1B16 | #E7DECC | 12.85 | 4.5 | pass | option letter badge (dimmed) / list row on panel |
| onConfidence1Container | confidence1Container | #1E1B16 | #EDE5D5 | 13.71 | 3.0 | pass | Əminlik 1 signal bars |
| onConfidence2Container | confidence2Container | #1E1B16 | #CFC2A9 | 9.76 | 3.0 | pass | Əminlik 2 signal bars |
| onConfidence3Container | confidence3Container | #F5EFE3 | #1E1B16 | 14.99 | 3.0 | pass | Əminlik 3 signal bars |
| onTertiaryContainer | tertiaryContainer | #3A1034 | #F0DDEA | 12.45 | 4.5 | pass | round-7 pill, opponent badge, Bot badge text + icon (components.md §32) |
| onSurface | surfaceContainer | #1E1B16 | #EDE5D5 | 13.71 | 4.5 | pass | nav: selected label and icon on the bar |
| onSurfaceVariant | surfaceContainer | #5F574C | #EDE5D5 | 5.68 | 4.5 | pass | nav: unselected label and icon |
| onSurface | navIndicator | #1E1B16 | #E0D6C3 | 11.91 | 4.5 | pass | nav: selected icon on the indicator pill |
| primary | surfaceContainer | #A82D25 | #EDE5D5 | 5.47 | 3.0 | pass | nav: 'not played yet' badge dot on the Gündəlik item |
| onSurfaceVariant | surface | #5F574C | #F5EFE3 | 6.21 | 4.5 | pass | home: date line, overline 'TƏK OYNA · 10 SUAL' |
| streak | surface | #8F5F00 | #F5EFE3 | 4.82 | 4.5 | pass | home top row: streak number and flame (saffron) |
| primary | surface | #A82D25 | #F5EFE3 | 5.98 | 4.5 | pass | home: category numerals 01-05 (serif) |
| primary | surface | #A82D25 | #F5EFE3 | 5.98 | 3.0 | pass | home: squiggle underline under the greeting word |
| onSurfaceVariant | surface | #5F574C | #F5EFE3 | 6.21 | 3.0 | pass | home: category line icons and chevrons |
| inverseOnSurface | inverseSurface | #F5EFE3 | #1E1B16 | 14.99 | 4.5 | pass | duel card: 'Duel' title and hint |
| inverseOnSurfaceVariant | inverseSurface | #C9BFAE | #1E1B16 | 9.44 | 4.5 | pass | duel card: '~3 dəq' and muted text |
| onSurface | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | duel card CTA pill (page colours on the inverted card) |
| surface | inverseSurface | #F5EFE3 | #1E1B16 | 14.99 | 3.0 | pass | duel card CTA pill boundary vs the card |
| onPrimary | primary | #FFFFFF | #A82D25 | 6.85 | 4.5 | pass | rating sticker text on the accent pill |
| outline | surface | #7A6F60 | #F5EFE3 | 4.29 | 3.0 | pass | Gündəlik: dashed empty answer box |
| correct | surface | #2E6B3F | #F5EFE3 | 5.57 | 3.0 | pass | Gündəlik: filled correct answer box vs card |
| onCorrect | correct | #FFFFFF | #2E6B3F | 6.38 | 4.5 | pass | Gündəlik: check glyph in a correct box |
| wrong | surface | #A3262E | #F5EFE3 | 6.38 | 3.0 | pass | Gündəlik: filled wrong answer box vs card |
| onWrong | wrong | #FFFFFF | #A3262E | 7.31 | 4.5 | pass | Gündəlik: cross glyph in a wrong box |
| onSurfaceVariant | surfaceContainerHighest | #5F574C | #E0D6C3 | 4.93 | 4.5 | pass | Gündəlik: dash glyph in a 'no answer' box |
| onSurface | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | Gündəlik: '#12' serif numeral, streak title |
| streak | surface | #8F5F00 | #F5EFE3 | 4.82 | 3.0 | pass | week dot: 1.5dp saffron ring (active) and dashed ring (today pending) |
| onStreakFill | streakFill | #1E1B16 | #D9A23A | 7.50 | 4.5 | pass | week dot: check glyph on the saffron fill |
| secondary | surface | #2E5A78 | #F5EFE3 | 6.43 | 3.0 | pass | week dot: freeze ring |
| onSecondaryContainer | secondaryContainer | #0F2D42 | #DCE7EE | 11.33 | 4.5 | pass | week dot: shield glyph on the freeze fill; freeze message |
| outline | surface | #7A6F60 | #F5EFE3 | 4.29 | 3.0 | pass | week dot: missed-day ring |
| onSurfaceVariant | surface | #5F574C | #F5EFE3 | 6.21 | 4.5 | pass | week row: day abbreviations 'B.e … B' |
| onSurface | surface | #1E1B16 | #F5EFE3 | 14.99 | 4.5 | pass | week row: today's abbreviation (bold) |
| onStreakContainer | streakContainer | #3D2A00 | #F3E2B8 | 10.71 | 4.5 | pass | streak hint message on the Gündəlik tab |
| streak | streakContainer | #8F5F00 | #F3E2B8 | 4.31 | 3.0 | pass | flame icon in the streak hint message |
| onSurface | surfaceContainerHigh | #1E1B16 | #E7DECC | 12.85 | 4.5 | pass | 'streak lost' message |
| timerNormal | surfaceContainerLow | #1E1B16 | #F1EADD | 14.35 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #2E6B3F | #EDE5D5 | 5.10 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #A3262E | #EDE5D5 | 5.83 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #5F574C | #EDE5D5 | 5.68 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #2E6B3F | #DDEBD8 | 5.15 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #A3262E | #F7DBD9 | 5.60 | 4.5 | pass | duel reveal: points text on a wrong player panel |
| primary | surfaceContainerHighest | #A82D25 | #E0D6C3 | 4.75 | 3.0 | pass | XP bar fill vs track (result card, profile, category rows) |
| onSecondaryContainer | secondaryContainer | #0F2D42 | #DCE7EE | 11.33 | 4.5 | pass | streak freeze row / freeze-used message |
| secondary | surface | #2E5A78 | #F5EFE3 | 6.43 | 3.0 | pass | streak freeze shield icon on a card |
| correct | surfaceContainerLowest | #2E6B3F | #FCF9F3 | 6.07 | 3.0 | pass | daily result square (correct) vs share card |
| wrong | surfaceContainerLowest | #A3262E | #FCF9F3 | 6.95 | 3.0 | pass | daily result square (wrong) vs share card |
| outline | surfaceContainerLowest | #7A6F60 | #FCF9F3 | 4.68 | 3.0 | pass | daily result square (no answer) border vs share card |
| onSurfaceVariant | surfaceContainerHighest | #5F574C | #E0D6C3 | 4.93 | 4.5 | pass | dash glyph in the no-answer square, notification card supporting text |
| onSurfaceVariant | surfaceContainerHigh | #5F574C | #E7DECC | 5.32 | 3.0 | pass | outlined flame (not yet today) |
| streak | surfaceContainerLowest | #8F5F00 | #FCF9F3 | 5.25 | 4.5 | pass | streak flame + number on the lightest card |

### Dark theme

| Foreground | Background | FG hex | BG hex | Ratio | Min | Result | Usage |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| onPrimary | primary | #2B0A06 | #EC7B6F | 6.64 | 4.5 | pass | text on primary (filled button, badge) |
| onPrimaryContainer | primaryContainer | #FFDAD3 | #5C1F18 | 9.75 | 4.5 | pass | text on primaryContainer |
| onSecondary | secondary | #0C2A3E | #9EC3DD | 7.98 | 4.5 | pass | text on secondary (filled button, badge) |
| onSecondaryContainer | secondaryContainer | #D6E7F3 | #1F3B50 | 9.21 | 4.5 | pass | text on secondaryContainer |
| onTertiary | tertiary | #3D1036 | #DDA6D0 | 7.87 | 4.5 | pass | text on tertiary (filled button, badge) |
| onTertiaryContainer | tertiaryContainer | #F8D8EF | #57294F | 8.75 | 4.5 | pass | text on tertiaryContainer |
| onError | error | #5C0D06 | #FFB4A9 | 8.15 | 4.5 | pass | text on error (filled button, badge) |
| onErrorContainer | errorContainer | #FFDAD4 | #7D1F16 | 7.81 | 4.5 | pass | text on errorContainer |
| onBackground | background | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | body text on background |
| onSurfaceVariant | surfaceVariant | #B5AB97 | #2E2A22 | 6.28 | 4.5 | pass | text on surfaceVariant |
| onSurface | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | primary text |
| onSurfaceVariant | surface | #B5AB97 | #17150F | 8.03 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceDim | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceDim | #B5AB97 | #17150F | 8.03 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceBright | #EFE7D6 | #3B362D | 9.75 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceBright | #B5AB97 | #3B362D | 5.27 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLowest | #EFE7D6 | #110F0A | 15.57 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLowest | #B5AB97 | #110F0A | 8.42 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerLow | #EFE7D6 | #1D1A14 | 14.11 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerLow | #B5AB97 | #1D1A14 | 7.63 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainer | #EFE7D6 | #23201A | 13.20 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainer | #B5AB97 | #23201A | 7.14 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHigh | #EFE7D6 | #2D2921 | 11.77 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHigh | #B5AB97 | #2D2921 | 6.37 | 4.5 | pass | secondary text, supporting text, icons |
| onSurface | surfaceContainerHighest | #EFE7D6 | #38332A | 10.19 | 4.5 | pass | primary text |
| onSurfaceVariant | surfaceContainerHighest | #B5AB97 | #38332A | 5.51 | 4.5 | pass | secondary text, supporting text, icons |
| primary | surface | #EC7B6F | #17150F | 6.63 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerLow | #EC7B6F | #1D1A14 | 6.30 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainer | #EC7B6F | #23201A | 5.90 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHigh | #EC7B6F | #2D2921 | 5.26 | 4.5 | pass | text button, link, selected label |
| primary | surfaceContainerHighest | #EC7B6F | #38332A | 4.55 | 4.5 | pass | text button, link, selected label |
| error | surface | #FFB4A9 | #17150F | 10.74 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerLow | #FFB4A9 | #1D1A14 | 10.21 | 4.5 | pass | field error text, destructive text button |
| error | surfaceContainerHigh | #FFB4A9 | #2D2921 | 8.52 | 4.5 | pass | field error text, destructive text button |
| inverseOnSurface | inverseSurface | #1E1B16 | #EFE7D6 | 13.95 | 4.5 | pass | snackbar text |
| inversePrimary | inverseSurface | #A82D25 | #EFE7D6 | 5.57 | 4.5 | pass | snackbar action |
| outline | surface | #8C8272 | #17150F | 4.82 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerLow | #8C8272 | #1D1A14 | 4.59 | 3.0 | pass | text-field and option-card border |
| outline | surfaceContainerHigh | #8C8272 | #2D2921 | 3.83 | 3.0 | pass | text-field and option-card border |
| primary | surface | #EC7B6F | #17150F | 6.63 | 3.0 | pass | filled button / focus ring boundary |
| error | surface | #FFB4A9 | #17150F | 10.74 | 3.0 | pass | error field border, error icon |
| onCorrect | correct | #0F2B17 | #8FCB9B | 8.11 | 4.5 | pass | text/icon on solid correct badge |
| onCorrectContainer | correctContainer | #CDEBD2 | #1F3524 | 10.30 | 4.5 | pass | option card text, correct state |
| onWrong | wrong | #4A0D0A | #F2958C | 6.99 | 4.5 | pass | text/icon on solid wrong badge |
| onWrongContainer | wrongContainer | #FFD9D4 | #4A1A17 | 11.11 | 4.5 | pass | option card text, wrong state |
| onPendingContainer | pendingContainer | #EFE7D6 | #38332A | 10.19 | 4.5 | pass | option card text, selected/pending |
| onConfidence1Container | confidence1Container | #EFE7D6 | #2D2921 | 11.77 | 4.5 | pass | Əminlik 1 chip label |
| onConfidence2Container | confidence2Container | #EFE7D6 | #5A5244 | 6.26 | 4.5 | pass | Əminlik 2 chip label |
| onConfidence3Container | confidence3Container | #1E1B16 | #EFE7D6 | 13.95 | 4.5 | pass | Əminlik 3 chip label |
| onBannerInfoContainer | bannerInfoContainer | #D6E7F3 | #1F3B50 | 9.21 | 4.5 | pass | info banner text |
| onBannerWarningContainer | bannerWarningContainer | #F6E2B5 | #4A3812 | 8.83 | 4.5 | pass | offline/warning banner text |
| timerNormal | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | timer digits (normal) |
| timerWarning | surface | #F0A65E | #17150F | 8.97 | 4.5 | pass | timer digits (last 5 s) |
| timerNormal | timerTrack | #EFE7D6 | #3A342B | 10.01 | 3.0 | pass | timer arc vs track |
| timerWarning | timerTrack | #F0A65E | #3A342B | 6.05 | 3.0 | pass | timer arc vs track (last 5 s) |
| correct | surface | #8FCB9B | #17150F | 9.72 | 4.5 | pass | 'Düzgün cavab' label, check icon |
| correct | surfaceContainerLow | #8FCB9B | #1D1A14 | 9.24 | 4.5 | pass | review card: correct option label |
| wrong | surface | #F2958C | #17150F | 8.22 | 4.5 | pass | wrong label, cross icon |
| wrong | surfaceContainerLow | #F2958C | #1D1A14 | 7.81 | 4.5 | pass | review card: your answer label |
| correct | correctContainer | #8FCB9B | #1F3524 | 7.02 | 3.0 | pass | check icon / border on correct card |
| wrong | wrongContainer | #F2958C | #4A1A17 | 6.51 | 3.0 | pass | cross icon / border on wrong card |
| confidenceSelectedOutline | surface | #EC7B6F | #17150F | 6.63 | 3.0 | pass | selected Əminlik chip ring vs page |
| confidence3Container | surface | #EFE7D6 | #17150F | 14.84 | 3.0 | pass | Əminlik 3 chip boundary vs page |
| confidenceSelectedOutline | surfaceContainerHigh | #EC7B6F | #2D2921 | 5.26 | 3.0 | pass | duel: selected Əminlik chip ring on the confidence panel |
| confidence3Container | surfaceContainerHigh | #EFE7D6 | #2D2921 | 11.77 | 3.0 | pass | duel: Əminlik 3 chip boundary on the confidence panel |
| outline | surfaceContainerHighest | #8C8272 | #38332A | 3.31 | 3.0 | pass | duel: neutral reveal panel border (timeout / missed) |
| tertiary | surface | #DDA6D0 | #17150F | 9.07 | 3.0 | pass | duel: opponent avatar accent ring (intro, result) |
| tertiary | surfaceContainerLow | #DDA6D0 | #1D1A14 | 8.63 | 3.0 | pass | duel: opponent accent ring in the player panel |
| primary | surfaceContainerLow | #EC7B6F | #1D1A14 | 6.30 | 3.0 | pass | duel: own accent ring in the player panel, calibration bar |
| onSurfaceVariant | timerTrack | #B5AB97 | #3A342B | 5.41 | 3.0 | pass | duel: timer ring in the result phase / frozen after own answer |
| timerNormal | surfaceContainerLow | #EFE7D6 | #1D1A14 | 14.11 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #8FCB9B | #23201A | 8.65 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #F2958C | #23201A | 7.31 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #B5AB97 | #23201A | 7.14 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #8FCB9B | #1F3524 | 7.02 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #F2958C | #4A1A17 | 6.51 | 4.5 | pass | duel reveal: points text on a wrong player panel |
| onSurface | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | web: body text and headings |
| onSurfaceVariant | surface | #B5AB97 | #17150F | 8.03 | 4.5 | pass | web: meta line, nav links, footer |
| primary | surface | #EC7B6F | #17150F | 6.63 | 4.5 | pass | web: underlined links |
| onSurface | surfaceContainer | #EFE7D6 | #23201A | 13.20 | 4.5 | pass | web: nav link on hover |
| primary | surfaceContainerLow | #EC7B6F | #1D1A14 | 6.30 | 4.5 | pass | web: link card title, table-of-contents links |
| onSurfaceVariant | surfaceContainerLow | #B5AB97 | #1D1A14 | 7.63 | 4.5 | pass | web: link card supporting text |
| onPrimaryContainer | primaryContainer | #FFDAD3 | #5C1F18 | 9.75 | 4.5 | pass | web: current nav pill, callout |
| onBannerWarningContainer | bannerWarningContainer | #F6E2B5 | #4A3812 | 8.83 | 4.5 | pass | web: 'Mətn hazırlanır' placeholder box |
| onPrimary | primary | #2B0A06 | #EC7B6F | 6.64 | 4.5 | pass | web: skip link |
| primary | surface | #EC7B6F | #17150F | 6.63 | 3.0 | pass | web: 3px focus ring |
| onPrimary | primary | #2B0A06 | #EC7B6F | 6.64 | 4.5 | pass | primary pill button label (components.md §1) |
| onSurface | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | secondary pill button label (transparent face on the page) |
| outline | surface | #8C8272 | #17150F | 4.82 | 3.0 | pass | secondary pill button 1dp border |
| onError | error | #5C0D06 | #FFB4A9 | 8.15 | 4.5 | pass | destructive pill button label |
| onSurface | surfaceContainerLowest | #EFE7D6 | #110F0A | 15.57 | 4.5 | pass | text on the lightest container (text field) |
| primary | surfaceContainerLowest | #EC7B6F | #110F0A | 6.96 | 4.5 | pass | link on the lightest container |
| onPrimaryContainer | primaryContainer | #FFDAD3 | #5C1F18 | 9.75 | 4.5 | pass | default option letter badge (v2) / level-up strip |
| onSurface | pendingContainer | #EFE7D6 | #38332A | 10.19 | 3.0 | pass | selected option 2dp ink border vs its container |
| onPendingContainer | pendingContainer | #EFE7D6 | #38332A | 10.19 | 4.5 | pass | selected option text |
| outline | surface | #8C8272 | #17150F | 4.82 | 3.0 | pass | option card 1dp border vs page |
| onSurface | surfaceContainerHigh | #EFE7D6 | #2D2921 | 11.77 | 4.5 | pass | option letter badge (dimmed) / list row on panel |
| onConfidence1Container | confidence1Container | #EFE7D6 | #2D2921 | 11.77 | 3.0 | pass | Əminlik 1 signal bars |
| onConfidence2Container | confidence2Container | #EFE7D6 | #5A5244 | 6.26 | 3.0 | pass | Əminlik 2 signal bars |
| onConfidence3Container | confidence3Container | #1E1B16 | #EFE7D6 | 13.95 | 3.0 | pass | Əminlik 3 signal bars |
| onTertiaryContainer | tertiaryContainer | #F8D8EF | #57294F | 8.75 | 4.5 | pass | round-7 pill, opponent badge, Bot badge text + icon (components.md §32) |
| onSurface | surfaceContainer | #EFE7D6 | #23201A | 13.20 | 4.5 | pass | nav: selected label and icon on the bar |
| onSurfaceVariant | surfaceContainer | #B5AB97 | #23201A | 7.14 | 4.5 | pass | nav: unselected label and icon |
| onSurface | navIndicator | #EFE7D6 | #38332A | 10.19 | 4.5 | pass | nav: selected icon on the indicator pill |
| primary | surfaceContainer | #EC7B6F | #23201A | 5.90 | 3.0 | pass | nav: 'not played yet' badge dot on the Gündəlik item |
| onSurfaceVariant | surface | #B5AB97 | #17150F | 8.03 | 4.5 | pass | home: date line, overline 'TƏK OYNA · 10 SUAL' |
| streak | surface | #E8B85A | #17150F | 9.94 | 4.5 | pass | home top row: streak number and flame (saffron) |
| primary | surface | #EC7B6F | #17150F | 6.63 | 4.5 | pass | home: category numerals 01-05 (serif) |
| primary | surface | #EC7B6F | #17150F | 6.63 | 3.0 | pass | home: squiggle underline under the greeting word |
| onSurfaceVariant | surface | #B5AB97 | #17150F | 8.03 | 3.0 | pass | home: category line icons and chevrons |
| inverseOnSurface | inverseSurface | #1E1B16 | #EFE7D6 | 13.95 | 4.5 | pass | duel card: 'Duel' title and hint |
| inverseOnSurfaceVariant | inverseSurface | #5A5246 | #EFE7D6 | 6.25 | 4.5 | pass | duel card: '~3 dəq' and muted text |
| onSurface | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | duel card CTA pill (page colours on the inverted card) |
| surface | inverseSurface | #17150F | #EFE7D6 | 14.84 | 3.0 | pass | duel card CTA pill boundary vs the card |
| onPrimary | primary | #2B0A06 | #EC7B6F | 6.64 | 4.5 | pass | rating sticker text on the accent pill |
| outline | surface | #8C8272 | #17150F | 4.82 | 3.0 | pass | Gündəlik: dashed empty answer box |
| correct | surface | #8FCB9B | #17150F | 9.72 | 3.0 | pass | Gündəlik: filled correct answer box vs card |
| onCorrect | correct | #0F2B17 | #8FCB9B | 8.11 | 4.5 | pass | Gündəlik: check glyph in a correct box |
| wrong | surface | #F2958C | #17150F | 8.22 | 3.0 | pass | Gündəlik: filled wrong answer box vs card |
| onWrong | wrong | #4A0D0A | #F2958C | 6.99 | 4.5 | pass | Gündəlik: cross glyph in a wrong box |
| onSurfaceVariant | surfaceContainerHighest | #B5AB97 | #38332A | 5.51 | 4.5 | pass | Gündəlik: dash glyph in a 'no answer' box |
| onSurface | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | Gündəlik: '#12' serif numeral, streak title |
| streak | surface | #E8B85A | #17150F | 9.94 | 3.0 | pass | week dot: 1.5dp saffron ring (active) and dashed ring (today pending) |
| onStreakFill | streakFill | #1E1B16 | #E8B85A | 9.35 | 4.5 | pass | week dot: check glyph on the saffron fill |
| secondary | surface | #9EC3DD | #17150F | 9.82 | 3.0 | pass | week dot: freeze ring |
| onSecondaryContainer | secondaryContainer | #D6E7F3 | #1F3B50 | 9.21 | 4.5 | pass | week dot: shield glyph on the freeze fill; freeze message |
| outline | surface | #8C8272 | #17150F | 4.82 | 3.0 | pass | week dot: missed-day ring |
| onSurfaceVariant | surface | #B5AB97 | #17150F | 8.03 | 4.5 | pass | week row: day abbreviations 'B.e … B' |
| onSurface | surface | #EFE7D6 | #17150F | 14.84 | 4.5 | pass | week row: today's abbreviation (bold) |
| onStreakContainer | streakContainer | #F6E2B5 | #3F3014 | 10.01 | 4.5 | pass | streak hint message on the Gündəlik tab |
| streak | streakContainer | #E8B85A | #3F3014 | 6.95 | 3.0 | pass | flame icon in the streak hint message |
| onSurface | surfaceContainerHigh | #EFE7D6 | #2D2921 | 11.77 | 4.5 | pass | 'streak lost' message |
| timerNormal | surfaceContainerLow | #EFE7D6 | #1D1A14 | 14.11 | 4.5 | pass | duel: search countdown digits on the search card |
| correct | surfaceContainer | #8FCB9B | #23201A | 8.65 | 4.5 | pass | duel result / profile: positive rating change on the rating card |
| wrong | surfaceContainer | #F2958C | #23201A | 7.31 | 4.5 | pass | duel result / profile: negative rating change on the rating card |
| onSurfaceVariant | surfaceContainer | #B5AB97 | #23201A | 7.14 | 4.5 | pass | duel result / profile: zero rating change, captions on the rating card |
| correct | correctContainer | #8FCB9B | #1F3524 | 7.02 | 4.5 | pass | duel reveal: points text on a correct player panel |
| wrong | wrongContainer | #F2958C | #4A1A17 | 6.51 | 4.5 | pass | duel reveal: points text on a wrong player panel |
| primary | surfaceContainerHighest | #EC7B6F | #38332A | 4.55 | 3.0 | pass | XP bar fill vs track (result card, profile, category rows) |
| onSecondaryContainer | secondaryContainer | #D6E7F3 | #1F3B50 | 9.21 | 4.5 | pass | streak freeze row / freeze-used message |
| secondary | surface | #9EC3DD | #17150F | 9.82 | 3.0 | pass | streak freeze shield icon on a card |
| correct | surfaceContainerLowest | #8FCB9B | #110F0A | 10.20 | 3.0 | pass | daily result square (correct) vs share card |
| wrong | surfaceContainerLowest | #F2958C | #110F0A | 8.62 | 3.0 | pass | daily result square (wrong) vs share card |
| outline | surfaceContainerLowest | #8C8272 | #110F0A | 5.06 | 3.0 | pass | daily result square (no answer) border vs share card |
| onSurfaceVariant | surfaceContainerHighest | #B5AB97 | #38332A | 5.51 | 4.5 | pass | dash glyph in the no-answer square, notification card supporting text |
| onSurfaceVariant | surfaceContainerHigh | #B5AB97 | #2D2921 | 6.37 | 3.0 | pass | outlined flame (not yet today) |
| streak | surfaceContainerLowest | #E8B85A | #110F0A | 10.43 | 4.5 | pass | streak flame + number on the lightest card |
