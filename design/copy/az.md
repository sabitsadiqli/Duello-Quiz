# UI copy — Azerbaijani (az)

Owner: `designer`. Source of truth for every user-facing string of the MVP screens
(auth, home, category quiz, settings, duel, profile, daily challenge, progression,
notifications) and of the post-MVP mock exams ("Sınaqlar", `exam_`). `android-dev` copies the keys 1:1 into
`android/app/src/main/res/values/strings.xml`; nothing user-facing is hard-coded.
Terms follow `docs/requirements/glossary.md`; where this file decides final wording
that differs from a proposal in the requirements, this file wins (see "Changes
against the requirement proposals" at the end).

## Open questions

- **OQ-D16 — profile history cannot tell "left" from "missed rounds" yet.**
  `MatchSummary` in `docs/api/openapi.yaml` has `endReason` but no `forfeitCause`
  (only `match.result` has it). Proposal: backend-dev adds nullable
  `forfeitCause` (`missed_rounds` | `left`) to `MatchSummary`; until then the app
  uses the generic labels `profile_match_label_opponent_forfeit` /
  `profile_match_label_self_forfeit`. Decides: backend-dev / Sabit.
- **OQ-D17 — what does the server store after `internal_error` on `round.bet` /
  `round.answer`?** The copy below (`duel_confidence_send_failed`,
  `duel_answer_send_failed`) assumes **nothing** was stored and a new pick or answer
  may be sent before the deadline (like `invalid_bet` / `invalid_option`).
  `duel-protocol.md` does not say. Decides: backend-dev.

- **OQ-D31 — placeholder syntax of the backend i18n push texts** (`push_` keys):
  backend-dev decides; names `dailyNumber`, `streak` stay.

- **OQ-D36 — reminder "time" on the Gündəlik tab** (`daily-tab.md`): "Səhər" without a
  clock time (as decided for settings). Decided by Sabit 2026-11-17.

- **OQ-D49 — exam variant names** (`exam-catalogue.md`): the app builds "Sınaq №1"
  from `variantNumber`; the type title is server i18n. Needs U+2116 in the font
  subsets (tokens 3.1.0). **Decided by Sabit 2026-11-21: proposal accepted.**

## Tone: "sən"

The app addresses the player with **sən** (informal "you") everywhere, including
errors and account deletion.

Why:
- It is a game. Azerbaijani games, social apps and most consumer apps aimed at
  young adults speak with "sən"; "siz" reads like a bank or a government form.
- The duel is a peer-to-peer contest; a friendly, direct voice fits it.
- Shorter verb forms ("daxil ol" vs "daxil olun", "cəhd et" vs "cəhd edin") save
  2–3 characters per label, which matters on a 360dp screen with long Azerbaijani
  words.

Rules:
- Imperatives in 2nd person singular: "Daxil ol", "Yenidən cəhd et", "Yoxla".
- Possessives in 2nd person singular: "hesabın", "şifrəni", "nəticən".
- Never mock the player (0 / 10 gets encouragement, not jokes).
- The confidence mechanic is always "Əminlik". Never "mərc", "stavka", "bet",
  "risk et", "qoy", no coin/chip wording.
- "Quiz" is kept as a loanword (it is in the product name). Case endings are
  written without a hyphen: quiz, quizi, quizə, quizdən, quizin.
- Ellipsis is the single character "…" (U+2026), not three dots.
- Quotation marks: « » inside strings.
- Numbers: Azerbaijani nouns after a numeral stay singular ("10 sual"). Strings with
  a count are still marked `plurals` so a later `values-en` / `values-ru` can add
  real plural forms; in `az` both `one` and `other` get the same text.
- Placeholders use Android positional syntax: `%1$s` (text), `%1$d` (integer).

## Width check (360dp)

> v3 (2026-11-17): new strings are measured from the font files, see "v3 minimal"
> under Strings. The estimate below is the v2 method, kept for older strings.

Estimate method (Nunito since tokens 2.0.0 — re-checked: average glyph advance
≈ 0.55 em lowercase at 600, ≈ 0.59 em at 800, 0.68 em uppercase; button labels are
now 16sp ExtraBold, so `btn` budgets were re-estimated with 0.59 em × 16sp), available text width on a 360dp phone with
`screenGutter` 16dp. "Fit" lists the component budget; every single-line label was
estimated at font scale **1.0 and 1.3**.

| Budget tag | Component | Text width available | Lines |
| --- | --- | ---: | --- |
| `btn` | Full-width filled/outlined button, 24dp side padding | 280dp (254dp with icon) | 1 |
| `tbtn` | Text button standing alone in a row | 304dp | 1 |
| `dbtn` | Dialog action button (two side by side) | ~100dp each | 1 |
| `bar` | Top app bar title with navigation icon | 264dp | 1 |
| `label` | Text-field floating label (with trailing icon) | 248dp | 1 |
| `row` | List row / radio row title | 264dp | 2 |
| `cat` | Category card title | 200dp | 2 |
| `wrap` | Body text, banners, snackbars, dialogs — wraps freely | — | n |

Result: all labels fit at 1.0. At 1.3 the risky ones are listed in
"Truncation risks" below with the shorter alternative that is already used where
it matters.

## Strings

### app_ / common_

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `app_name` | Duello Quiz | Brand, not translated. Already in strings.xml. |
| `common_retry` | Yenidən cəhd et | `btn`/`tbtn`. 15 chars. |
| `common_cancel` | Ləğv et | `dbtn`. |
| `common_back` | Geri | `btn`/`tbtn`; also content description of the navigation-up icon. |
| `common_close` | Bağla | `dbtn`; content description of close icons. |
| `common_ok` | Oldu | `dbtn`. |
| `common_continue` | Davam et | `dbtn`. |
| `common_refresh` | Yenilə | `btn`. |
| `common_loading` | Yüklənir… | Content description of progress indicators. |
| `common_password_show_cd` | Şifrəni göstər | Content description, eye icon. |
| `common_password_hide_cd` | Şifrəni gizlət | Content description, eye-off icon. |
| `common_offline_banner` | İnternet yoxdur. Məlumatlar yenilənməyib. | `wrap`. Offline banner over cached data (HOME-05). |
| `common_deleted_player` | Silinmiş oyunçu | Shown for anonymized players (OQ-014): profile match history. |
| `common_home` | Əsas səhifə | `btn`/`tbtn`. Duel screens (same text as `quiz_result_home`, kept separate so the quiz key can change on its own). |

### error_ (shared errors, server error codes)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `error_offline` | İnternet bağlantısı yoxdur. Yenidən cəhd et. | `wrap`. Any network failure before a request reached the server. |
| `error_server_unreachable` | Serverə qoşulmaq mümkün olmadı. Bir az sonra yenidən cəhd et. | `wrap`. Device has validated internet but the server cannot be reached (DNS failure, connection refused, no route, connect timeout). Without internet `error_offline` is used. |
| `error_generic` | Xəta baş verdi. Yenidən cəhd et. | `wrap`. 5xx, timeout (15 s), unknown codes. |
| `error_sign_in_expired` | Giriş müddəti bitib. Yenidən daxil ol. | `wrap`. HTTP 401 after one refresh+retry (AUTH-09), snackbar on welcome. |
| `error_category_not_playable` | Bu kateqoriya hazırda əlçatan deyil. | `wrap`. Server codes `category_not_playable` (409) and `category_not_found` (404). |
| `error_rate_limited` | Qısa müddətdə çox quiz başlatdın. Bir az sonra yenidən cəhd et. | `wrap`. Server code `rate_limited` (429, OQ-022) when `Retry-After` is missing, unreadable or more than 60 minutes. |
| `error_rate_limited_retry` | Qısa müddətdə çox quiz başlatdın. %1$d dəqiqə sonra yenidən cəhd et. | `plurals` (one/other same text). `wrap`. 429 with `Retry-After` (seconds): minutes = ceil(seconds / 60), at least 1, at most 60. Used where `error_rate_limited` was used (home snackbar, quiz loading screen, quiz result). The text is not live-updated. |
| `error_session_expired` | Quizin vaxtı bitdi. | `wrap`. Server code `session_expired` (409); client then opens the result screen. |
| `error_session_closed` | Bu quiz artıq bitib. | `wrap`. Server code `session_closed` (409). |
| `error_already_answered` | Bu suala artıq cavab vermisən. | `wrap`. Server code `already_answered` (409). Normally not shown: the client keeps the stored result. Log only, show if it ever surfaces. |
| `error_quiz_invalid` | Quizdə xəta baş verdi. Yenidən başla. | `wrap`. Server codes `question_not_in_session`, `invalid_option` (400) — client bugs; also logged to Crashlytics. |
| `error_upgrade_required` | Duel üçün tətbiqi yenilə. | `wrap`. Duel protocol code `upgrade_required` (OQ-029). Normally the full screen in `duel-availability.md` is shown; this text is the snackbar fallback. |
| `error_duel_unavailable` | Duel hazırda əlçatan deyil. Bir az sonra yenidən cəhd et. | `wrap`. Code `duel_unavailable` (fewer than 10 active questions). Snackbar fallback of the full screen. |
| `error_duel_connect_failed` | Duelə qoşulmaq alınmadı. | `wrap`. Handshake 5xx / 15 s timeout (DUEL-01). Action `common_retry`. |
| `error_session_replaced` | Bu hesabla başqa cihazda duelə qoşuldun. | `wrap`. Code `session_replaced` (DUEL-04): this device lost the queue entry or match to a newer connection. |
| `error_match_not_found` | Oyun texniki səbəbdən dayandırıldı. Reytinq dəyişmədi. | `wrap`. Code `match_not_found` on rejoin (server lost the match, OQ-033). |
| `error_already_in_match` | Artıq oyundasan. Oyuna qayıdırsan… | `wrap`. Code `already_in_match`: the client rejoins at once; shown only as the loading caption while the snapshot arrives. |
| `error_confidence_limit_reached` | Əminlik 3 limiti bitib. 1 və ya 2 seç. | `wrap`. Code `bet_limit_reached` (key renamed: keys never say "bet"). Only reachable if the client and server disagree; status line of the confidence panel. |
| `error_duel_internal` | Serverdə xəta baş verdi. Yenidən cəhd et. | `wrap`. Duel code `internal_error` in answer to `queue.join` (search screen: error state with `duel_search_retry`), `match.rejoin` (state view with `common_retry` + `common_home`) or `match.leave` (snackbar on home; the leave is retried on the next connection like OQ-058). Round messages use `duel_confidence_send_failed` / `duel_answer_send_failed`. |
| `error_display_name_not_allowed` | Bu ad istifadə edilə bilməz. | Field error. Code `display_name_not_allowed` (OQ-030) on sign-up and profile edit. |
| `error_invalid_display_name` | Bu ad uyğun deyil. Ad 2–20 simvol olmalı, yalnız hərf, rəqəm, boşluq, «-», «'» və «.» ola bilər. | Field error. Code `invalid_display_name` (400) when the server rejects a name the client let through. |

Duel protocol codes **without a UI string** (log only, never shown; if one ever
surfaces where the user waits for something, fall back to `error_generic`):
`bet_already_locked`, `invalid_bet`, `invalid_option`, `phase_not_open`,
`stale_round`, `bad_message`; `already_answered` (log; the client keeps its stored
state). `too_late` is shown through the phase it belongs to:
`duel_confidence_too_late` (confidence phase) or `duel_answer_too_late` (answer
phase).

Every duel error code and close code → what the user sees (`docs/api/duel-protocol.md`
§1.2, §6):

| Code | Reaches the user? | Copy |
| --- | --- | --- |
| HTTP 401 `unauthorized` (handshake, after one token refresh) | Yes | AUTH-09: sign-in screen + `error_sign_in_expired` |
| Handshake 5xx / 15 s timeout | Yes | `duel_connect_failed_title` / `duel_connect_failed_body` (+ `error_duel_connect_failed` snackbar fallback) |
| `upgrade_required` / close `4000` | Yes | `duel_upgrade_*` screen (`error_upgrade_required` fallback) |
| `session_replaced` / close `4001` | Yes | `error_session_replaced` (state view F, `duel-interruptions.md`) |
| `duel_unavailable` | Yes | `duel_unavailable_*` screen (`error_duel_unavailable` fallback) |
| `already_in_match` | Yes, briefly | `error_already_in_match` as the loading caption, then the match (OQ-073) |
| `bet_limit_reached` | Rarely | `error_confidence_limit_reached` |
| `too_late` | Yes | `duel_confidence_too_late` / `duel_answer_too_late` |
| `match_not_found` | Yes (rejoin) | `error_match_not_found`; after `match.leave` nothing extra (the user is already home) |
| `internal_error` | Yes | `duel_confidence_send_failed` (`ref = round.bet`), `duel_answer_send_failed` (`ref = round.answer`), else `error_duel_internal` |
| close `4002` / `4003` | Indirectly | Reconnect overlay `duel_reconnecting_*` (`duel-interruptions.md`) |
| `bet_already_locked`, `invalid_bet`, `invalid_option`, `already_answered`, `phase_not_open`, `stale_round`, `bad_message` | No | Log only |

### auth_ (welcome, sign-in, sign-up, reset, verification)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `auth_welcome_tagline` | Biliyini sına, yeni şeylər öyrən. | `wrap`, max 2 lines. Under the logo. |
| `auth_google_sign_in` | Google ilə daxil ol | `btn` with Google icon. 19 chars. |
| `auth_email_sign_in` | E-poçtla daxil ol | `btn`. |
| `auth_sign_up_cta` | Qeydiyyatdan keç | `btn`/`tbtn`. Welcome and sign-up submit. |
| `auth_privacy_link` | Məxfilik siyasəti | `tbtn`. Footer of welcome. |
| `auth_account_deleted` | Hesabın silindi. | Snackbar on welcome after SET-05. |
| `auth_sign_in_title` | Daxil ol | Screen headline (headlineSmall). |
| `auth_email_label` | E-poçt | `label`. |
| `auth_password_label` | Şifrə | `label`. |
| `auth_sign_in_button` | Daxil ol | `btn`. |
| `auth_forgot_password` | Şifrəni unutmusan? | `tbtn`. |
| `auth_no_account_prompt` | Hesabın yoxdur? | Body text before the link. |
| `auth_sign_up_link` | Qeydiyyatdan keç | `tbtn` after the prompt (prompt + link may wrap to 2 lines). |
| `auth_error_email_required` | E-poçt ünvanını yaz. | Field error. |
| `auth_error_email_invalid` | E-poçt ünvanı düzgün deyil. | Field error. |
| `auth_error_password_required` | Şifrəni yaz. | Field error. |
| `auth_error_wrong_credentials` | E-poçt və ya şifrə yanlışdır. | Form error; same for unknown email (no enumeration). |
| `auth_error_user_disabled` | Bu hesab deaktiv edilib. | Form error. |
| `auth_error_too_many_attempts` | Çox sayda uğursuz cəhd. Bir az sonra yenidən cəhd et. | Form error, sign-in. |
| `auth_error_too_many_requests` | Çox sayda cəhd edildi. Bir az sonra yenidən cəhd et. | Form error, sign-up / reset / resend. |
| `auth_sign_up_title` | Qeydiyyat | Screen headline. |
| `auth_name_label` | Ad | `label`. |
| `auth_name_supporting` | Bu ad digər oyunçulara görünəcək. | Supporting text under the field. |
| `auth_name_counter` | %1$d/%2$d | Counter, e.g. "7/20". `%2$d` = 20. |
| `auth_error_name_required` | Adını yaz. | Field error. |
| `auth_error_name_length` | Ad %1$d–%2$d simvol olmalıdır. | Field error; 2 and 20 (OQ-011). Also used by the profile name dialog. |
| `auth_error_name_characters` | Adda yalnız hərf, rəqəm, boşluq, «-», «'» və «.» ola bilər. | Field error (OQ-042: emoji, symbols, control characters). Approved as proposed by android-dev. In `strings.xml` escape the apostrophe: `«\'»`. Also used by the profile name dialog. |
| `auth_password_supporting` | Ən azı %1$d simvol. | Supporting text; 8 (OQ-018). |
| `auth_error_password_short` | Şifrə ən azı %1$d simvol olmalıdır. | Field error; 8. |
| `auth_error_email_in_use` | Bu e-poçtla artıq hesab var. Daxil ol və ya şifrəni bərpa et. | Form error + `auth_go_to_sign_in` action. |
| `auth_go_to_sign_in` | Daxil ol | `tbtn` inside the email-in-use error. |
| `auth_have_account_prompt` | Artıq hesabın var? | Body text before `auth_sign_in_link`. |
| `auth_sign_in_link` | Daxil ol | `tbtn`. |
| `auth_reset_title` | Şifrəni bərpa et | Screen headline (content, not app bar). |
| `auth_reset_body` | E-poçt ünvanını yaz, şifrəni bərpa etmək üçün sənə link göndərək. | `wrap`. |
| `auth_reset_send` | Link göndər | `btn`. |
| `auth_reset_send_countdown` | Yenidən göndər (%1$d san.) | `btn`, disabled, 60 → 1. 25 chars max. |
| `auth_reset_sent` | Əgər bu e-poçtla hesab varsa, şifrəni bərpa etmək üçün link göndərdik. Poçt qutunu, həmçinin spam qovluğunu yoxla. | `wrap`. Same text whether or not the account exists. |
| `auth_reset_back_to_sign_in` | Daxil olmağa qayıt | `tbtn`. |
| `auth_error_google_no_account` | Bu cihazda Google hesabı tapılmadı. | Snackbar. |
| `auth_error_google_unavailable` | Google ilə daxil olmaq mümkün olmadı. E-poçtla daxil ol. | Snackbar (Play services missing/outdated). |
| `auth_error_google_failed` | Google ilə daxil olmaq alınmadı. Yenidən cəhd et. | Snackbar. |
| `auth_verify_banner_title` | E-poçtunu təsdiqlə | Banner title (titleSmall), 1 line. |
| `auth_verify_banner_body` | Təsdiq linkini %1$s ünvanına göndərdik. | `wrap`, max 3 lines. `%1$s` = the account email, so a typo is visible. |
| `auth_verify_resend` | Yenidən göndər | Banner `tbtn`. |
| `auth_verify_resend_countdown` | Yenidən göndər (%1$d san.) | Banner `tbtn`, disabled. |
| `auth_verify_sent` | Təsdiq linki göndərildi. | Snackbar. |
| `auth_verify_done` | E-poçtun təsdiqləndi. | Snackbar when the banner disappears after verification. |
| `auth_verify_dismiss_cd` | Bildirişi bağla | Content description of the banner close icon. |

### home_

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `home_rating` | Reytinq: %1$d | v3: profile header only (home shows `home_duel_sticker_rating`). |
| `home_profile_cd` | %1$s, reytinq %2$d | **v3 removed** (no home header). |
| `home_settings_cd` | — | **v3 deleted** (2026-11-18, after `docs/legal/account-deletion.html` moved to `nav_profile` + `profile_settings_cd`). |
| `home_duel_title` | Duel | Duel card title. Hidden until the duel ships (OQ-020). |
| `home_duel_hint` | %1$d sual · nə qədər əminsən? | **v3 changed** (was "Əminliyini sına, reytinq qazan"). `plurals`; value 7. Duel card hint, bodyMedium, `wrap`. U+00B7 middle dot. 172 / 224dp (264). |
| `home_duel_cta` | Rəqib tap | **v3 changed** (was "Duelə başla"). CTA pill on the duel card with a trailing `arrow_forward` icon (the arrow is an icon, not text). 71 / 92dp at 1.0 / 1.3. `profile_empty_cta` keeps "Duelə başla". |
| `home_categories_title` | Kateqoriyalar | **v3 removed** (replaced by `home_categories_overline`). |
| `home_category_question_count` | %1$d sual | `plurals`. Category card supporting text; value 10. |
| `home_category_loading_cd` | %1$s quizi açılır | Content description while the start request runs. |
| `home_categories_empty` | Hələ ki kateqoriya yoxdur. Bir az sonra yenidən yoxla. | Empty state body, `wrap`. Action: `common_refresh`. |
| `home_categories_error` | Kateqoriyaları yükləmək alınmadı. | Error state title, `wrap`. Action: `common_retry`. |
| `home_offline_title` | İnternet bağlantısı yoxdur. | Full-screen offline title (no cache). |
| `home_offline_body` | Bağlantını yoxla və yenidən cəhd et. | Full-screen offline body. Action: `common_retry`. |
| `home_quiz_needs_internet` | Quiz üçün internet lazımdır. | Snackbar. |
| `home_duel_needs_internet` | Duel üçün internet lazımdır. | Snackbar (duel entry, later). |

### quiz_ (loading, question, reveal, exit, result, review)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `quiz_loading` | Suallar hazırlanır… | Loading screen text. |
| `quiz_start_error` | Quizi başlatmaq alınmadı. | Error title on loading screen. Actions: `common_retry`, `common_back`. |
| `quiz_progress` | %1$d / %2$d | Progress label "3 / 10", tabular figures. |
| `quiz_progress_cd` | Sual %1$d, cəmi %2$d | Content description of progress. |
| `quiz_timer_cd` | %1$d saniyə qalıb | Content description of the timer; announced at 10 s and 5 s only (not every second). |
| `quiz_close_cd` | Quizdən çıx | Content description of the close icon. |
| `quiz_image_cd_fallback` | Sualın şəkli | Used when the question image has no `alt`. |
| `quiz_image_failed` | Şəkil yüklənmədi | Placeholder caption when the image fails. |
| `quiz_option_1_cd` | 1-ci variant: %1$s | TalkBack label; ordinal suffix follows vowel harmony, so 4 keys. |
| `quiz_option_2_cd` | 2-ci variant: %1$s | |
| `quiz_option_3_cd` | 3-cü variant: %1$s | |
| `quiz_option_4_cd` | 4-cü variant: %1$s | |
| `quiz_option_state_checking_cd` | seçildi, yoxlanılır | Appended state description. |
| `quiz_option_state_correct_cd` | düzgün cavab | Appended state description. |
| `quiz_option_state_wrong_cd` | sənin cavabın, səhv | Appended state description. |
| `quiz_feedback_correct` | Düzgün! | Reveal label, titleMedium. |
| `quiz_feedback_wrong` | Səhv | Reveal label. |
| `quiz_option_outcome_correct` | Düzgün | Outcome word in the trailing slot of the correct option card (v2 colour-blind cue, `components.md` §3), labelMedium. Not read by TalkBack (the state description already says it). |
| `quiz_option_outcome_wrong` | Səhv | Outcome word on the user's wrong option card, labelMedium. Not read by TalkBack. |
| `quiz_timeout` | Vaxt bitdi | Reveal label after timeout. |
| `quiz_answer_saved` | Cavab yadda saxlanıldı | Reveal label when the answer is queued offline (no result yet). |
| `quiz_reconnecting` | Bağlantı bərpa olunur… | Inline status while the answer request is retried. |
| `quiz_offline_banner` | İnternet yoxdur. Cavablar göndəriləcək. | Banner after 10 s without connection (QUIZ-08). |
| `quiz_exit_title` | Quizdən çıxmaq istəyirsən? | Dialog title, `wrap`. |
| `quiz_exit_body` | Bu quiz yarımçıq qalacaq. Vaxt bu arada dayanmır. | Dialog body. |
| `quiz_exit_confirm` | Çıx | `dbtn`. |
| `quiz_exit_dismiss` | Davam et | `dbtn`. |
| `quiz_result_title` | Nəticə | Screen headline. |
| `quiz_result_correct_count` | %1$d / %2$d düzgün | Score line, e.g. "7 / 10 düzgün" (OQ-012). |
| `quiz_result_score_suffix` | " / %1$d" | Shown after the big score number ("7" + " / 10"). Leading space; in `strings.xml` write `\u0020/ %1$d`. |
| `quiz_result_score_cd` | %2$d sualdan %1$d düzgün cavab | Content description of the score block. |
| `quiz_result_message_perfect` | Bütün suallara düzgün cavab verdin! | 10 / 10. |
| `quiz_result_message_great` | Çox yaxşı nəticə! | 7–9. |
| `quiz_result_message_good` | Pis deyil! Səhvlərə bax, növbəti dəfə daha yaxşı olacaq. | 4–6. |
| `quiz_result_message_low` | Başlanğıc üçün yaxşıdır. Səhvlərdən öyrən! | 1–3. |
| `quiz_result_message_zero` | Bu dəfə alınmadı, amma hər sual yeni bilikdir. Yenidən cəhd et! | 0 / 10, encouraging. |
| `quiz_result_expired_note` | Vaxt bitdi. Cavab verilməmiş suallar düzgün sayılmadı. | Shown when the session deadline passed. |
| `quiz_result_not_synced` | Nəticə serverə göndərilə bilmədi. İnternet qayıdanda yenidən göndərəcəyik. | Info banner when `finish` failed. |
| `quiz_result_play_again` | Yenidən oyna | `btn`. |
| `quiz_result_home` | Əsas səhifə | `btn` (neutral). |
| `quiz_result_review_entry` | Səhvlərin təhlili | `row` title of the entry card. |
| `quiz_result_review_entry_count` | %1$d sual | `plurals`. Supporting text of the entry card. |
| `quiz_review_title` | Səhvlər | `bar` title of the review screen (short form, see risks). |
| `quiz_review_question_number` | Sual %1$d | Card overline. |
| `quiz_review_your_answer` | Sənin cavabın | Label above the user's option. |
| `quiz_review_correct_answer` | Düzgün cavab | Label above the correct option. |
| `quiz_review_not_answered` | Cavab verilməyib | Replaces the user's option on timeout/unsent. |
| `quiz_review_explanation` | İzah | Label above the explanation. |

### settings_

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `settings_title` | Parametrlər | `bar`. |
| `settings_section_theme` | TEMA | **v3**: overline, authored in capitals (was "Tema"). |
| `settings_theme_system` | Sistem | `row` radio. |
| `settings_theme_system_supporting` | Telefonun ayarına uyğun | Supporting text under "Sistem". |
| `settings_theme_light` | İşıqlı | `row` radio. |
| `settings_theme_dark` | Qaranlıq | `row` radio. |
| `settings_section_language` | DİL | **v3**: overline, authored in capitals (was "Dil"). |
| `settings_language_value` | Azərbaycan dili | `row`, not interactive in MVP. |
| `settings_section_account` | HESAB | **v3**: overline, authored in capitals (was "Hesab"). |
| `settings_signed_in_as` | %1$s | Supporting line with the account email (or Google name). 1 line, ellipsis at the end. |
| `settings_sign_out` | Hesabdan çıx | `row`. |
| `settings_sign_out_title` | Hesabdan çıxmaq istəyirsən? | Dialog title. |
| `settings_sign_out_body` | Nəticələrin və reytinqin hesabında qalır. İstədiyin vaxt yenidən daxil ola bilərsən. | Dialog body. |
| `settings_sign_out_confirm` | Çıx | `dbtn`. |
| `settings_delete_account` | Hesabı sil | `row`, error colour. |
| `settings_section_about` | HAQQINDA | **v3**: overline, authored in capitals (was "Haqqında"). |
| `settings_version` | Versiya %1$s (%2$d) | `row`, e.g. "Versiya 1.0.0 (12)". |
| `settings_privacy_policy` | Məxfilik siyasəti | `row`, opens Custom Tab. |
| `settings_licenses` | Açıq mənbə lisenziyaları | `row` (fits at 1.3, 24 chars). |
| `settings_licenses_title` | Lisenziyalar | `bar` title of the licences screen (short form). |
| `settings_licenses_intro` | Duello Quiz açıq mənbə layihələrdən istifadə edir. Onların müəlliflərinə təşəkkür edirik! | `wrap`. Intro on the licences list (`licenses.md`). |
| `settings_licenses_section_assets` | ŞRİFTLƏR VƏ İKONLAR | **v3**: overline (§35a), authored in capitals (was "Şriftlər və ikonlar"). Fraunces, Plus Jakarta Sans, Material Symbols. ~175 / 230dp. |
| `settings_licenses_section_libraries` | KİTABXANALAR | **v3**: overline (§35a), authored in capitals (was "Kitabxanalar"). ~110 / 145dp. |
| `settings_licenses_item_supporting` | %1$s · %2$s | Row supporting text: `settings_licenses_version` · licence name(s). |
| `settings_licenses_version` | Versiya %1$s | Version of a library ("Versiya 3.6.0"). |
| `settings_licenses_unknown` | Lisenziya göstərilməyib | Row supporting text when the metadata has no licence. |
| `settings_licenses_item_cd` | %1$s, versiya %2$s, lisenziya %3$s | Merged row for TalkBack. |
| `settings_licenses_open_website` | Veb səhifə | `tbtn` with `open_in_new`, detail screen. |
| `settings_licenses_open_license` | Lisenziya mətni | `tbtn`, opens the licence URL. |
| `settings_licenses_text_missing` | Bu kitabxananın lisenziya mətni tətbiqə daxil edilməyib. Mətni linkdən oxuya bilərsən. | `wrap`. Detail, when the metadata has no text. |
| `settings_licenses_error` | Kitabxanaların siyahısını göstərmək alınmadı. | `wrap`. Inline, list screen. |
| `settings_link_open_failed` | Səhifəni açmaq mümkün olmadı. | Snackbar if no browser can open the link. |
| `settings_delete_title` | Hesabı sil | Delete screen headline. |
| `settings_delete_body` | Profilin, reytinqin və quiz nəticələrin həmişəlik silinəcək. Bu əməliyyatı geri qaytarmaq olmaz. | `wrap`. |
| `settings_delete_body_matches` | Keçmiş duellərdə rəqiblərin səni «Silinmiş oyunçu» kimi görəcək. | `wrap`. OQ-014. |
| `settings_delete_body_backups` | Ehtiyat nüsxələrdən məlumatlar 30 gün ərzində silinir. | `wrap`. OQ-014. |
| `settings_delete_confirm_button` | Hesabı həmişəlik sil | `btn`, error colour. 20 chars. |
| `settings_delete_dialog_title` | Hesab həmişəlik silinsin? | Second confirmation dialog title. |
| `settings_delete_dialog_body` | Bu əməliyyatı geri qaytarmaq olmaz. | Dialog body. |
| `settings_delete_dialog_confirm` | Sil | `dbtn`, error colour. |
| `settings_delete_in_progress` | Hesab silinir… | Progress text on the delete screen. |
| `settings_delete_offline` | Hesabı silmək üçün internet lazımdır. | Snackbar / inline error. |
| `settings_delete_failed` | Hesabı silmək alınmadı. Yenidən cəhd et. | Inline error. |

### duel_ (search, intro, match, reveal, interruptions, result, availability)

Never "bet", "mərc", "stavka", "risk et", "qoy", "jeton" or any casino wording. Signed numbers use U+2212 "−" for minus,
never "-" (format helper in code; the strings below take the absolute value).

#### Shared numbers and labels

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_you` | Sən | Self label in the player panel and intro. |
| `duel_delta_positive` | +%1$d | Points, stakes, bonus and rating change. Tabular figures. |
| `duel_delta_negative` | −%1$d | U+2212. `%1$d` is the absolute value. |
| `duel_delta_zero` | 0 | |
| `duel_score_pair` | %1$s : %2$s | "640 : 410", own score first; `%s` because of the U+2212 helper. |

#### Search / queue (`duel-search.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_search_close_cd` | Axtarışı ləğv et | Close icon. |
| `duel_search_connecting` | Qoşulur… | Before `queue.joined` arrives (> 1 s). |
| `duel_search_title` | Rəqib axtarılır… | headlineSmall, wraps. Also the overtime text (countdown hidden). |
| `duel_search_countdown` | %1$d san. | headlineMedium, tabular. "24 san." |
| `duel_search_countdown_cd` | Axtarışın bitməsinə %1$d saniyə qalıb | Announced at most every 10 s (DUEL-02). |
| `duel_search_tip_label` | Bilirsən? | Overline of the tip card, labelMedium. |
| `duel_search_tip_1` | Variantları görməzdən əvvəl Əminliyini seç: 1, 2 və ya 3. | Tip card, `wrap`. Rotates every 10 s. |
| `duel_search_tip_2` | Əminlik 3 ən çox xal gətirir, səhv olsa isə xal itirirsən. Onu bir oyunda 2 dəfə seçə bilərsən. | |
| `duel_search_tip_3` | 7-ci raund həlledicidir: Əminlik xalları ikiqat olur. | |
| `duel_search_cancel` | Ləğv et | `btn` (neutral). |
| `duel_search_timeout_title` | Rəqib tapılmadı | State title. |
| `duel_search_timeout_body` | Hazırda başqa oyunçu axtarmır. Bir az sonra yenidən cəhd et. | `wrap`. |
| `duel_search_retry` | Yenidən axtar | `btn` (primary). 13 chars. |
| `duel_search_stopped_title` | Axtarış dayandırıldı | After the app went to the background while searching (DUEL-03). |
| `duel_search_stopped_body` | Tətbiqdən çıxdığın üçün axtarışı dayandırdıq. | `wrap`. |
| `duel_search_disconnected_title` | Bağlantı kəsildi | Socket dropped while searching. |
| `duel_search_disconnected_body` | İnternet bağlantını yoxla və yenidən axtar. | `wrap`. |
| `duel_search_error_title` | Axtarışı başlatmaq alınmadı | State title when `queue.join` gets `internal_error`; body `error_duel_internal`, action `duel_search_retry`. |
| `duel_search_opponent_left` | Rəqib ayrıldı. Axtarış davam edir. | Snackbar when `match.cancelled` arrives during the intro (OQ-037); the search restarts with a new deadline. |

#### Match intro (`duel-intro.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_intro_title` | Rəqib tapıldı! | headlineMedium, wraps. |
| `duel_intro_starting` | Oyun başlayır | labelLarge above the 3-2-1 digits. |
| `duel_intro_rules` | 7 raund. Əminlik 3-ü bir oyunda 2 dəfə seçə bilərsən. | bodyMedium, `wrap`. |
| `duel_intro_cd` | Rəqib tapıldı: %1$s, reytinq %2$d. Oyun başlayır. | Announced once (assertive). |

#### Match frame and phases (`duel-match.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_leave_cd` | Oyundan çıx | Close icon in the duel top bar. |
| `duel_round_label` | Raund %1$d / %2$d | Top bar, titleMedium, tabular. |
| `duel_round_label_cd` | Raund %1$d, cəmi %2$d | |
| `duel_round_label_sudden_death` | Əlavə sual %1$d / %2$d | Top bar in sudden death. 16 chars. |
| `duel_round_label_sudden_death_cd` | Əlavə sual %1$d, cəmi %2$d | |
| `duel_all_in_pill` | ×2 | Round 7 pill next to the round label (U+00D7). |
| `duel_all_in_pill_cd` | Həlledici raund, xallar ikiqat | |
| `duel_phase_question` | Sual | Phase name under the round label in the duel top bar, labelMedium. |
| `duel_phase_confidence` | Əminlik | Phase name (top bar). |
| `duel_phase_answer` | Cavab | Phase name (top bar). |
| `duel_phase_result` | Nəticə | Phase name (top bar). |
| `duel_phase_cd` | %1$s mərhələsi | Announced on phase change: "Əminlik mərhələsi". |
| `duel_player_score_cd` | %1$s: %2$s xal | "Sən: 340 xal". |
| `duel_status_choosing` | Seçir… | Opponent pill, confidence phase. |
| `duel_status_answering` | Düşünür… | Opponent pill, answer phase. |
| `duel_status_sending` | Göndərilir… | Own pill / status line while waiting for the server ack. |
| `duel_status_locked_opponent` | Seçdi | Opponent locked Əminlik (no value, OQ-024). |
| `duel_status_locked_self` | Əminlik %1$d | Own panel after `round.betLocked {self}`. |
| `duel_status_answered_opponent` | Cavabladı | Opponent answered (no option). |
| `duel_status_answered_self` | Cavabladın | Own panel after the answer was sent. |
| `duel_status_disconnected` | Bağlantı kəsildi | Opponent pill; wraps to 2 lines at 1.3 (allowed). |
| `duel_status_returned` | Qayıtdı | Opponent pill for 2 s after reconnect. |
| `duel_question_hint` | Variantlar hələ gizlidir. Nə qədər əmin olduğunu düşün. | Question phase, under the question, `wrap`. |
| `duel_question_hint_sudden_death` | Əminlik 1-dir. Ən tez düzgün cavab verən qalib gəlir. | Sudden-death question phase. |
| `duel_confidence_title` | Nə qədər əminsən? | Confidence panel title, titleLarge. |
| `duel_confidence_chip_1_cd` | Əminlik 1. Düzgün olsa %1$d xal qazanırsan, səhv olsa xal itirmirsən. | Chip 1 TalkBack label (100, round 7: 200). |
| `duel_confidence_chip_cd` | Əminlik %1$d. Düzgün olsa %2$d xal qazanırsan, səhv olsa %3$d xal itirirsən. | Chips 2 and 3. |
| `duel_confidence_selected_cd` | seçildi | State description of the chosen chip. |
| `duel_confidence_three_left` | Əminlik 3: %1$d dəfə qalıb | `plurals` (one/other same text). Caption under the chips; values 2, 1. |
| `duel_confidence_three_none` | Əminlik 3 limiti bitdi | Caption when 0 left; also TalkBack label of the disabled chip. |
| `duel_confidence_locked` | Seçimin qeydə alındı | Status line after `round.betLocked {self}`. |
| `duel_confidence_waiting_opponent` | Rəqib hələ seçir… | Status line after own lock while the opponent has not locked. |
| `duel_confidence_defaulted` | Vaxt bitdi — Əminlik 1 | Status line when the server default applied. |
| `duel_confidence_too_late` | Seçimin gec çatdı — Əminlik 1 sayıldı. | Code `too_late` in the confidence phase (OQ-036). |
| `duel_confidence_send_failed` | Seçimin qeydə alınmadı. Vaxt varsa, yenidən seç. | `internal_error` with `ref = round.bet`; chips enabled again while time is left (OQ-D17). |
| `duel_answer_your_confidence` | Əminliyin: %1$d | Content description of the own mini Əminlik badge in the player panel (answer phase). |
| `duel_answer_sent` | Cavabın göndərildi. Nəticə sənə və rəqibə eyni anda göstəriləcək. | Status line after the own answer, `wrap` (2 lines). "sən" tone: no plural "hər ikiniz". |
| `duel_answer_too_late` | Cavabın gec çatdı — cavabsız sayıldı. | Code `too_late` in the answer phase. |
| `duel_answer_send_failed` | Cavabın qeydə alınmadı. Vaxt varsa, yenidən seç. | `internal_error` with `ref = round.answer`; options enabled again while time is left, timer ring unfrozen (OQ-D17). |
| `duel_option_picked_by_you_cd` | sən seçdin | Option state description in the result phase. |
| `duel_option_picked_by_opponent_cd` | rəqib seçdi | Option state description in the result phase. |

#### Result phase / reveal (`duel-reveal.md`, `duel-special-rounds.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_reveal_correct` | Düzgün | Reveal face outcome word. |
| `duel_reveal_wrong` | Səhv | Reveal face outcome word. |
| `duel_reveal_missed` | Raund buraxıldı | Reveal face when the player sent neither a pick nor an answer. Wraps at 1.3 (allowed). |
| `duel_reveal_bonus_cd` | sürət bonusu %1$d xal | Bolt icon + bonus. |
| `duel_reveal_summary_cd` | %1$s. Sən %2$s, rəqib %3$s. Hesab %4$s. | Announced once per result: "Düzgün. Sən +237, rəqib −100. Hesab 537 : 300." `%1$s` = own outcome word, `%4$s` = `duel_score_pair`. |
| `duel_all_in_title` | Həlledici raund: xallar ikiqat! | Round 7 banner title, titleSmall, wraps. |
| `duel_all_in_body` | Əminlik xalları ikiqat olur, sürət bonusu dəyişmir. | Round 7 banner body. |
| `duel_sudden_death_title` | Hesab bərabərdir! | Sudden-death banner title. |
| `duel_sudden_death_body` | Əlavə sual %1$d / %2$d. Ən tez düzgün cavab verən qalib gəlir. | Sudden-death banner body. |
| `duel_sudden_death_you_won` | Daha tez düzgün cavab verdin! | Status line, sudden-death result. |
| `duel_sudden_death_opponent_won` | Rəqib daha tez düzgün cavab verdi. | Status line. |
| `duel_sudden_death_none` | Heç kim düzgün cavab vermədi. Növbəti əlavə sual. | Status line (1st and 2nd question). |
| `duel_sudden_death_same_time` | Rəqiblə eyni anda düzgün cavab verdin. Növbəti əlavə sual. | `suddenDeathOutcome = same_time` (both right, identical `answeredMs`). "sən" tone: no plural "verdiniz". |
| `duel_sudden_death_none_last` | Heç kim düzgün cavab vermədi. Oyun heç-heçə bitdi. | 3rd question unresolved → draw. |

#### Interruptions (`duel-interruptions.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_reconnecting_title` | Bağlantı bərpa olunur… | Overlay card, first 10 s. |
| `duel_reconnecting_body` | Oyun davam edir, vaxt dayanmır. | Overlay card body. |
| `duel_offline_title` | Bağlantı yoxdur | Overlay card after 10 s (OQ-032). |
| `duel_offline_body` | Oyun davam edir. Ardıcıl 2 raund buraxsan, oyun məğlubiyyətlə bitəcək. | `wrap`. |
| `duel_reconnect_button` | Yenidən qoşul | `btn` (primary). |
| `duel_leave_button` | Oyundan çıx | `tbtn` in the overlay card; opens the leave dialog. |
| `duel_rejoining` | Oyuna qayıdırsan… | Loading caption after process death / `already_in_match`. |
| `duel_opponent_disconnected` | Rəqibin bağlantısı kəsildi. %1$d san. gözləyirik. | Warning banner with live countdown (10 → 1). |
| `duel_opponent_still_away` | Rəqib hələ qayıtmayıb. Oyun davam edir: rəqib ardıcıl 2 raund buraxsa, qalib sən olacaqsan. | Warning banner after 10 s. |
| `duel_opponent_returned` | Rəqib qayıtdı. | Info banner for 2 s. |
| `duel_leave_title` | Oyundan çıxmaq istəyirsən? | Dialog title. |
| `duel_leave_body` | Bu, məğlubiyyət sayılacaq və reytinqin azalacaq. Vaxt bu arada dayanmır. | Dialog body. |
| `duel_leave_confirm` | Çıx | `dbtn`, error colour. Dismiss = `common_continue` ("Davam et"). |
| `duel_left_snackbar` | Oyundan çıxdın. Məğlubiyyət sayıldı. | Snackbar on home after a confirmed leave. |

#### Match result (`duel-result.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_result_win` | Qələbə! | displaySmall. |
| `duel_result_loss` | Məğlubiyyət | displaySmall; ~300dp at 1.3 (fits 328dp, tight, wraps never needed). |
| `duel_result_draw` | Heç-heçə | displaySmall. |
| `duel_result_abandoned` | Ləğv edildi | displaySmall. |
| `duel_result_reason_sudden_death_win` | Əlavə sualda qalib gəldin. | bodyLarge, `wrap`. |
| `duel_result_reason_sudden_death_loss` | Əlavə sualda uduzdun. | |
| `duel_result_reason_draw` | 3 əlavə sualdan sonra da hesab bərabər qaldı. | |
| `duel_result_reason_opponent_left` | Rəqib oyundan çıxdı. Qələbə sənindir! | `forfeitedBy = opponent`, `forfeitCause = left` (DUEL-15, also opponent's account deletion, OQ-069). |
| `duel_result_reason_opponent_missed_rounds` | Rəqib ardıcıl 2 raund buraxdı. Qələbə sənindir! | `forfeitedBy = opponent`, `forfeitCause = missed_rounds` (DUEL-14). |
| `duel_result_reason_opponent_forfeit` | Rəqib oyunu tərk etdi. Qələbə sənindir! | `forfeitedBy = opponent`, `forfeitCause = null` (result rebuilt after a restart). Fallback only. |
| `duel_result_reason_self_forfeit` | Ardıcıl 2 raund buraxdığın üçün oyun başa çatdı. | `forfeitedBy = self`, `forfeitCause = missed_rounds` (DUEL-14). |
| `duel_result_reason_self_left` | Oyundan çıxdın. Məğlubiyyət sayıldı. | `forfeitedBy = self`, `forfeitCause = left`. Defensive: the leaver normally goes home with `duel_left_snackbar` and never sees this screen (DUEL-15). |
| `duel_result_reason_self_forfeit_unknown` | Oyun texniki məğlubiyyətlə bitdi. | `forfeitedBy = self`, `forfeitCause = null` (rebuilt after a restart). Fallback only. |
| `duel_result_reason_abandoned` | Oyun texniki səbəbdən dayandırıldı. | OQ-033. |
| `duel_result_message_loss` | Növbəti dəfə alınacaq. Əminlik kartına bax. | Normal loss only; encouraging. |
| `duel_result_winner_badge` | Qalib | Pill on the winner's column. |
| `duel_result_score_cd` | Hesab: sən %1$s, %2$s %3$s | "Hesab: sən 640, Aysel 410". |
| `duel_result_rating_unchanged` | Reytinq dəyişmədi | Abandoned. |
| `duel_result_rating_cd` | Reytinq %1$d, dəyişiklik %2$s | |
| `duel_result_play_again` | Yenidən oyna | `btn` (primary). Joins the queue again (no rematch, OQ-025). |
| `duel_calibration_title` | Əminlik kartı | Card title. |
| `duel_calibration_subtitle` | Bu oyunda hər Əminlik səviyyəsində neçə düzgün cavab verdin | Card subtitle, `wrap`. |
| `duel_calibration_level` | Əminlik %1$d | Row label. |
| `duel_calibration_value` | %1$d / %2$d düzgün | "2 / 3 düzgün". |
| `duel_calibration_unused` | — | Level not used in this match. |
| `duel_calibration_unused_cd` | Əminlik %1$d: bu oyunda seçilməyib | |
| `duel_calibration_row_cd` | Əminlik %1$d: %3$d raunddan %2$d düzgün | |
| `duel_calibration_footnote` | Vaxt bitəndə verilən Əminlik 1, buraxılan raundlar və əlavə suallar sayılmır. | bodySmall (OQ-027). |
| `duel_calibration_hint_good` | Əla! Əmin olanda daha çox düz tapırsan. | Hint rules: `duel-result.md`. |
| `duel_calibration_hint_overconfident` | Yüksək əminlikdə tez-tez yanılırsan. Şübhə edəndə 1 seç. | |
| `duel_calibration_hint_underconfident` | Bildiyin suallarda daha cəsarətli ol: 2 və ya 3 seçməkdən çəkinmə. | |
| `duel_calibration_hint_few_data` | Daha çox raund oyna ki, nəticə dəqiqləşsin. | |
| `duel_calibration_hint_neutral` | Əminliyini bildiklərinə uyğun seçməyə çalış. | |

#### Availability (`duel-availability.md`)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_upgrade_title` | Duel üçün tətbiqi yenilə | State title. |
| `duel_upgrade_body` | Bu versiya duel üçün köhnədir. Kateqoriya quizləri əvvəlki kimi işləyir. | `wrap`. |
| `duel_upgrade_button` | Yenilə | `btn` (primary); opens the Play Store page. |
| `duel_upgrade_store_failed` | Play Store-u açmaq mümkün olmadı. | Snackbar if no store app can open the link. |
| `duel_unavailable_title` | Duel hazırda əlçatan deyil | State title. |
| `duel_unavailable_body` | Bir az sonra yenidən cəhd et. | |
| `duel_connect_failed_title` | Duelə qoşulmaq alınmadı | State title (handshake 5xx/timeout). |
| `duel_connect_failed_body` | Bağlantını yoxla və yenidən cəhd et. | |

#### Bot opponent (OQ-106 … OQ-111, `duel-search.md`, `duel-intro.md`, `duel-match.md`, `duel-result.md`, `components.md` §32)

Added 2026-10-30 (DUEL-23 … DUEL-29). The bot is always called a bot: never a human
name, never "AI", never "kompüter rəqib". Rating texts say it plainly: no Elo change.

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `duel_bot_name` | Duello Bot | **Client fallback only.** The name shown in the intro, result and history comes from the server (`displayName`, backend i18n, same text, see "Backend i18n — bot"); this key is used when that is missing and inside `duel_search_bot_hint` (no server name exists yet while searching). Keep both texts identical. |
| `duel_bot_badge` | Bot | Bot badge text (`components.md` §32), labelMedium + 14dp icon. ≈ 57 / 64dp. |
| `duel_opponent_bot_cd` | Rəqib: %1$s, bot | TalkBack label of the opponent panel / intro row: "Rəqib: Duello Bot, bot" (DUEL-24). `%1$s` = bot name. |
| `duel_bot_rating_none` | Reytinq: — | Intro opponent row, in place of the rating (U+2014). Not read by TalkBack (the row reads `duel_opponent_bot_cd`). |
| `duel_bot_no_rating_note` | Bot oyunu reytinqə təsir etmir. | Intro note under the rules and second line of the result rating card. `wrap`. |
| `duel_intro_bot_cd` | Rəqib tapıldı: %1$s, bot. Bot oyunu reytinqə təsir etmir. Oyun başlayır. | Announced once on the bot intro (assertive). |
| `duel_search_bot_hint` | %1$d saniyə ərzində oyunçu tapılmasa, %2$s ilə oynayacaqsan. İstəmirsənsə, ləğv et. | `plurals` (one/other same text). `%1$d` = seconds to `botAtEpochMs` when `queue.joined` arrived (normally 15), `%2$s` = `duel_bot_name`. Search screen, bodyMedium, `wrap` (3 lines at 1.0, 3–4 at 1.3). Polished from OQ-111 ("15 saniyəyə …"): "ərzində" is unambiguous, and the second sentence states the opt-out. |
| `duel_search_bot_countdown_cd` | Bot ilə oyuna %1$d saniyə qalıb | `plurals`. Announced at 10 s and 5 s remaining (not per second). |
| `duel_leave_body_bot` | Bu, məğlubiyyət sayılacaq. Bot oyunu reytinqə təsir etmir. Vaxt bu arada dayanmır. | Leave dialog body in a bot match (DUEL-26). Title and buttons unchanged. |
| `duel_result_rating_bot_cd` | Reytinq dəyişmədi. Bot oyunu reytinqə təsir etmir. | Result rating card in a bot match (one element). |
| `duel_result_score_bot_cd` | Hesab: sən %1$s, %2$s (bot) %3$s | "Hesab: sən 640, Duello Bot (bot) 410". |

### profile_

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `profile_title` | Profil | `bar`. |
| `profile_open_cd` | Profili aç | **v3 removed** (use `nav_profile_open_cd`). |
| `profile_edit_name_cd` | Adı dəyiş | Edit icon next to the name. |
| `profile_joined` | Qoşulub: %1$s %2$d | "Qoşulub: oktyabr 2026". `%1$s` from `profile_months` (lowercase, as in Azerbaijani running text). |
| `profile_months` | yanvar, fevral, mart, aprel, may, iyun, iyul, avqust, sentyabr, oktyabr, noyabr, dekabr | `string-array` (12 items, nominative). Do not use the system date formatter (not every device has full az data). |
| `profile_months_short` | yan, fev, mar, apr, may, iyn, iyl, avq, sen, okt, noy, dek | `string-array` for history dates. |
| `profile_record_title` | Duel nəticələri | Record card title. |
| `profile_record_total` | %1$d oyun | `plurals`. Trailing in the record card header. |
| `profile_record_cd` | %1$d qələbə, %2$d məğlubiyyət, %3$d heç-heçə, cəmi %4$d oyun | Merged card. |
| `profile_record_bot` | Bota qarşı: %1$s, %2$s, %3$s | Bot line under the record rows (OQ-110), bodyMedium, `wrap` (2 lines at 1.3). `%1$s`–`%3$s` = the three plurals below, always all three, also with 0 ("0 heç-heçə"). Hidden when the bot total is 0. Appended to `profile_record_cd` for TalkBack. |
| `profile_record_bot_wins` | %1$d qələbə | `plurals` (one/other same text). |
| `profile_record_bot_losses` | %1$d məğlubiyyət | `plurals`. |
| `profile_record_bot_draws` | %1$d heç-heçə | `plurals`. |
| `profile_record_empty_real` | Hələ oyunçulara qarşı oynamamısan. | Record card body when only bot matches were played (DUEL-28). |
| `profile_match_win` | Qələbə | Record row label and history result word. |
| `profile_match_loss` | Məğlubiyyət | |
| `profile_match_draw` | Heç-heçə | |
| `profile_match_abandoned` | Ləğv edildi | History only (not in the record, OQ-026). |
| `profile_matches_title` | SON OYUNLAR | **v3**: overline, capitals (was "Son oyunlar"). |
| `profile_match_supporting` | %1$s · %2$s | "Qələbə · 640 : 410" (`%2$s` = `duel_score_pair`). |
| `profile_match_label_opponent_forfeit` | Rəqib tərk etdi | History label (forfeit win) when the cause is unknown (no `forfeitCause`, OQ-D16). |
| `profile_match_label_self_forfeit` | Texniki məğlubiyyət | History label (forfeit loss) when the cause is unknown. |
| `profile_match_label_opponent_left` | Rəqib oyundan çıxdı | Forfeit win, `forfeitCause = left`. Needs `forfeitCause` in `MatchSummary` (OQ-D16). |
| `profile_match_label_opponent_missed_rounds` | Rəqib 2 raund buraxdı | Forfeit win, `forfeitCause = missed_rounds`. ~183dp at 1.3, fits the ~192dp line. |
| `profile_match_label_self_left` | Oyundan çıxdın | Forfeit loss, `forfeitCause = left`. |
| `profile_match_label_self_missed_rounds` | 2 raund buraxdın | Forfeit loss, `forfeitCause = missed_rounds`. |
| `profile_match_label_sudden_death` | Əlavə sualda | History label (decided in sudden death). |
| `profile_match_today` | bu gün | History date. |
| `profile_match_yesterday` | dünən | History date. |
| `profile_match_date` | %1$d %2$s | "12 okt" (`profile_months_short`). |
| `profile_match_date_year` | %1$d %2$s %3$d | "12 okt 2025" (other year). |
| `profile_match_cd` | %1$s ilə oyun, %2$s, hesab %3$s, reytinq %4$s, %5$s | "Aysel ilə oyun, Qələbə, hesab 640 : 410, reytinq +16, dünən". |
| `profile_match_rating_none` | — | Trailing rating column of a bot match (U+2014), never "0" (DUEL-28). |
| `profile_match_bot_cd` | %1$s ilə oyun, bot, %2$s, hesab %3$s, reytinq dəyişmədi, %4$s | "Duello Bot ilə oyun, bot, Qələbə, hesab 640 : 410, reytinq dəyişmədi, dünən". |
| `profile_calibration_title` | Əminlik statistikası | Card title. |
| `profile_calibration_subtitle` | Bütün oyunlarda hər Əminlik səviyyəsində düzgün cavab payın | `wrap`. |
| `profile_calibration_value` | %1$d dəfə · %2$d%% düzgün | "18 dəfə · 72% düzgün". In `strings.xml` the percent sign is `%%`. Decision (PROF-05): "72%" — number then sign, no space, as usual in Azerbaijani UI text. |
| `profile_calibration_few` | %1$d dəfə · Az məlumat | Fewer than 5 counted rounds at this level. |
| `profile_calibration_never` | Hələ seçilməyib | 0 counted rounds. |
| `profile_calibration_row_cd` | Əminlik %1$d: %2$d dəfə seçilib, %3$d faiz düzgün | |
| `profile_calibration_row_few_cd` | Əminlik %1$d: %2$d dəfə seçilib, az məlumat | |
| `profile_calibration_footnote` | 1–7-ci raundlar sayılır. Vaxt bitəndə verilən Əminlik 1, buraxılan raundlar və əlavə suallar sayılmır. | bodySmall. |
| `profile_empty_title` | Hələ duel oynamamısan | Empty state title. |
| `profile_empty_body` | İlk duelini oyna və Əminlik statistikanı gör. | Empty state body. |
| `profile_empty_cta` | Duelə başla | `btn` (primary). Hidden when the duel flag is off. |
| `profile_section_error` | Yükləmək alınmadı | Inline section error; action `common_retry` (`tbtn`). |
| `profile_edit_title` | Adı dəyiş | Dialog title. |
| `profile_edit_save` | Yadda saxla | `dbtn`. 11 chars; at 1.3 ~117dp > ~100dp, the dialog stacks the buttons (accepted, see risks). |
| `profile_edit_saved` | Adın dəyişdirildi. | Snackbar. |
| `profile_edit_offline` | Adı dəyişmək üçün internet lazımdır. | Field error in the dialog. |
| `profile_edit_failed` | Adı dəyişmək alınmadı. Yenidən cəhd et. | Field error (timeout 15 s / 5xx). |

### daily_ / home_daily_ (daily challenge, `daily-challenge.md`)

Added 2026-10-22 (DAILY-01 … DAILY-10, HOME-07). Never show a clock time; only
countdowns (DAILY-07). The score is "4 / 5" with spaces in the UI and "4/5" without
spaces only in the share text (OQ-080).

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `daily_title` | Gündəlik çağırış | Overline on the home card, play top bar, result `bar` title (~245dp at 1.3, fits 264dp). |
| `daily_number` | #%1$d | "#12". headlineMedium on the card, pill on the result hero. |
| `home_daily_hint` | %1$d sual · hamı üçün eyni | `plurals`. Gradient card hint; value 5. U+00B7 middle dot. |
| `home_daily_in_progress_hint` | Başladığın çağırışı bitir. Vaxt dayanmır. | Gradient card hint, in progress. No remaining time on the card. |
| `home_daily_start` | Başla | `btn` in the daily card. |
| `home_daily_resume` | Davam et | `btn` in the daily card. |
| `home_daily_next_countdown` | Növbəti çağırış: %1$s | `%1$s` = "05:12:33" (HH:MM:SS, tabular). titleSmall, 1 line at 1.3 (~268dp of 274dp). |
| `home_daily_refreshing` | Yeni çağırış yüklənir… | Countdown reached 0. |
| `home_daily_unavailable` | Bu günün çağırışı hazır deyil. Sabah yenidən yoxla. | `wrap`. Status `unavailable`. |
| `home_daily_offline_unknown` | Bugünkü çağırışı görmək üçün internet lazımdır. | `wrap`. Offline with a cache of an older day. |
| `home_daily_error` | Çağırışı yükləmək alınmadı. | `wrap`. Action `common_retry`. |
| `home_daily_needs_internet` | Gündəlik çağırış üçün internet lazımdır. | Snackbar on "Başla" / "Davam et" offline. |
| `home_daily_card_cd` | Gündəlik çağırış %1$d, %2$d sual. Başla. | `plurals` on `%2$d`. Gradient card, available. |
| `home_daily_card_in_progress_cd` | Gündəlik çağırış %1$d davam edir. Davam et. | |
| `home_daily_card_finished_cd` | Gündəlik çağırış %1$d: %3$d sualdan %2$d düzgün. Növbəti çağırışa %4$d saat %5$d dəqiqə qalıb. | Finished card, recomputed on focus. |
| `daily_score_short` | %1$d / %2$d | "4 / 5" on the finished card, tabular. |
| `daily_close_cd` | Çağırışdan çıx | Close icon (play, loading). |
| `daily_start_error` | Çağırışı başlatmaq alınmadı. | State title on the daily loading screen. Actions `common_retry`, `common_back`. |
| `daily_exit_title` | Çağırışdan çıxmaq istəyirsən? | Dialog title. |
| `daily_exit_body` | Çıxsan da vaxt gedir. Bugünkü cəhdin bitəndə yenidən oynamaq olmayacaq. | Dialog body (DAILY-03). |
| `daily_exit_confirm` | Çıx | `dbtn`. Dismiss = `common_continue`. |
| `daily_result_correct_count` | %1$d / %2$d düzgün | Hero line, "4 / 5 düzgün". |
| `daily_result_score_cd` | Gündəlik çağırış %3$d: %2$d sualdan %1$d düzgün cavab | Hero content description. |
| `daily_result_message_perfect` | Hamısı düzgün! Bugünkü çağırış tam sənindir. | 5 / 5. |
| `daily_result_message_good` | Əla nəticə! Sabah yeni çağırış səni gözləyir. | 3–4. |
| `daily_result_message_low` | Başlanğıc var! Səhvlərə bax, sabah daha yaxşı olacaq. | 1–2. |
| `daily_result_message_zero` | Bu dəfə alınmadı, amma hər sual yeni bilikdir. Sabah yenə gəl! | 0 / 5, encouraging. |
| `daily_result_expired_note` | Vaxt bitdi. Cavab verilməmiş suallar düzgün sayılmadı. | Deadline ended the attempt. |
| `daily_result_not_synced` | Nəticə serverə göndərilə bilmədi. İnternet qayıdanda yenidən göndərəcəyik, XP və seriya sonra görünəcək. | Info banner (DAILY-04). |
| `daily_share_card_title` | Nəticən | Share card title. |
| `daily_share_card_hint` | Paylaşanda cavablar görünmür — yalnız rənglər. | bodySmall under the squares. |
| `daily_share` | Paylaş | `btn` with share icon (result card and finished home card). |
| `daily_share_pending` | Nəticə hələ təsdiqlənməyib | Replaces the hint while "Paylaş" is disabled (DAILY-05). |
| `daily_share_chooser_title` | Nəticəni paylaş | Android share sheet title. |
| `daily_share_title` | Duello Quiz · Gündəlik çağırış #%1$d | Share text line 1 (OQ-080). |
| `daily_share_square_correct` | 🟩 | U+1F7E9. `translatable="false"`. |
| `daily_share_square_wrong` | 🟥 | U+1F7E5. `translatable="false"`. |
| `daily_share_square_none` | ⬜ | U+2B1C (no answer). `translatable="false"`. |
| `daily_share_score` | %1$d/%2$d | Share text only, no spaces: "3/5". |
| `daily_share_streak` | Seriya: %1$d gün | `plurals`. Share text line 3; left out when the streak is not confirmed. |
| `daily_share_link` | https://play.google.com/store/apps/details?id=%1$s | `translatable="false"`; `%1$s` = applicationId. |
| `daily_squares_cd` | Nəticələr: %1$s | `%1$s` = the items below joined with ", ". |
| `daily_square_item_cd` | sual %1$d %2$s | "sual 2 səhv". |
| `daily_square_correct_cd` | düzgün | |
| `daily_square_wrong_cd` | səhv | |
| `daily_square_none_cd` | cavab verilməyib | |
| `daily_square_pending_cd` | təsdiqlənməyib | |

### progress_ / home_streak_ (XP, levels, streak, `progression.md`)

Added 2026-10-22 (PROG-01 … PROG-10, HOME-08). "XP" is never translated and never
called "xal" (glossary: "xal" is the quiz/duel score). Numbers ≥ 1 000 use a
no-break space as the thousands separator ("12 450 XP"); placeholders that carry
such numbers are `%s` (pre-formatted).

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `progress_level` | Səviyyə %1$d | Level chip (home), progress card, category rows. |
| `progress_level_cd` | səviyyə %1$d | Appended to the home header content description. |
| `progress_xp_gained` | +%1$s XP | headlineSmall, e.g. "+70 XP". Never shown for 0. |
| `progress_xp_into_level` | %1$s / %2$s XP | "270 / 500 XP", tabular. |
| `progress_level_up` | Yeni səviyyə: %1$d! | Level-up strip, titleMedium, wraps. |
| `progress_level_up_cd` | Yeni səviyyəyə çatdın: %1$d. | |
| `progress_category_level_up` | %1$s: səviyyə %2$d | "Coğrafiya: səviyyə 3". `%1$s` = category name from the server. |
| `progress_xp_bot_note` | Bota qarşı oyunda yarım XP qazanırsan. | Duel result progress card of a bot match, under the XP row (`components.md` §26 5a), bodyMedium, `wrap`. The number itself comes from the server (OQ-109). |
| `progress_xp_capped` | Bu gün bu oyun növündən maksimum XP qazandın. | Cap note (PROG-09, needs OQ-D29). |
| `progress_streak_days` | Seriya: %1$d gün | `plurals`. Streak row of the progress card. |
| `progress_streak_extended` | +1 gün | Chip when `streakExtendedToday`. |
| `progress_streak_extended_cd` | Seriyan uzandı: %1$d gün. | `plurals`. |
| `progress_card_cd` | %1$s XP qazandın. Səviyyə %2$d, növbəti səviyyəyə %3$s XP qalıb. | Progress card with XP (PROG-02). |
| `progress_card_no_xp_cd` | Səviyyə %1$d, növbəti səviyyəyə %2$s XP qalıb. | Progress card without the XP row. |
| `progress_streak_cd_active` | %1$d günlük seriya, bu gün oynamısan | `plurals`. HOME-08. |
| `progress_streak_cd_inactive` | %1$d günlük seriya, bu gün hələ oynamamısan | `plurals`. Also for streak 0 and unconfirmed cache. |
| `progress_streak_freeze_cd` | seriya qoruyucusu var | Appended when a freeze is held. |
| `home_streak_hint` | Seriyanı qoru: bu gün bir oyun oyna. | Home message banner (PROG-06), dismissible. |
| `home_streak_freeze_used` | Seriya qoruyucusu dünən seriyanı qorudu. | Home message banner, once (PROG-07). |
| `home_streak_lost` | Seriyan bitdi, amma ən uzun seriyan %1$d gün olaraq qalır. Bu gün yenidən başla! | `plurals`. Home message banner, once (PROG-08). Never mocking. |
| `profile_level_title` | Səviyyə %1$d | Level card title (PROF-09). |
| `profile_level_total` | Cəmi %1$s XP | "Cəmi 1 270 XP". |
| `profile_level_to_next` | Növbəti səviyyəyə %1$s XP | |
| `profile_level_cd` | Səviyyə %1$d. %2$s / %3$s XP. Növbəti səviyyəyə %4$s XP qalıb. Cəmi %5$s XP. | Level card, one element. |
| `profile_streak_current` | Seriya: %1$d gün | `plurals`. Streak card (PROF-11). |
| `profile_streak_best` | Ən uzun seriya: %1$d gün | `plurals`. |
| `profile_streak_today_done` | Bu gün oynamısan | |
| `profile_streak_today_pending` | Bu gün hələ oynamamısan | |
| `profile_streak_freeze_available` | Seriya qoruyucusu: var | |
| `profile_streak_freeze_earn` | Seriya qoruyucusu %1$d günlük seriyada qazanılır | Value 7 (OQ-086). `wrap`. |
| `profile_streak_empty` | Hələ seriyan yoxdur. Bu gün bir oyun oyna və başla. | Streak 0 and best 0. |
| `profile_streak_cd` | Seriya %1$d gün, ən uzun seriya %2$d gün | Streak card, one element; today and freeze lines are read after it. |
| `profile_categories_title` | KATEQORİYALAR | **v3**: overline, capitals (was "Kateqoriyalar"). PROF-10. |
| `profile_category_level_cd` | %1$s: səviyyə %2$d, növbəti səviyyəyə %3$s XP qalıb | Category row (PROF-10). |

### notif_ / settings_notif_ (notifications, `notifications.md`)

Added 2026-10-22 (NOTIF-01 … NOTIF-08, SET-07). No clock times, no guilt, no false
urgency, no emoji.

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `notif_prompt_title` | Xatırladaq? | Permission card title (titleMedium). |
| `notif_prompt_body` | Gündəlik çağırış hazır olanda və seriyan bitmək üzrə olanda sənə xəbər verək. | `wrap`. |
| `notif_prompt_allow` | İcazə ver | Primary button; opens the system dialog (requirement proposed "Bəli"). |
| `notif_prompt_later` | Sonra | Text button. |
| `notif_open_settings` | Ayarları aç | Primary button when the permission is permanently denied. |
| `notif_enabled_snackbar` | Bildirişlər açıldı. Parametrlərdən istədiyin vaxt dəyişə bilərsən. | Snackbar after "granted". |
| `settings_section_notifications` | BİLDİRİŞLƏR | **v3**: overline, capitals (was "Bildirişlər"). SET-07. |
| `settings_notif_daily` | Gündəlik çağırış xatırlatması | Switch row, `row` (2 lines at 1.3). |
| `settings_notif_daily_supporting` | Səhər, bugünkü çağırışı hələ oynamamısansa | Supporting text, `wrap`. |
| `settings_notif_streak` | Seriya xatırlatması | Switch row. |
| `settings_notif_streak_supporting` | Axşam, seriyan varsa və bu gün hələ oynamamısansa | Supporting text, `wrap`. |
| `settings_notif_blocked` | Bildirişlər telefonda söndürülüb. | Warning banner in the section. |
| `settings_notif_open_system` | Ayarları aç | Banner action; opens the app's system notification settings. |
| `settings_notif_channel_off` | Telefonda söndürülüb | Supporting text of a row whose channel is off. |
| `settings_notif_load_error` | Bildiriş ayarlarını yükləmək alınmadı. | Replaces the switch rows (no cache). Action `common_retry`. |
| `settings_notif_save_failed` | Dəyişikliyi yadda saxlamaq alınmadı. | Snackbar; the switch reverts. |
| `settings_notif_offline` | Bildiriş ayarlarını dəyişmək üçün internet lazımdır. | Snackbar; the switch reverts. |
| `notif_channel_daily_name` | Gündəlik çağırış | Android channel name. |
| `notif_channel_daily_description` | Bugünkü çağırışı hələ oynamamısansa, səhər xatırladırıq. | Android channel description. |
| `notif_channel_streak_name` | Seriya | Android channel name. |
| `notif_channel_streak_description` | Seriyan bitmək üzrə olanda axşam xatırladırıq. | Android channel description. |

### push_ — backend i18n (not in `strings.xml`)

**Backend i18n, not Android.** The server sends these texts in FCM notification
messages (OQ-096), resolved by the token's locale with fallback `az`, in its i18n
JSON (`{"az": "..."}`). backend-dev mirrors them 1:1; the placeholder syntax is the
backend's (OQ-D31), the names stay `dailyNumber` and `streak`. Limits: title ≤ 40,
body ≤ 90 characters (counted with the largest realistic values).

| Key | Azerbaijani | Length | Sent when |
| --- | --- | ---: | --- |
| `push_daily_reminder_title` | Gündəlik çağırış #{dailyNumber} hazırdır | 31 (at #1234) | `daily_reminder`, 10:00 Baku, today's daily not started (OQ-088) |
| `push_daily_reminder_body` | 5 sual səni gözləyir. Bu gün neçəsini tapacaqsan? | 49 | same |
| `push_streak_reminder_title` | {streak} günlük seriyanı qoru | 25 (at 1000) | `streak_reminder`, 20:00 Baku, streak ≥ 2, today not active |
| `push_streak_reminder_body` | Bu gün bir oyun oyna ki, seriyan davam etsin. | 45 | same |

### Backend i18n — bot (not in `strings.xml`)

**Backend i18n, not Android.** The server sends the bot's `displayName` in
`match.found`, `match.result` / `match.state` and `GET /v1/me/matches`, resolved by
`Accept-Language` with fallback `az` (DUEL-24). backend-dev picks the key name in its
i18n JSON; the text must equal `duel_bot_name`.

| Text | Azerbaijani | Used in |
| --- | --- | --- |
| Bot display name | Duello Bot | Intro, player panel TalkBack, result score column, history rows. "duello" is a reserved display-name word (OQ-051), so no human can be called this. |

### v3 minimal (tokens 3.0.0, 2026-11-17) — new keys

Bottom navigation, home "Oyna", Gündəlik tab, Profil tab. Widths below were
**measured from the font files** (hmtx advance widths of static instances made
with fonttools: Plus Jakarta Sans 500/700/800, Fraunces SemiBold opsz 72 /
Regular opsz 48; kerning ignored, so slightly conservative). Format:
"width at font scale 1.0 / 1.3 (budget)". Older budgets in this file stay valid
as upper bounds.

Overline strings are **authored in capitals** (never uppercased at runtime: the
"i → İ" rule depends on the locale). Each greeting line carries one squiggle word
as an Android string annotation `<annotation squiggle="1">…</annotation>` (read
with `getText()`, `components.md` §36); shown below as [word].

| Key | Azerbaijani | Notes / width |
| --- | --- | --- |
| `nav_play` | Oyna | Bottom bar label. 34 / 45dp (104). |
| `nav_daily` | Gündəlik | Bottom bar label. 57 / 74dp (104). |
| `nav_profile` | Profil | Bottom bar label. 34 / 44dp (104). |
| `nav_daily_badge_cd` | bugünkü çağırış hələ oynanmayıb | Appended to the Gündəlik item label while the badge dot shows. |
| `nav_daily_open_cd` | Gündəlik bölməsini aç | Click label of the home streak chip. |
| `nav_profile_open_cd` | Profil | Content description of the home avatar (role Button). |
| `common_weekdays` | Bazar ertəsi, Çərşənbə axşamı, Çərşənbə, Cümə axşamı, Cümə, Şənbə, Bazar | `string-array`, Monday first (ISO). |
| `common_weekdays_short` | B.e, Ç.a, Ç, C.a, C, Ş, B | `string-array`, Monday first. Week row labels (labelSmall). ≤ 19 / 25dp (40dp cell). |
| `home_date_line` | %1$s, %2$d %3$s | "Bazar ertəsi, 16 noyabr"; `%1$s` from `common_weekdays`, `%3$s` from `profile_months`. Asia/Baku date. Longest "Çərşənbə axşamı, 30 sentyabr" 202 / 263dp (212): **wraps to 2 lines at 1.3** (allowed). |
| `home_greeting_hello` | Salam, %1$s. | Greeting line 1 (headlineSmall serif); `%1$s` = first word of the display name. 20-char name 275 / 357dp (328): wraps at 1.3 (allowed). |
| `home_greeting_hello_no_name` | Salam. | Name not known yet. |
| `home_greeting_1` | Bu gün kimisə [yenək]? | Duel line (skipped while the duel flag is off). 233 / 303dp (328). |
| `home_greeting_2` | Bu gün nə [öyrənək]? | 212 / 275dp. |
| `home_greeting_3` | Bir duel, bir fincan [çay]? | Duel line. 252 / 328dp (fits exactly at 1.3). |
| `home_greeting_4` | Yeni gün, yeni [suallar]. | 235 / 305dp. |
| `home_greeting_5` | [Əminsən]? Gəl yoxlayaq. | Duel line (Əminlik). 256 / 333dp: wraps at 1.3 (allowed). |
| `home_greeting_6` | Hazırsan? [Başlayaq]. | 218 / 283dp. |
| `home_greeting_7` | Bu gün nəyi [bilirsən]? | 225 / 292dp. |
| `home_greeting_8` | Bir sual, bir [kəşf]. | 179 / 233dp. |
| `home_duel_duration` | ~%1$d dəq | Value 3 (7 rounds ≈ 2.5 min + intro). labelLarge. 51 / 66dp. |
| `home_duel_duration_cd` | təxminən %1$d dəqiqə | `plurals`; used inside `home_duel_card_cd`. |
| `home_duel_sticker_rating` | Reytinq %1$d | Sticker, labelMedium tabular. "Reytinq 12400" 95 / 123dp (150). No colon: a label, not a sentence. |
| `home_duel_card_cd` | Duel. %1$s. %2$s. %3$s Rəqib tap | `%1$s` = hint text, `%2$s` = `home_duel_duration_cd`, `%3$s` = "Reytinq 1240." or empty. |
| `home_categories_overline` | TƏK OYNA · %1$d SUAL | `plurals`, value 10. 135 / 175dp (328). |
| `daily_tab_title` | Gündəlik | Gündəlik tab top app bar. 87 / 113dp (264). |
| `daily_tab_overline` | GÜNÜN ÇAĞIRIŞI | Challenge card overline. 112 / 146dp. |
| `daily_tab_streak_overline` | SERİYA | 48 / 62dp. |
| `daily_tab_streak_best` | ən uzun: %1$d gün | `plurals`. Under "Seriya: 7 gün". 110 / 143dp. |
| `daily_tab_week_cd` | Bu həftə: %1$s | `%1$s` = day items joined with ", " (days after today left out). |
| `daily_tab_week_day_cd` | %1$s %2$s | "Bazar ertəsi oynamısan"; `%1$s` weekday or `daily_tab_today_cd`, `%2$s` one of the state keys below. |
| `daily_tab_today_cd` | bu gün | |
| `daily_tab_day_active_cd` | oynamısan | |
| `daily_tab_day_pending_cd` | hələ oynamamısan | Today only. |
| `daily_tab_day_freeze_cd` | seriya qoruyucusu işlədi | |
| `daily_tab_day_missed_cd` | oynamamısan | |
| `daily_tab_reminder_overline` | XATIRLATMA | 84 / 110dp. |
| `daily_tab_reminder` | Hər gün xatırlat | Switch headline. 117 / 152dp (244): 1 line. Supporting reuses `settings_notif_daily_supporting` (295 / 384dp → 2 lines, allowed). |
| `daily_result_back_to_tab` | Gündəliyə qayıt | Main button of the daily result in v3 (replaces `common_home` there). ~120 / 155dp (280). |
| `profile_settings_cd` | Parametrlər | Settings icon in the Profil top app bar. |
| `profile_streak_open_daily` | Həftəni Gündəlik bölməsində gör | Supporting text of the Profil streak row (`row`, wraps). |

Other v3 strings checked with the same method: "Seriya: 365 gün" serif 24sp
165 / 215dp (328); "Növbəti çağırış: 05:12:33" 170 / 221dp (264); "#1234"
numeralLarge 156 / 203dp; "Kinematoqrafiya" category row 130 / 170dp (216);
"Azərbaycan ədəbiyyatı" 179 / 233dp → 2 lines at 1.3 (allowed); "Səviyyə 123"
chip 73 / 95dp.

### v3 — removed keys

Delete from `strings.xml` when the v3 screens ship: `home_categories_title`,
`home_profile_cd`, `profile_open_cd`, and `home_settings_cd` (only after
`docs/legal/account-deletion.html` no longer names the home settings icon).
Moved but kept: `home_streak_hint`, `home_streak_freeze_used`, `home_streak_lost`
and `home_daily_*` (now on the Gündəlik tab; prefix kept to avoid churn),
`progress_level` (Profil header).

### v3 — changed values

`home_duel_hint`, `home_duel_cta`, `settings_section_theme`,
`settings_section_language`, `settings_section_notifications`,
`settings_section_account`, `settings_section_about`, `profile_matches_title`,
`profile_categories_title`, `settings_licenses_section_assets`,
`settings_licenses_section_libraries` (see their rows above, marked **v3**).

### exam_ — mock exams "Sınaqlar" (tokens 3.1.0, 2026-11-21)

Phase 1 = "Buraxılış — İngilis dili" subject sınaq (OQ-128). Screens:
`design/screens/exam-*.md`; components `components.md` §3a, §39 … §46. Copy
rules for this feature, on top of the "sən" tone:
- **Never claim to be an official or real DİM exam** (EXAM-25, OQ-130): no
  "rəsmi", "real", "DİM imtahanı", "DİM balı", no year, no DİM logo. "DİM
  formatında" and "DİM formatına uyğun" are the only allowed uses of "DİM",
  plus the disclaimer and "Bu, rəsmi DİM nəticəsi deyil."
- The score is always "Təxmini bal"; raw points are "xal", the 100-scale score
  is "bal".
- Never "mərc", "stavka", no reward or casino wording (nothing to win here).
- No ordinal suffixes after placeholders ("1-i", "2-si" change with the number):
  use "%1$d / %2$d", "Abzas %1$d", "A variantı" instead.
- Fractions ⅓ ⅔ only in the sans face (Fraunces has no such glyphs); "№" and
  the fractions need the 3.1.0 subset (`tokens.json`), check with
  `python3 design/tools/text_width.py --glyphs "№⅓½⅔"`.
- Widths: **measured** with `design/tools/text_width.py` (stdlib; reads the
  shipped font files; same method as "v3 minimal": hmtx advances, kerning
  ignored, letter spacing added). Format "role width at 1.0 / 1.3dp (budget)".
  `wrap` strings are body text that wraps freely. Budgets: `bar` 264, `btn` 280,
  half pill 110 (84 with an icon), `dbtn` ~100, status line 242, segment 114,
  mark tile 51, exam header centre 157 / 143.

#### Oyna entry (`home.md`, `exam-catalogue.md`)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `home_exams_overline` | İMTAHANA HAZIRLIQ | Overline above the Sınaqlar row (authored in capitals). overline 137 / 178dp (328): fits |
| `home_exams_row_title` | Sınaqlar | Exam row title. titleMedium 64 / 83dp (260): fits |
| `home_exams_row_supporting` | DİM formatında məşq sınaqları | Row supporting; 2 lines at 1.3 (allowed). bodyMedium 203 / 264dp (260): wraps at 1.3 |
| `home_exams_row_running` | Davam edir — %1$s qalıb | Row supporting while an attempt runs; `%1$s` = `exam_duration_min`. bodyMedium 178 / 232dp (260): fits — measured "Davam edir — 42 dəq qalıb" |
| `home_exams_row_cd` | Sınaqlar. %1$s | `%1$s` = the visible supporting text. |
| `home_exam_running_banner` | Davam edən sınaq: %1$s qalıb | Compact info banner, text budget 244dp (icon + chevron); wraps at 1.3 (allowed). bodyMedium 214 / 279dp (244): wraps at 1.3 — measured "Davam edən sınaq: 42 dəq qalıb" |
| `exam_resume_click_cd` | sınağa davam et | Click label of the running banner / card. |

#### Shared (`exam_`)

Durations and sizes are built in code from numbers; decimal comma, one place.

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_disclaimer` | Sınaqlar Duello Quiz komandasının orijinal tapşırıqlarıdır və DİM formatına uyğun hazırlanıb. Dövlət İmtahan Mərkəzi (DİM) ilə əlaqəsi yoxdur və DİM tərəfindən təsdiqlənməyib. Bal təxminidir. | EXAM-25 / OQ-130 text **unchanged** (decided). `wrap`, exam note §46: 5 lines at 1.0, 7 at 1.3 on 328dp. Catalogue, info, result, settings. |
| `exam_estimate_note` | Nəticə təxmini baldır, rəsmi DİM nəticəsi deyil. | Second paragraph of the info-screen note. `wrap`. |
| `exam_variant_title` | Sınaq №%1$d | "Sınaq №1". Needs U+2116 in both font subsets (tokens 3.1.0). Never a year, "rəsmi", "real" (EXAM-25). titleMedium 64 / 83dp (240): fits — measured "Sınaq №1"; glyph № not in the shipped subset (tokens 3.1.0 adds it) |
| `exam_size_mb` | %1$s MB | "3,4 MB"; below 0,1 → "0,1 MB". |
| `exam_duration_min` | %1$d dəq | "42 dəq" in status lines; below 1 min → "1 dəq" (never "0 dəq" while running). |
| `exam_duration_hm` | %1$d saat %2$d dəq | Phase 2 (≥ 60 min left in a 180-min attempt). |
| `exam_duration_cd` | %1$d dəqiqə | `plurals`. Content descriptions. |
| `exam_seconds_cd` | %1$d saniyə | `plurals`. Timer content description in the last minute. |
| `exam_retake_label` | Təkrar cəhd | Chip (EXAM-20). labelMedium 77 / 100dp (200): fits |
| `exam_mode_subject` | Fənn sınağı | Chip (EXAM-19). labelMedium 72 / 93dp (200): fits |
| `exam_mode_full` | Tam sınaq | Chip, Phase 2. labelMedium 64 / 84dp (200): fits |
| `exam_back_to_catalogue` | Sınaqlara qayıt | `btn`. labelLarge 109 / 142dp (280): fits |

#### Catalogue and history (`exam-catalogue.md`)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_catalogue_title` | Sınaqlar | `bar`. titleLarge 79 / 102dp (264): fits |
| `exam_type_meta` | %1$s · %2$d tapşırıq · %3$s | `%1$s` = `exam_mode_subject`, `%3$s` = `exam_duration_min`. bodyMedium 215 / 280dp (328): fits — measured "Fənn sınağı · 30 tapşırıq · 60 dəq" |
| `exam_variant_meta` | %1$d tapşırıq · %2$s · %3$s | Variant card line 2. bodyMedium 187 / 243dp (264): fits — measured "30 tapşırıq · 60 dəq · 3,4 MB" |
| `exam_status_not_started` | Başlanmayıb | Status line (18dp icon + text, budget 242dp). labelMedium 83 / 108dp (242): fits |
| `exam_status_downloaded` | Yüklənib · başlamağa hazır | labelMedium 170 / 222dp (242): fits |
| `exam_status_downloading` | Yüklənir… %1$d%% | `%%` = literal percent sign. labelMedium 103 / 134dp (242): fits — measured "Yüklənir… 100%" |
| `exam_status_running` | Davam edir — %1$s qalıb | labelMedium 171 / 223dp (242): fits — measured "Davam edir — 42 dəq qalıb" |
| `exam_status_pending` | Nəticə hesablanır | Submitted offline, not yet scored. labelMedium 113 / 147dp (242): fits |
| `exam_status_needs_assessment` | Yazılılar qiymətləndirilməyib | labelMedium 182 / 237dp (242): fits |
| `exam_status_score` | Təxmini bal: %1$s | Never "DİM balı" (EXAM-15). labelMedium 119 / 154dp (242): fits — measured "Təxmini bal: 100,0" |
| `exam_status_update` | Yenilənib — yenidən yüklə | labelMedium 168 / 219dp (242): fits |
| `exam_variant_card_cd` | %1$s. %2$d tapşırıq, %3$s, %4$s meqabayt. %5$s. | `%3$s` = `exam_duration_cd`, `%5$s` = status text. |
| `exam_catalogue_empty_title` | Hələ ki sınaq yoxdur | State view title (headlineSmall, wraps). headlineSmall 219 / 284dp (328): fits |
| `exam_catalogue_empty_body` | Yeni sınaqlar hazırlanır. Bir az sonra yenidən yoxla. | `wrap`. |
| `exam_catalogue_error` | Sınaqları yükləmək alınmadı. | State view title. headlineSmall 313 / 407dp (328): wraps at 1.3 |
| `exam_catalogue_offline` | İnternet yoxdur. Başlanmış sınağı oflayn davam etdirə bilərsən, yenisini başlatmaq üçün internet lazımdır. | Full warning banner. `wrap` (3 lines at 1.0). |
| `exam_catalogue_offline_body` | Sınaqların siyahısını görmək üçün internetə qoşul. | State view body. |
| `exam_history_title` | Sınaqlarım | Row title and `bar` title. titleLarge 102 / 132dp (264): fits |
| `exam_history_count` | %1$d cəhd | `plurals`. Row supporting. |
| `exam_history_none` | Hələ cəhd yoxdur | Row supporting. |
| `exam_history_row_title` | %1$s · %2$s | `%1$s` = `exam_variant_title`, `%2$s` = subject name (server). titleMedium 147 / 192dp (296): fits — measured "Sınaq №1 · İngilis dili"; glyph № not in the shipped subset (tokens 3.1.0 adds it) |
| `exam_history_date` | %1$d %2$s | "21 noyabr" (Baku day); `profile_months`. |
| `exam_history_date_year` | %1$d %2$s %3$d | "21 noyabr 2027" when not the current year. |
| `exam_history_abandoned` | Bitirilməyib — server yekunlaşdırıb | History supporting (wraps at 1.3). bodyMedium 229 / 298dp (264): wraps at 1.3 |
| `exam_history_empty_title` | Hələ sınaq verməmisən | State view title. headlineSmall 250 / 325dp (328): fits |
| `exam_history_empty_body` | İlk sınağını seç — nəticən burada görünəcək. | `wrap`. |
| `exam_history_empty_cta` | Sınaq seç | `btn` (secondary). labelLarge 70 / 91dp (280): fits |
| `exam_history_error` | Sınaqlarını yükləmək alınmadı. | State view title; wraps (allowed). headlineSmall 334 / 434dp (328): **2 lines at 1.0** |

#### Info and download (`exam-info.md`)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_info_overline` | FƏNN SINAĞI · DİM FORMATINDA | overline 218 / 284dp (328): fits |
| `exam_info_fact_duration_cap` | dəqiqə | Facts caption (bodySmall, 104dp item). bodySmall 43 / 56dp (104): fits |
| `exam_info_fact_tasks_cap` | tapşırıq | bodySmall 45 / 58dp (104): fits |
| `exam_info_fact_max_cap` | maksimum bal | bodySmall 85 / 110dp (104): wraps at 1.3 |
| `exam_info_fact_cd` | %1$d %2$s | "60 dəqiqə", "30 tapşırıq", "100 maksimum bal". |
| `exam_info_structure_overline` | QURULUŞ | overline 64 / 83dp (328): fits |
| `exam_info_section_counts` | %1$d tapşırıq: %2$d qapalı, %3$d yazılı | Section row supporting. bodyMedium 173 / 225dp (260): fits — measured "8 tapşırıq: 4 qapalı, 4 yazılı" |
| `exam_info_section_closed_only` | %1$d qapalı tapşırıq | bodyMedium 112 / 145dp (260): fits — measured "16 qapalı tapşırıq" |
| `exam_info_rules_overline` | QAYDALAR | overline 74 / 96dp (328): fits |
| `exam_info_rule_time` | Vaxt %1$d dəqiqədir. Fasilə yoxdur, vaxt arxa planda da işləyir. | `wrap`. EXAM-02 wording kept. |
| `exam_info_rule_navigation` | Tapşırıqları istədiyin ardıcıllıqla həll et. Bitirənə qədər cavablarını dəyişə bilərsən. | `wrap`. |
| `exam_info_rule_listening` | Dinləmə mətni %1$d dəfə səslənir. Fasilə vermək olar, geri çəkmək olmaz. Qulaqcıq taxmağı tövsiyə edirik. | `wrap`. Value 2 (OQ-119). |
| `exam_info_rule_written` | Yazılı tapşırıqları sınaqdan sonra özün qiymətləndirəcəksən. | `wrap`. EXAM-02 wording kept. |
| `exam_info_rule_scoring` | Hər düzgün qapalı cavab 1 xal, hər yazılı tapşırıq 2 xala qədər verir. Cəmi %1$d xal 100 bala çevrilir. Səhv cavab xal azaltmır. | `wrap`. Value 37 (English). "xal" = raw point, "bal" = the 100-scale score, used consistently. |
| `exam_info_rule_penalty` | Səhv qapalı cavab ¼ xal azaldır, boş buraxılan cavab azaltmır. | Phase 3 only (stage 2). ¼ U+00BC is in the subset. |
| `exam_info_rule_online` | Başlamaq üçün internet lazımdır. Sonra oflayn da davam edə bilərsən — cavablar telefonda saxlanılır. | `wrap`. |
| `exam_info_skip_listening` | Dinləmə hissəsini keç | Switch headline (244dp). titleMedium 166 / 216dp (244): fits |
| `exam_info_skip_listening_supporting` | Eşitməkdə çətinlik çəkirsənsə. Dinləmə tapşırıqları göstərilməyəcək və təxmini bala daxil edilməyəcək. | `wrap`. |
| `exam_info_download_hint` | Sınaq əvvəlcədən telefona yüklənir ki, zəif internet vaxtını almasın. | `wrap`. |
| `exam_info_download` | Yüklə · %1$s | `btn` primary. labelLarge 109 / 142dp (280): fits — measured "Yüklə · 12,8 MB" |
| `exam_info_downloading` | Yüklənir… %1$s / %2$s | Line above the bar. bodySmall 137 / 178dp (328): fits — measured "Yüklənir… 12,8 / 12,8 MB" |
| `exam_download_paused_offline` | İnternet kəsildi. Qayıdanda yükləmə özü davam edəcək. | Warning banner. |
| `exam_download_failed` | Yükləmə yarımçıq qaldı. Yenidən cəhd et — yüklənmiş hissə saxlanılıb. | Warning banner. |
| `exam_download_verify_failed` | Bəzi fayllar zədələnib. Yenidən cəhd et. | Warning banner (hash mismatch). |
| `exam_download_storage` | Cihazda kifayət qədər yer yoxdur. %1$s lazımdır. | `%1$s` = `exam_size_mb`. |
| `exam_download_needs_internet` | Yükləmək üçün internet lazımdır. | Banner / snackbar. |
| `exam_download_done_cd` | Sınaq yükləndi. Başlamağa hazırdır. | Polite announcement. |
| `exam_download_notif` | Sınaq yüklənir | Worker notification title, only if Android requires one. |
| `exam_info_ready_hint` | Hazırsan? Başlayanda vaxt işləməyə başlayacaq. | `wrap`. |
| `exam_info_notif_off_hint` | Bildirişlər söndürülüb: tətbiq arxa planda olanda vaxt xatırlatması gəlməyəcək. | OQ-D45. Shown above "Başla" when POST_NOTIFICATIONS, app notifications or the `exam` channel are off. `wrap` (bodySmall 458 / 595dp: 2 lines at 1.0 and 1.3). |
| `exam_info_start` | Başla | `btn` primary. labelLarge 40 / 52dp (280): fits |
| `exam_info_running_hint` | Vaxt gedir: %1$s qalıb. | bodySmall 144 / 187dp (328): fits — measured "Vaxt gedir: 42 dəq qalıb." |
| `exam_info_resume` | Davam et | `btn`. labelLarge 70 / 91dp (280): fits |
| `exam_info_resume_download` | Davam etmək üçün sınağı bu telefona yüklə. Vaxt gedir: %1$s qalıb. | `wrap`. |
| `exam_info_retake_hint` | Bu sınağı artıq vermisən. Yeni nəticə «Təkrar cəhd» kimi qeyd olunacaq. | `wrap`. |
| `exam_info_retake` | Yenidən başla | `btn`. labelLarge 103 / 134dp (280): fits |
| `exam_info_last_result` | Son nəticəyə bax | `btn` secondary. labelLarge 127 / 165dp (280): fits |
| `exam_variant_updated` | Bu sınaq yenilənib. Başlamaq üçün yenidən yüklə. | Warning banner. |
| `exam_variant_unavailable` | Bu sınaq artıq mövcud deyil | State view title. headlineSmall 296 / 385dp (328): wraps at 1.3 |
| `exam_start_confirm_title` | Sınağa başlayırsan? | Dialog title. headlineSmall 213 / 277dp (280): fits |
| `exam_start_confirm_body` | %1$d dəqiqə vaxtın olacaq. Vaxtı dayandırmaq olmur — tətbiqdən çıxsan da sınaq davam edir. | Dialog body. |
| `exam_start_confirm_skip` | Dinləmə hissəsi keçiləcək. | Appended to the body. |
| `exam_start_confirm` | Başla | `dbtn`. labelLarge 40 / 52dp (100): fits |
| `exam_start_other_running_title` | Əvvəlcə davam edən sınağı bitir | Dialog title; 2 lines (allowed). headlineSmall 336 / 437dp (280): **2 lines at 1.0** |
| `exam_start_other_running_body` | «%1$s» hələ davam edir: %2$s qalıb. |  |
| `exam_start_other_running_open` | Ona keç | `dbtn`. labelLarge 61 / 79dp (100): fits |
| `exam_start_needs_internet` | Sınağı başlatmaq üçün internet lazımdır. | Snackbar / banner. EXAM-04 wording kept. |
| `exam_start_rate_limited` | Bu gün çox sınaq başlatmısan. Sabah yenidən cəhd et. | Snackbar (429, 10 per Baku day). |
| `exam_start_failed` | Sınağı başlatmaq alınmadı. | Snackbar + `common_retry`. |

#### Attempt (`exam-attempt.md`, `components.md` §39 … §43)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_close_cd` | Sınaqdan çıx | Close icon. |
| `exam_progress` | %1$d / %2$d | Header, tabular. titleMedium 57 / 74dp (143): fits — measured "30 / 30" |
| `exam_progress_cd` | Tapşırıq %1$d, cəmi %2$d. %3$s | `%3$s` = section name. |
| `exam_timer_cd` | Qalan vaxt: %1$s | `%1$s` = `exam_duration_cd` or `exam_seconds_cd`. |
| `exam_grid_cd` | Bütün tapşırıqlar | Grid icon. |
| `exam_offline_banner` | Oflayn — cavablar telefonda saxlanılır | Compact banner; EXAM-13 wording. 1 line at 1.0, 2 at 1.3. bodyMedium 251 / 327dp (288): wraps at 1.3 |
| `exam_task_label` | Tapşırıq %1$d · %2$s | `%2$s` = type word. labelMedium 123 / 160dp (180): fits — measured "Tapşırıq 24 · Qapalı" |
| `exam_task_type_closed` | Qapalı |  |
| `exam_task_type_written` | Yazılı |  |
| `exam_flag` | İşarələ | Flag toggle (EXAM-12). labelLarge 49 / 64dp (120): fits |
| `exam_flagged` | İşarələnib | labelLarge 72 / 94dp (120): fits |
| `exam_flag_on_cd` | İşarələndi | Announcement. |
| `exam_flag_off_cd` | İşarə götürüldü | Announcement. |
| `exam_option_cd` | %1$s variantı: %2$s | "A variantı: …" (EXAM-22). Letters avoid ordinal suffixes. |
| `exam_option_selected_cd` | seçilib | State description. |
| `exam_clear_answer` | Cavabı sil | Text button (EXAM-06). labelLarge 70 / 91dp (304): fits |
| `exam_tab_task` | Tapşırıq | Segment (label budget 114dp). labelLarge 58 / 75dp (114): fits |
| `exam_tab_passage` | Mətn | Segment. labelLarge 38 / 49dp (114): fits |
| `exam_passage_overline` | OXU MƏTNİ | overline 76 / 99dp (328): fits |
| `exam_paragraph_cd` | Abzas %1$d | Paragraph number (no ordinal suffix). |
| `exam_marked_word_cd` | altından xətt çəkilmiş söz: %1$s | EXAM-09. |
| `exam_gap_cd` | boşluq | Gap token. |
| `exam_written_label` | Cavabın | Label above the field. labelMedium 53 / 69dp (296): fits |
| `exam_written_placeholder` | Cavabını bura yaz… | bodyLarge 143 / 186dp (296): fits |
| `exam_written_supporting` | Yazmaq məcburi deyil: sınaqdan sonra nümunəvi cavabla özün müqayisə edəcəksən. | `wrap` (2–3 lines). |
| `exam_written_counter` | %1$d / %2$d | "0 / 1000", tabular. |
| `exam_written_limit` | Ən çox %1$d simvol yazmaq olar. | Error supporting at the limit. |
| `exam_written_solution_hint` | Həlli kağızda yaz; sınaqdan sonra nümunəvi həll ilə müqayisə edəcəksən. | Phase 2 `solution` tasks (EXAM-11 wording). |
| `exam_audio_title` | Dinləmə mətni | Player title (budget 204dp). titleSmall 99 / 128dp (204): fits |
| `exam_audio_play` | Dinlə | Button tooltip / label (EXAM-10). |
| `exam_audio_resume` | Davam et |  |
| `exam_audio_play_cd` | Dinlə. İstifadə olunub: %1$d / %2$d | Replaces EXAM-10 "2 dəfədən 1-i" (ordinal suffix changes with the number). |
| `exam_audio_pause_cd` | Fasilə ver |  |
| `exam_audio_plays_left` | %1$d dəfə qalıb | `plurals`; EXAM-10 "1 dəfə qalıb". bodyMedium 80 / 103dp (114): fits — measured "2 dəfə qalıb" |
| `exam_audio_play_n` | Dinləmə %1$d / %2$d | bodyMedium 84 / 109dp (114): fits — measured "Dinləmə 1 / 2" |
| `exam_audio_no_plays` | Dinləmə imkanı bitdi | With the elapsed time the row is 136 + 68dp at 1.0 (fits 228dp); at 1.3 the plays text moves under the time. bodyMedium 136 / 177dp (228): fits |
| `exam_audio_counts_after` | %1$d saniyədən sonra sayılacaq | Caption during the first 10 s of a play. bodySmall 170 / 222dp (296): fits — measured "10 saniyədən sonra sayılacaq" |
| `exam_audio_elapsed` | %1$s / %2$s | "1:12 / 3:05", tabular. |
| `exam_audio_interrupted` | Audio fasiləyə verildi. | Caption after an automatic pause. |
| `exam_audio_preparing` | Audio hazırlanır… |  |
| `exam_audio_redownloading` | Audio yenidən yüklənir… |  |
| `exam_audio_failed` | Audio yüklənmədi. Dinləmələrin sayılmayıb — yenidən cəhd et. | Warning banner in the player (EXAM-10). |
| `exam_audio_failed_offline` | Audio yüklənmədi. İnternetə qoşul və yenidən cəhd et. |  |
| `exam_audio_restored` | Tətbiq bağlanmışdı. Dinləmə dayandığı yerdən davam edəcək. | OQ-D43. |
| `exam_audio_volume_hint` | Səsi artır və ya qulaqcıq tax. | EXAM-10 wording. |
| `exam_audio_skipped` | Dinləmə hissəsini keçmisən. Bu tapşırıqlar təxmini bala daxil edilmir. | Exam note. |
| `exam_audio_play_review_cd` | Dinlə | Review player (§42a) play button. |
| `exam_audio_replay_cd` | Yenidən dinlə | Review player button at the end of the file. labelLarge 100 / 129dp (cd + tooltip). |
| `exam_audio_seek_cd` | Audio mövqeyi | Review player slider. |
| `exam_audio_back10_cd` | 10 saniyə geri | Icon button `replay_10`. |
| `exam_audio_forward10_cd` | 10 saniyə irəli | Icon button `forward_10`. |
| `exam_grid_title` | Tapşırıqlar | Sheet title. titleLarge 100 / 130dp (180): fits |
| `exam_grid_summary` | Cavablanıb: %1$d · Cavabsız: %2$d · İşarələnib: %3$d | `wrap` (2 lines at 1.3). bodyMedium 301 / 391dp (328): wraps at 1.3 — measured "Cavablanıb: 30 · Cavabsız: 30 · İşarələnib: 30" |
| `exam_grid_legend_answered` | Cavablanıb | Legend (bodySmall). |
| `exam_grid_legend_unanswered` | Cavabsız |  |
| `exam_grid_legend_flagged` | İşarələnib |  |
| `exam_grid_legend_current` | Hazırkı |  |
| `exam_grid_cell_cd` | Tapşırıq %1$d, %2$s | `%2$s` = states joined with ", ". |
| `exam_grid_state_answered_cd` | cavablanıb |  |
| `exam_grid_state_written_cd` | mətn yazılıb |  |
| `exam_grid_state_unanswered_cd` | cavabsız |  |
| `exam_grid_state_flagged_cd` | işarələnib |  |
| `exam_grid_state_current_cd` | hazırda açıqdır |  |
| `exam_grid_skipped` | Keçildi | Instead of the listening cells. |
| `exam_prev` | Əvvəlki | Half pill with icon (label 84dp). labelLarge 55 / 72dp (84): fits |
| `exam_next` | Növbəti | Half pill with icon. labelLarge 59 / 77dp (84): fits |
| `exam_finish` | Sınağı bitir | Half pill without icon (110dp), sheet button, dialog action. labelLarge 77 / 100dp (110): fits |
| `exam_leave_title` | Sınaqdan çıxırsan? | Dialog title. headlineSmall 204 / 265dp (280): fits |
| `exam_leave_body` | Vaxt dayanmır: sınaq %1$s sonra avtomatik bitəcək. Cavabların saxlanılır. | `%1$s` = `exam_duration_min`. |
| `exam_leave_later` | Sonra davam et | Stacked dialog action. labelLarge 115 / 149dp (240): fits |
| `exam_submit_title` | Sınağı bitirirsən? | Dialog title. headlineSmall 185 / 240dp (280): fits |
| `exam_submit_counts` | Cavabsız: %1$d · İşarələnib: %2$d | Replaces EXAM-14 "Cavabsız: 7, İşarələnmiş: 3". titleSmall 194 / 252dp (280): fits — measured "Cavabsız: 30 · İşarələnib: 30" |
| `exam_submit_all_answered` | Bütün tapşırıqlara cavab vermisən. |  |
| `exam_submit_body` | Bitirəndən sonra cavabları dəyişə bilməyəcəksən. |  |
| `exam_submit_confirm` | Bitir | `dbtn`. labelLarge 30 / 39dp (100): fits |
| `exam_time_notice` | %1$d dəqiqə qaldı | `plurals`; snackbar at 30 / 10 / 1 (EXAM-04 wording). |
| `exam_time_up_title` | Vaxt bitdi | headlineSmall 104 / 135dp (328): fits |
| `exam_time_up_body` | Sınaq avtomatik bitirildi. Cavabların göndərilir… |  |
| `exam_submit_offline_title` | Nəticə internet olanda hesablanacaq | State title; 2 lines (allowed). EXAM-14 wording. headlineSmall 387 / 503dp (328): **2 lines at 1.0** |
| `exam_submit_offline_body` | Cavabların telefonda saxlanılıb. İnternet qayıdanda avtomatik göndərəcəyik. |  |
| `exam_submit_late_window` | %1$d saat ərzində internetə qoşulmasan, yalnız əvvəl göndərilmiş cavablar sayılacaq. | Value 24 (OQ-118). |
| `exam_submitting` | Cavablar göndərilir… |  |
| `exam_attempt_loading` | Sınaq açılır… |  |
| `exam_merged_other_device` | Başqa cihazdakı cavablar əlavə olundu. | Snackbar. |
| `exam_package_missing_title` | Sınağın faylları tapılmadı | headlineSmall 270 / 351dp (328): wraps at 1.3 |
| `exam_package_missing_body` | Davam etmək üçün yenidən yüklə. Vaxt gedir: %1$s qalıb. |  |
| `exam_attempt_closed_title` | Bu sınaq artıq bitib | headlineSmall 203 / 264dp (280): fits |
| `exam_attempt_closed_body` | Sınaq başqa cihazda bitirilib və ya vaxtı keçib. |  |
| `exam_open_result` | Nəticəyə bax | `dbtn`. labelLarge 98 / 127dp (140): fits |

#### Self-assessment (`exam-self-assessment.md`, `components.md` §44)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_assess_title` | Qiymətləndirmə | `bar` (progress is in the content, not the bar). titleLarge 159 / 207dp (264): fits |
| `exam_assess_close_cd` | Qiymətləndirməni bağla |  |
| `exam_assess_progress` | %1$d / %2$d | End of the task line, titleSmall tabular. |
| `exam_assess_progress_cd` | Yazılı tapşırıq %1$d, cəmi %2$d |  |
| `exam_assess_intro` | Cavabını nümunə ilə müqayisə et və meyarlara görə qiymət seç. Özünə dürüst qiymət ver — bu bal yalnız sənin üçündür. | Exam note, first page. |
| `exam_assess_task_label` | Tapşırıq %1$d · %2$s | `%2$s` = section name. labelMedium 216 / 281dp (328): fits — measured "Tapşırıq 24 · Qrammatika və lüğət" |
| `exam_assess_show_passage` | Mətnə bax | Text button. labelLarge 78 / 101dp (280): fits |
| `exam_assess_show_transcript` | Transkriptə bax | Text button. labelLarge 114 / 148dp (280): fits |
| `exam_assess_your_answer` | Sənin cavabın |  |
| `exam_assess_no_answer` | Cavab yazılmayıb — kağızda həll etmisənsə, onunla müqayisə et. | EXAM-16 wording. |
| `exam_assess_model_answer` | Nümunəvi cavab |  |
| `exam_assess_model_answer_n` | Nümunə %1$d | Second and further model answers. |
| `exam_assess_criteria` | Meyarlar |  |
| `exam_assess_pick` | Qiymətin | Picker label / group label. |
| `exam_assess_pick_mark_cd` | bu qiyməti seç | Click label of a criterion row. |
| `exam_assess_points` | %1$s / %2$d xal | "1,33 / 2 xal" (2 · mark, two decimals only for thirds). |
| `exam_mark_0` | 0 | Tile label (titleLarge sans). titleLarge 14 / 18dp (51): fits |
| `exam_mark_1_3` | ⅓ | U+2153 — sans subset must include it (tokens 3.1.0). Fallback if the glyph is missing: "1/3". titleLarge 13 / 17dp (51): fits; glyph ⅓ not in the shipped subset (tokens 3.1.0 adds it) |
| `exam_mark_1_2` | ½ | U+00BD. titleLarge 16 / 21dp (51): fits |
| `exam_mark_2_3` | ⅔ | U+2154 — as ⅓. titleLarge 13 / 17dp (51): fits; glyph ⅔ not in the shipped subset (tokens 3.1.0 adds it) |
| `exam_mark_1` | 1 | titleLarge 8 / 10dp (51): fits |
| `exam_mark_0_cd` | sıfır |  |
| `exam_mark_1_3_cd` | üçdə bir |  |
| `exam_mark_1_2_cd` | yarım |  |
| `exam_mark_2_3_cd` | üçdə iki |  |
| `exam_mark_1_cd` | bir, tam qiymət |  |
| `exam_assess_finish` | Qiymətləndirməni bitir | `btn` (full width on the last page). labelLarge 166 / 216dp (280): fits |
| `exam_assess_saved_offline` | Qiymətlər telefonda saxlanıldı, internet olanda göndəriləcək. | Banner. |
| `exam_transcript_title` | Transkript | Sheet / card title. titleMedium 79 / 103dp (260): fits |

#### Result (`exam-result.md`, `components.md` §45)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_result_title` | Nəticə | `bar`. titleLarge 65 / 84dp (264): fits |
| `exam_result_overline` | TƏXMİNİ BAL | EXAM-15 label, authored in capitals. overline 86 / 112dp (328): fits |
| `exam_result_max` |  / %1$s | After the serif score; leading space (`\u0020/ %1$s`). "100" or a reduced maximum "83,8" (OQ-D42). headlineSmall 67 / 87dp (197): fits — measured " / 83,8" |
| `exam_result_not_official` | Bu, rəsmi DİM nəticəsi deyil. | EXAM-15 wording, always under the score. bodyMedium 187 / 243dp (306): fits |
| `exam_result_score_cd` | Təxmini bal: %1$s, %2$s baldan. Bu, rəsmi DİM nəticəsi deyil. |  |
| `exam_result_finalized` | Sınağı bitirmədin, ona görə server onu yalnız göndərilmiş cavablarla yekunlaşdırdı. | Exam note. |
| `exam_result_auto_submitted` | Vaxt bitdi — sınaq avtomatik bitirildi. | Exam note. |
| `exam_result_listening_failed` | Audio işləmədi, ona görə dinləmə tapşırıqları bala daxil edilməyib. Maksimum bal: %1$s. | EXAM-10: never a silent 0. |
| `exam_result_listening_skipped` | Dinləmə hissəsini keçdin, ona görə bu tapşırıqlar bala daxil edilməyib. Maksimum bal: %1$s. | OQ-119 / OQ-D42. |
| `exam_split_auto` | Avtomatik yoxlanılıb | Legend label (OQ-112 wording). bodyMedium 136 / 176dp (200): fits |
| `exam_split_self` | Özün qiymətləndirdin | Legend label (OQ-112 wording). bodyMedium 145 / 189dp (200): fits |
| `exam_split_unassessed_count` | Qiymətləndirilməyib: %1$d tapşırıq | Legend row when written tasks are open. bodyMedium 207 / 269dp (300): fits — measured "Qiymətləndirilməyib: 7 tapşırıq" |
| `exam_split_value` | %1$s / %2$s | "46,9 / 62,2" titleSmall tabular, legend end (≤ 98dp at 1.3). |
| `exam_split_cd` | Avtomatik yoxlanılıb: %2$s baldan %1$s. Özün qiymətləndirdin: %4$s baldan %3$s. |  |
| `exam_result_assess_title` | Yazılı tapşırıqları qiymətləndir | Card title; wraps at 1.3 (allowed). EXAM-16 wording without the count (count is on the button). titleMedium 229 / 298dp (264): wraps at 1.3 |
| `exam_result_assess_body` | Qiymətləndirməsən, %1$d yazılı tapşırıq 0 sayılır. | `wrap`. |
| `exam_result_assess_cta` | Qiymətləndir (%1$d) | `btn` primary. labelLarge 120 / 156dp (248): fits — measured "Qiymətləndir (7)" |
| `exam_result_edit_marks` | Qiymətləri dəyiş | Text button (OQ-D47). labelLarge 121 / 157dp (304): fits |
| `exam_result_sections_overline` | BÖLMƏLƏR | overline 76 / 98dp (328): fits |
| `exam_result_closed` | Qapalı: %1$d / %2$d düzgün | Section supporting. bodyMedium 144 / 187dp (296): fits — measured "Qapalı: 16 / 16 düzgün" |
| `exam_result_written` | Yazılı: %1$s / %2$d | "Yazılı: 1,7 / 3" (sum of marks). bodyMedium 81 / 106dp (296): fits — measured "Yazılı: 2,7 / 4" |
| `exam_result_written_unassessed` | Yazılı: qiymətləndirilməyib | bodyMedium 176 / 228dp (296): fits |
| `exam_result_section_excluded` | Bala daxil edilməyib | bodyMedium 134 / 174dp (296): fits |
| `exam_result_closed_summary` | Qapalı tapşırıqlar: %1$d düzgün · %2$d səhv · %3$d cavabsız | `wrap` (2 lines at 1.3). bodyMedium 318 / 413dp (328): wraps at 1.3 — measured "Qapalı tapşırıqlar: 18 düzgün · 4 səhv · 1 cavabsız" |
| `exam_result_review` | Təhlilə bax | `btn`. labelLarge 78 / 101dp (280): fits |
| `exam_result_back` | Sınaqlara qayıt | `btn` secondary (same text as `exam_back_to_catalogue`, separate key per screen). labelLarge 109 / 142dp (280): fits |
| `exam_result_updated` | Təxmini bal yeniləndi | Snackbar. |
| `exam_result_error` | Nəticəni yükləmək alınmadı. | State view title. headlineSmall 305 / 397dp (328): wraps at 1.3 |

#### Review (`exam-review.md`)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `exam_review_title` | Təhlil | `bar` (EXAM-18 "Təhlil"). titleLarge 50 / 65dp (264): fits |
| `exam_filter_all` | Hamısı | Filter chip. labelLarge 49 / 63dp (200): fits |
| `exam_filter_wrong` | Səhvlər | labelLarge 56 / 72dp (200): fits |
| `exam_filter_unanswered` | Cavabsızlar | labelLarge 85 / 111dp (200): fits |
| `exam_filter_flagged` | İşarələnmişlər | labelLarge 103 / 133dp (200): fits |
| `exam_filter_written` | Yazılılar | labelLarge 55 / 72dp (200): fits |
| `exam_filter_count_cd` | %1$s, %2$d tapşırıq |  |
| `exam_review_passage` | Oxu mətni | Card title. titleMedium 81 / 105dp (264): fits |
| `exam_review_show_passage` | Mətni göstər | Text button. labelLarge 94 / 122dp (264): fits |
| `exam_review_show_transcript` | Transkripti göstər | Text button. labelLarge 130 / 169dp (264): fits |
| `exam_review_hide` | Gizlət | Text button. labelLarge 43 / 56dp (264): fits |
| `exam_review_excluded_note` | Bu dinləmə tapşırıqları bala daxil edilməyib. |  |
| `exam_review_task_label` | Tapşırıq %1$d · %2$s | `%2$s` = type word; outcome word at the end of the row. labelMedium 123 / 160dp (200): fits — measured "Tapşırıq 24 · Qapalı" |
| `exam_review_flagged_cd` | işarələmişdin |  |
| `exam_review_outcome_blank` | Cavabsız | Outcome word. labelMedium 59 / 76dp (120): fits |
| `exam_review_self_mark` | Qiymətin: %1$s | labelMedium 76 / 99dp (140): fits — measured "Qiymətin: ⅔"; glyph ⅔ not in the shipped subset (tokens 3.1.0 adds it) |
| `exam_review_unassessed` | Qiymətləndirilməyib | Outcome word for written tasks; the task line wraps first. labelMedium 133 / 173dp (140): wraps at 1.3 |
| `exam_review_your_answer` | Sənin cavabın |  |
| `exam_review_your_answer_correct` | Sənin cavabın — düzgün |  |
| `exam_review_correct_answer` | Düzgün cavab |  |
| `exam_review_not_answered` | Cavab verilməyib |  |
| `exam_review_show_criteria` | Meyarları göstər | Text button. labelLarge 120 / 156dp (264): fits |
| `exam_review_assess` | Qiymətləndir | Text button. labelLarge 97 / 127dp (264): fits |
| `exam_review_change_mark` | Qiyməti dəyiş | Text button (OQ-D47). labelLarge 102 / 133dp (264): fits |
| `exam_review_explanation` | İzah |  |
| `exam_review_corrected` | Bu tapşırıq sonradan düzəldilib. Balın dəyişməyib. | EXAM-15. |
| `exam_review_excluded` | Bala daxil edilməyib |  |
| `exam_review_filter_empty` | Bu filtrdə tapşırıq yoxdur. |  |
| `exam_review_error` | Təhlili yükləmək alınmadı. | State view title. headlineSmall 285 / 371dp (328): wraps at 1.3 |
| `exam_review_offline` | Təhlil üçün internet lazımdır. | State view title. headlineSmall 312 / 405dp (328): wraps at 1.3 |

#### Settings and licences (`settings.md`, `licenses.md`)

| Key | Azerbaijani | Notes / width at 1.0 / 1.3 (budget) |
| --- | --- | --- |
| `settings_exams_about` | Sınaqlar haqqında | Info row headline (exams flag only); supporting = `exam_disclaimer`. titleMedium 141 / 183dp (264): fits |
| `settings_licenses_section_voices` | DİNLƏMƏ SƏSLƏRİ | Overline (§35a). overline 125 / 162dp (328): fits |
| `settings_licenses_voice_supporting` | Piper TTS səsi · %1$s | `%1$s` = licence ("CC BY 4.0") or `settings_licenses_public_domain`. bodyMedium 215 / 279dp (264): wraps at 1.3 — measured "Piper TTS səsi · ictimai mülkiyyət" |
| `settings_licenses_public_domain` | ictimai mülkiyyət |  |


#### Local notification (OQ-D45, decided 2026-11-21; `exam-attempt.md` I)

Android strings (the notification is local, not a server push). Text rules of
`notifications.md` C: no exclamation marks, no emoji, no false urgency.

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `notif_exam_time_left_title` | %1$d dəqiqə qalıb | `plurals`; value = whole minutes left when posted (normally 10, never below 5). 15 chars. |
| `notif_exam_time_left_body` | «%1$s» davam edir. Cavablarını yoxlamağa qayıt. | `%1$s` = `exam_variant_title`; 46 chars (≤ 90). |
| `notif_channel_exam_name` | Sınaqlar | Android channel name. |
| `notif_channel_exam_description` | Sınaq zamanı tətbiq arxa planda olanda vaxtın azaldığını xatırladırıq. | Android channel description. |

#### Backend i18n — exam content (server `{"az": …}`, not `strings.xml`)

Names below follow EXAM-25; content-writer / backend-dev store them with the
exam template. Overlines are authored in capitals in the bank.

| Field | Azerbaijani | Notes |
| --- | --- | --- |
| Exam type title | Buraxılış — İngilis dili (DİM formatında) | headlineSmall 428dp → 2 lines at 1.0 and 1.3 (allowed); catalogue, Play listing wording per OQ-130 |
| Exam type overline | BURAXILIŞ · İNGİLİS DİLİ | overline 162 / 211dp (328): fits |
| Subject name | İngilis dili | history row, result line |
| Section names | Dinləmə · Qrammatika və lüğət · Oxu | header line 2 (157 / 143dp): "Qrammatika və lüğət" 136 / 176dp → 2 lines at 1.3, header reserves 2 lines for the whole attempt (`exam-attempt.md` A) |
| Phase 2 (indicative) | Buraxılış sınağı №3 (DİM formatında) | EXAM-25 example; the app shows "Sınaq №3" separately, so the server title never repeats the number |

## Truncation risks

Estimated with the method above. "Used" = which form the specs use.

| Key / text | Component | Width at 1.0 / 1.3 | Budget | Risk and decision |
| --- | --- | --- | --- | --- |
| `settings_licenses` "Açıq mənbə lisenziyaları" as app bar title | `bar` (titleLarge 22sp) | ~300 / ~390dp | 264dp | **Truncates.** App bar uses `settings_licenses_title` "Lisenziyalar"; the full text is only a list row (bodyLarge, ~210 / ~270dp, may wrap to 2 lines at 1.3, allowed). |
| "Səhvlərin təhlili" as app bar title | `bar` | ~215 / ~280dp | 264dp | **Truncates at 1.3.** Review screen app bar uses `quiz_review_title` "Səhvlər"; the full term stays on the result-screen entry card (`row`, 2 lines allowed). |
| `auth_reset_title` "Şifrəni bərpa et" | `bar` | ~195 / ~255dp | 264dp | Fits, tight. Specs put screen titles in the content as headlineSmall (wraps), never in the app bar, for all auth screens. |
| `auth_reset_send_countdown` "Yenidən göndər (59 san.)" | `btn` | ~175 / ~230dp | 280dp | Fits. If a later language is longer, drop "san." → "Yenidən göndər (59)". |
| `auth_verify_resend_countdown` in the banner | banner `tbtn` | ~165 / ~215dp | 296dp (actions row) | Fits; the banner puts actions on their own row under the text. |
| `settings_delete_confirm_button` "Hesabı həmişəlik sil" | `btn` | ~155 / ~205dp | 280dp | Fits. |
| `auth_google_sign_in` "Google ilə daxil ol" | `btn` + icon | ~145 / ~190dp | 254dp | Fits. |
| Theme options "Sistem / İşıqlı / Qaranlıq" | segmented button (3 segments) | "Qaranlıq" ~70 / ~90dp | ~60dp per segment | **Truncates.** Do not use a segmented button; specs use a radio list. |
| Category names from the server, single long word (e.g. "Kinematoqrafiya") | `cat` (titleMedium) | ~140 / ~180dp | 200dp | Fits in a full-width card. A 2-column grid (~126dp) would break words like this at 1.3, so home uses full-width cards. Server-side rule proposed: category names ≤ 20 chars. |
| Display name (2–20 chars) in home header | titleMedium, 1 line | up to ~190 / ~245dp | ~200dp | May truncate at 1.3 with wide letters: ellipsis at the end is accepted by HOME-04. |
| Dialog buttons ("Ləğv et", "Davam et", "Çıx", "Sil") | `dbtn` | ≤ ~75 / ~95dp | ~100dp | Fit. Compose `AlertDialog` stacks buttons automatically if they do not fit. |
| Opponent name in the duel player panel | `labelLarge` next to a 40dp avatar in a ~160dp panel | ~88dp text: ~11 chars at 1.0, ~8 at 1.3 | 88dp | **Truncates** long names (ellipsis). Accepted by DUEL-05; the full name is on the intro (one row per player, ~240dp) and on the result screen (2 lines). |
| Own name | panel | — | — | Not shown in the panel: `duel_you` "Sən" is used instead (fits, and is clearer). |
| Status pills "Bağlantı kəsildi", "Cavabladın", "Göndərilir…" | pill in the panel, labelMedium + 16dp icon | "Bağlantı kəsildi" ~107 / ~139dp | ~100dp | **Wraps** to 2 lines at 1.3 (allowed, never truncated; both panels keep equal height). Shorter verbs chosen on purpose: "Cavabladı" instead of "Cavab verdi", "Düşünür…" instead of "Cavab verir…". |
| Əminlik chip stakes "+600" / "−400" | chip ~101dp wide, labelLarge | ~34 / ~44dp | ~85dp | Fits. Stakes are on two lines inside the chip, never as "+300 / −200" on one line (~96dp at 1.3 would not fit). |
| "Əminlik 3: 2 dəfə qalıb" | caption under the chip row, `wrap` | ~150 / ~195dp | 328dp | Fits. A per-chip caption (~101dp) would truncate, so the count is a badge on the chip plus this full-width caption. |
| Phase name "Əminlik", "Nəticə" | second line of the duel top bar, labelMedium | "Əminlik" ~47 / ~61dp | ~230dp | Fits. |
| `duel_round_label_sudden_death` "Əlavə sual 3 / 3" | top bar centre between two 48dp icons, titleMedium | ~150 / ~195dp | ~230dp | Fits. |
| `duel_result_loss` "Məğlubiyyət" | displaySmall headline | ~231 / ~300dp | 328dp | Fits, tight. Never put it next to an icon in the same row (icon sits above). |
| `duel_search_title` "Rəqib axtarılır…" | headlineSmall | ~224 / ~291dp | 328dp | Fits (wraps if a later language is longer). |
| `duel_search_retry`, `duel_reconnect_button`, `profile_empty_cta` | `btn` | ≤ ~110 / ~145dp | 280dp | Fit. |
| `profile_edit_save` "Yadda saxla" + `common_cancel` | `dbtn` side by side | ~90 / ~117dp | ~100dp | **Stacks** at 1.3 (Compose `AlertDialog` does it automatically). Accepted. |
| Record labels "Məğlubiyyət" | 3 stat tiles in a row (~80dp text) | ~74 / ~96dp | ~80dp | **Would truncate** in tiles, so the record uses full-width rows (`components.md` §23). |
| Calibration row "Əminlik 3" + "18 dəfə · 72% düzgün" | one row | ~260 / ~340dp | 296dp | **Would truncate** on one line at 1.3, so the label and value are stacked (`components.md` §20). |
| History row supporting "Məğlubiyyət · −120 : 300" | `row` | ~180 / ~235dp | ~192dp | Wraps to 2 lines at 1.3 (allowed); the label line moves down. |
| `profile_match_date_year` "12 okt 2025" | trailing column, labelSmall | ~62 / ~80dp | ~72dp | Tight at 1.3; trailing column may grow to 80dp (headline still ≥ 180dp). |
| `home_daily_next_countdown` "Növbəti çağırış: 05:12:33" | finished daily card, titleSmall + 18dp icon | ~207 / ~268dp | 274dp | Fits on 1 line at 1.3 because the white card uses `space.lg` padding (with `space.xl` it would wrap). |
| "Gündəlik çağırış #123" | daily card title | ~250 / ~325dp as one headline | 2 lines | Split by design: overline `daily_title` + `daily_number` on its own line, so it is always ≤ 2 lines. |
| `home_rating` in the v2 home header | — | — | — | **Obsolete in v3** (no home header; the sticker shows the rating). |
| `exam_assess_title` "Qiymətləndirmə" next to a "2 / 7" counter in the app bar | `bar` | 159 / 207dp | ~206dp with the counter | **Would truncate at 1.3**; the counter moved into the content (`exam-self-assessment.md`) |
| Section name "Qrammatika və lüğət" in the exam header | header centre, labelMedium | 136 / 176dp | 157 / 143dp | 2 lines at 1.3; header height fixed per attempt by the longest section name (no jumping) |
| "Qiymətləndirməni bitir" as a half-width pill | half pill | 166 / 216dp | 110dp | **Would truncate**; the last self-assessment page stacks two full-width pills |
| "Sınağı bitir" as a half-width pill | half pill, no icon | 77 / 100dp | 110dp | Fits; no icon on purpose |
| "Təxmini bal: 67,7 · Təkrar cəhd" on one status line | status line | 195 / 253dp | 242dp | **Would truncate at 1.3**; "Təkrar cəhd" is a separate chip |
| Review outcome "Qiymətləndirilməyib" at the end of the task line | labelMedium | 133 / 173dp | ~140dp | Moves under the task label at 1.3 |
| `exam_audio_no_plays` + elapsed time on one row | player row | 136 + 68 / 177 + 88dp | 228dp | Plays text moves under the time at 1.3 |
| "maksimum bal" facts caption | bodySmall, 104dp item | 85 / 110dp | 104dp | 2 lines at 1.3 (allowed) |
| "Sınaq №1" / "⅓" "⅔" with the shipped (pre-3.1.0) font files | — | — | — | **Glyph fallback** (Roboto) until android-dev re-subsets with U+2116, U+2153, U+2154 (`tokens.json` 3.1.0) |
| `progress_level` "Səviyyə 12" chip + mini bar | home header line 3 | ~95 / ~120dp | ~176dp | Fits; the mini bar moves under the chip when < 48dp is left. |
| `settings_notif_daily` "Gündəlik çağırış xatırlatması" | switch row | ~255 / ~330dp | ~232dp | **Wraps to 2 lines** at 1.0 and 1.3 (allowed by `row`, SET-07); switch stays centred. |
| `notif_prompt_allow` "İcazə ver" + `notif_prompt_later` "Sonra" | two buttons in one row of the card | ~140 / ~185dp together | ~296dp | Fit side by side; stack at font scale ≥ 2.0. |
| `home_daily_start` / `home_daily_resume` / `daily_share` | `btn` | ≤ ~70 / ~90dp | 280dp | Fit. |
| `progress_xp_gained` "+1 250 XP" | headlineSmall + 24dp icon | ~110 / ~145dp | 296dp | Fits. |
| Bot opponent in the duel player panel: "Duello Bot" + Bot badge | name slot ~84dp, labelLarge | ~94 / ~123dp + badge 57 / 64dp | 84dp | **Would truncate** both. The panel shows the badge alone in the name slot (`components.md` §16, like "Sən" on the own panel); the full name is on the intro and result. |
| "Duello Bot" + badge, intro opponent row | titleMedium + badge | ~108 + 65 / ~130 + 72dp | 240dp | Fits on one line. |
| "Duello Bot" + badge, history headline | bodyLarge + badge | ~95 + 65 / ~124 + 72dp | 264dp (minus trailing ~80dp ≈ 184dp) | Fits at 1.0; at 1.3 ≈ 196dp vs 184dp: the name ellipsizes by ~1 character before the badge ever does. Accepted; TalkBack reads the full name. |
| Result score column "Duello Bot" + badge | titleSmall, ~130dp column | ~83 + 65 / ~107 + 72dp | 130dp | **Would truncate** side by side; badge is stacked under the name (`duel-result.md`). |
| DUEL-26 "Reytinq dəyişmədi · Bot oyunu" | rating card, titleMedium next to a 20dp icon | ~291 / ~378dp | 268dp | **Does not fit** even at 1.0; replaced by two lines `duel_result_rating_unchanged` + `duel_bot_no_rating_note` (see changes below). |
| `duel_search_bot_hint` | bodyMedium + 20dp icon, full width | ~680 / ~880dp total | 300dp per line | Wraps: 3 lines at 1.0, 3–4 at 1.3 (≤ 96dp); allowed (`wrap`), the search column absorbs it with its flexible spaces. |
| `profile_record_bot` "Bota qarşı: 12 qələbə, 9 məğlubiyyət, 1 heç-heçə" | bodyMedium + 20dp icon in the record card | ~360 / ~470dp | ~264dp | Wraps to 2 lines at 1.0 and 1.3 (allowed). |
| `profile_category_level_cd` row: "Kinematoqrafiya" + "Səviyyə 12" | category row (name column + trailing level) | name ~140 / ~180dp, level ~85 / ~110dp | ~232dp | At 1.3 the level label moves under the bar when the name needs its full column (`progression.md` C). |

## Changes against the requirement proposals

The requirement files propose Azerbaijani text in the "siz" form. Final copy uses
"sən" (above). Meaning is unchanged; the business-analyst may mirror these in the
requirement files when convenient. Examples:

| Requirement proposal | Final copy |
| --- | --- |
| Şifrəni unutmusunuz? | Şifrəni unutmusan? |
| Xəta baş verdi. Yenidən cəhd edin. | Xəta baş verdi. Yenidən cəhd et. |
| Bu e-poçtla artıq hesab var. Daxil olun və ya şifrəni bərpa edin. | Bu e-poçtla artıq hesab var. Daxil ol və ya şifrəni bərpa et. |
| E-poçtunuzu təsdiqləyin | E-poçtunu təsdiqlə |
| Quizdən çıxmaq istəyirsiniz? Bu quiz yarımçıq qalacaq. | Quizdən çıxmaq istəyirsən? / Bu quiz yarımçıq qalacaq. Vaxt bu arada dayanmır. |
| Hesabdan çıxmaq istəyirsiniz? | Hesabdan çıxmaq istəyirsən? |
| Bütün suallara düzgün cavab verdiniz! | Bütün suallara düzgün cavab verdin! |
| Çox sayda quiz başladıldı. Bir az sonra yenidən cəhd edin. | Qısa müddətdə çox quiz başlatdın. Bir az sonra yenidən cəhd et. |
| Hesabınız silindi. | Hesabın silindi. |
| Profiliniz, reytinqiniz və quiz nəticələriniz həmişəlik silinəcək. Bu əməliyyat geri qaytarıla bilməz. | Profilin, reytinqin və quiz nəticələrin həmişəlik silinəcək. Bu əməliyyatı geri qaytarmaq olmaz. |
| Giriş müddəti bitib. Yenidən daxil olun. | Giriş müddəti bitib. Yenidən daxil ol. |
| Kateqoriya quiz-i | quiz / quizi (no hyphen) |
| Rəqib axtarılır... / Qoşulur... | Rəqib axtarılır… / Qoşulur… (single "…") |
| Rəqib tapılmadı. Bir az sonra yenidən cəhd edin. | Title "Rəqib tapılmadı" + body "Hazırda başqa oyunçu axtarmır. Bir az sonra yenidən cəhd et." |
| Nə qədər əminsiniz? | Nə qədər əminsən? |
| 3 — 2 dəfə qalıb / Limit bitdi | Badge "2" on chip 3 + caption "Əminlik 3: 2 dəfə qalıb" / "Əminlik 3 limiti bitdi" |
| Seçiminiz qeydə alındı | Seçimin qeydə alındı |
| Rəqib seçdi / Rəqib cavab verdi | Opponent panel pill "Seçdi" / "Cavabladı" (the panel already names the opponent) |
| Düzgün. Siz +237, rəqib −100. Hesab 537 : 300. | Düzgün. Sən +237, rəqib −100. Hesab 537 : 300. |
| Bu hesabla başqa cihazda duelə qoşuldunuz. | Bu hesabla başqa cihazda duelə qoşuldun. |
| Duel üçün tətbiqi yeniləyin. | Duel üçün tətbiqi yenilə. |
| Duel hazırda əlçatan deyil. Bir az sonra yenidən cəhd edin. | Duel hazırda əlçatan deyil. Bir az sonra yenidən cəhd et. |
| Oyundan çıxmaq istəyirsiniz? Bu məğlubiyyət sayılacaq və reytinqiniz azalacaq. | Oyundan çıxmaq istəyirsən? / Bu, məğlubiyyət sayılacaq və reytinqin azalacaq. Vaxt bu arada dayanmır. |
| Rəqib oyunu tərk etdi. Qələbə sizindir! | Rəqib oyunu tərk etdi. Qələbə sənindir! |
| Ardıcıl 2 raund buraxdığınız üçün oyun başa çatdı. | Ardıcıl 2 raund buraxdığın üçün oyun başa çatdı. |
| Əlavə sualda qalib gəldiniz / uduzdunuz | Əlavə sualda qalib gəldin. / Əlavə sualda uduzdun. |
| Yüksək əminlikdə tez-tez yanılırsınız. | Yüksək əminlikdə tez-tez yanılırsan. Şübhə edəndə 1 seç. |
| Hələ duel oynamamısınız. İlk duelinizi oynayın və Əminlik statistikanızı görün. | Title "Hələ duel oynamamısan" + body "İlk duelini oyna və Əminlik statistikanı gör." |
| Qoşulub: Oktyabr 2026 | Qoşulub: oktyabr 2026 (month lowercase inside a sentence) |
| Əminlik 3: 18 dəfə, 72% düzgün | Label "Əminlik 3" + value "18 dəfə · 72% düzgün" |
| Ad 2–20 simvol olmalıdır. | Ad 2–20 simvol olmalıdır. (`auth_error_name_length`, unchanged) |
| Gündəlik çağırış #12 (one line) | Overline "Gündəlik çağırış" + "#12" (never more than 2 lines) |
| Gündəlik çağırış hazır olanda və seriyan bitmək üzrə olanda xəbər verək? — "Bəli" / "Sonra" | Title "Xatırladaq?" + body "Gündəlik çağırış hazır olanda və seriyan bitmək üzrə olanda sənə xəbər verək." — "İcazə ver" / "Sonra" (the button names the next step: the system dialog) |
| Seriyan təhlükədədir / 7 günlük seriyanı qorumaq üçün bu gün bir oyun oyna. | "7 günlük seriyanı qoru" / "Bu gün bir oyun oyna ki, seriyan davam etsin." (no alarm word in the title, NOTIF-07) |
| Gündəlik çağırış #12 hazırdır / 5 sual səni gözləyir. | Title unchanged; body "5 sual səni gözləyir. Bu gün neçəsini tapacaqsan?" |
| Bildirişlər telefonda söndürülüb (+ Ayarları aç) | Same, as a banner with a full stop; action "Ayarları aç" |
| Seriya qoruyucusu: 7 günlük seriyada qazanılır | "Seriya qoruyucusu 7 günlük seriyada qazanılır" (no colon: it is a sentence, not a value) |
| 15 saniyəyə oyunçu tapılmasa, Duello Bot ilə oynayacaqsan. (OQ-111) | "15 saniyə ərzində oyunçu tapılmasa, Duello Bot ilə oynayacaqsan. İstəmirsənsə, ləğv et." — "ərzində" reads unambiguously as "within"; the second sentence names the opt-out OQ-111 decided (cancel before 0). The seconds are a placeholder from the server. |
| Reytinq dəyişmədi · Bot oyunu (DUEL-26 result line) | Two lines: "Reytinq dəyişmədi" + "Bot oyunu reytinqə təsir etmir." (the one-liner does not fit 360dp, see risks) |
| Bu, məğlubiyyət sayılacaq. Bot oyunu reytinqə təsir etmir. (DUEL-26 leave dialog) | Same + "Vaxt bu arada dayanmır." (as in the real-match dialog) |
| Dinlə, 2 dəfədən 1-i istifadə olunub (EXAM-10) | "Dinlə. İstifadə olunub: 1 / 2" (no number-dependent suffix) |
| Cavabsız: 7, İşarələnmiş: 3. Sınağı bitirmək istəyirsən? (EXAM-14) | Title "Sınağı bitirirsən?" + "Cavabsız: 7 · İşarələnib: 3" + "Bitirəndən sonra cavabları dəyişə bilməyəcəksən." |
| Yazılı tapşırıqları qiymətləndir (7) (EXAM-16) | Card title "Yazılı tapşırıqları qiymətləndir" + button "Qiymətləndir (7)" |
| Əvvəlcə davam edən sınağı bitir — "Davam et" / "Bitir" (EXAM-20) | Same title; actions "Ləğv et" / "Ona keç" (finishing happens in the attempt with its confirmation) |
| Davam edir — 1 saat 12 dəq qalıb (EXAM-01) | Same pattern; Phase 1 shows minutes only ("Davam edir — 42 dəq qalıb") |
| "Mətn" tab (EXAM-09) | Segments "Tapşırıq" / "Mətn" |

### Tone audit (2026-10-17)

Every string above was checked for the "sən" form (imperatives, possessives and
verb endings in the 2nd person singular; no "siz" and no plural "-nız/-niz"
addressing the player). Fixed in this audit:

| Key | Before | After |
| --- | --- | --- |
| `duel_sudden_death_same_time` | Eyni anda cavab verdiniz. Növbəti əlavə sual. | Rəqiblə eyni anda düzgün cavab verdin. Növbəti əlavə sual. |
| `duel_answer_sent` | Cavabın göndərildi. Nəticə hər ikiniz üçün eyni anda açılacaq. | Cavabın göndərildi. Nəticə sənə və rəqibə eyni anda göstəriləcək. |
