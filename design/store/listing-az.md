# Google Play store listing — Azerbaijani (az)

Owner: `designer`. Text for Play Console → Grow → Store presence → Main store
listing, default language **Azerbaijani (az-AZ)**. Tone "sən" (OQ-041), no casino
words ("mərc", "stavka", "bet", "risk et", "qoy", "jeton", "uduş", "şans"), no
emoji, no ranking claims ("ən yaxşı", "№1"), no ALL CAPS words — all of these are
either our own copy rules or Play metadata policy. Character limits are Play's;
counts below include spaces and were checked by script.

## Open questions

- **OQ-D23 — app name.** Proposal: "Duello Quiz" (11 / 30). Alternative with a
  descriptive part: "Duello Quiz: Bilik Dueli" (24 / 30), better for search, but
  then the launcher name and the listing name differ. Decides: Sabit.
- **OQ-D24 — "Reklam yoxdur" line.** True for the MVP (ads and in-app purchases are
  out of scope). If either is added later, the line and the Data safety form must
  change in the same release. Keep it? Decides: Sabit.
- **OQ-D25 — Advertising ID.** `firebase-analytics` adds the
  `com.google.android.gms.permission.AD_ID` permission by default. Either declare
  "Device or other IDs → Advertising ID" in Data safety, or (proposal) remove the
  permission in the manifest and disable ad-ID collection
  (`google_analytics_adid_collection_enabled = false`), since there are no ads.
  Decides: Sabit, built by android-dev.
- **OQ-D20** (contact email) and **OQ-D22** (target age) from
  `design/screens/web-legal.md` also apply to the Play Console contact details and
  the "Target audience and content" section.

## App name (≤ 30)

```
Duello Quiz
```
11 characters.

## Short description (≤ 80)

```
Kateqoriya quizləri və canlı 1-ə 1 Əminlik dueli. Biliyini sına, reytinq qazan!
```
79 characters.

## Full description (≤ 4000)

Plain text (Play shows line breaks; no HTML needed). 1 806 characters.

```
Duello Quiz — Azərbaycan dilində bilik oyunu. Kateqoriya quizlərində özünü yoxla, canlı duellərdə başqa oyunçularla yarış və nə qədər əmin olduğunu sına.

Kateqoriya quizləri
• 5 kateqoriya: Coğrafiya, Tarix, Biologiya, Fizika və İdman.
• Hər quizdə 10 sual, hər suala 20 saniyə.
• Bəzi suallar şəkillidir.
• Sonda səhvlərinə bax: hər sualın düzgün cavabı və izahı var.

Əminlik dueli
Duel adi quiz deyil. Burada təkcə nə bildiyin yox, bildiyinə nə qədər əmin olduğun da xal gətirir.
• Rəqibin real oyunçudur və eyni sualı səninlə eyni anda görür.
• Oyun 7 raunddur. Əvvəlcə sualı oxuyursan, sonra variantları görmədən Əminliyini seçirsən: 1, 2 və ya 3.
• Düzgün cavab: +100, +200 və ya +300 xal. Səhv cavab: 0, −100 və ya −200 xal.
• Tez və düzgün cavab versən, sürət bonusu da qazanırsan.
• Əminlik 3-ü bir oyunda yalnız 2 dəfə seçə bilərsən. Onu ən çox bildiyin suala saxla.
• 7-ci raund həlledicidir: Əminlik xalları ikiqat olur.
• Hesab bərabərdirsə, əlavə sual qalibi müəyyən edir.
• Seçimlər və cavablar hər iki oyunçuya eyni anda göstərilir — heç kim rəqibin seçimini qabaqcadan bilmir.

Əminlik kartı və reytinq
• Hər duel reytinqini dəyişir: güclü rəqibə qalib gəlsən, daha çox qazanırsan.
• Oyundan sonra Əminlik kartı göstərir: əmin olduğun suallarda nə qədər düz tapırsan? Şübhə edəndə ehtiyatlı ol, bildiyində cəsarətli.
• Profilində son oyunların və bütün oyunlar üzrə Əminlik statistikası var.

Rahat və sadə
• Tam Azərbaycan dilində.
• İşıqlı və qaranlıq tema.
• Google hesabı və ya e-poçtla daxil ol.
• Reklam və tətbiqdaxili ödəniş yoxdur.

Məxfilik
Hesabını istənilən vaxt tətbiqdə silə bilərsən: Parametrlər → Hesabı sil. Bütün təfərrüatlar məxfilik siyasətindədir:
https://sabitsadiqli.github.io/Duello-Quiz/legal/privacy-policy.html

Duelə hazırsan? Biliyini sına, yeni şeylər öyrən!
```

Notes:
- Numbers match `docs/mvp-spec.md` (10 questions, 20 s, 7 rounds, Əminlik 3 twice,
  round 7 doubled, sudden death). If a game number changes, update this text.
- "−" is U+2212 as in the app.
- The privacy URL must be the same one entered in Play Console (OQ-D18).
- "Tez və düzgün cavab versən, sürət bonusu da qazanırsan" and "xal qazanırsan"
  use "qazanmaq" (to earn/win) as in the app copy; it is not casino wording.

## Graphics

| Asset | Spec | File |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit, from `design/brand/app-icon-full.svg` (`brand.md`) | export |
| Feature graphic | 1024 × 500 PNG/JPEG, no alpha; export from `feature-graphic.svg` | `design/store/feature-graphic.svg` |
| Phone screenshots | 6–8, plan and captions in `screenshots.md` | capture |

Feature graphic notes (`feature-graphic.svg`):
- Light colours only (Play shows it on both light and dark store themes; the
  indigo `primary` #4355B9 background works on both). Pairs checked in
  `contrast.py` ("store" group).
- Only text: the app name "Duello Quiz" (not translated). The text uses Nunito
  ExtraBold; **convert the text to outlines** (or export on a machine with Nunito
  installed) before rendering the PNG, otherwise a fallback font is used.
- Everything important sits inside the central 924 × 400 area, so crops and the
  rounded corners Play applies in some placements never cut the mark or the name.
- Export: `rsvg-convert -w 1024 -h 500 -b '#4355B9' feature-graphic.svg -o
  feature-graphic.png` (or Inkscape / Figma), then check it is exactly
  1024 × 500 and has no transparency.

## Data safety form — hints for Sabit

Not legal advice; the answers must match the privacy policy (`docs/legal/`) and
what the app actually does in the release build. "Collected" = sent off the
device; Firebase (Google) acts as our service provider, which Play does not count
as "sharing".

| Data type (Play category) | Collected | Shared | Optional? | Purpose | Source |
| --- | --- | --- | --- | --- | --- |
| Personal info → Email address | Yes | No | Required for an account | Account management, app functionality | Firebase Auth (email or Google) |
| Personal info → Name | Yes (display name "Ad") | No (shown to opponents as part of the game) | Required | App functionality, account management | Sign-up / Google profile |
| Personal info → User IDs | Yes (Firebase UID) | No | Required | Account management, app functionality | Firebase Auth, backend |
| Photos and videos | No (only the URL of the Google profile photo is stored; declare under "Other info" if in doubt) | — | — | — | — |
| App activity → App interactions | Yes (quiz answers, duel picks and answers, results, rating; Analytics events) | No | Required | App functionality, analytics | Backend, Firebase Analytics |
| App info and performance → Crash logs, Diagnostics | Only once Crashlytics is added | No | — | Analytics (stability) | Crashlytics |
| Device or other IDs | Yes (Firebase Analytics app instance ID; Advertising ID only if not removed, OQ-D25) | No | — | Analytics | Firebase Analytics |
| Location, contacts, messages, financial info, health, files, calendar, audio | No | — | — | — | — |

Security practices:
- Data encrypted in transit: **Yes** (HTTPS / WSS only).
- Users can request deletion: **Yes** — in the app (Parametrlər → Hesabı sil) and
  via `https://sabitsadiqli.github.io/Duello-Quiz/legal/account-deletion.html`.
- Committed to the Play Families Policy: depends on OQ-D22.
- Independent security review: No.

Other Play Console sections:
- Ads: **No ads**.
- App access: provide a test account (email + password from the project's test
  setup, not a personal one) so reviewers can pass sign-in; mention that the duel
  needs a second player (reviewers may see "Rəqib tapılmadı").
- Content rating questionnaire: quiz/trivia, no violence, no user-to-user chat;
  users see each other's display name and avatar only (answer "users can interact"
  accordingly). No gambling and no simulated gambling: Əminlik points are not
  money and cannot be bought.
- Category: Games → Trivia. Tags: Trivia, Quiz.
