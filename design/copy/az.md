# UI copy — Azerbaijani (az)

Owner: `designer`. Source of truth for every user-facing string of the MVP screens
(auth, home, category quiz, settings). `android-dev` copies the keys 1:1 into
`android/app/src/main/res/values/strings.xml`; nothing user-facing is hard-coded.
Terms follow `docs/requirements/glossary.md`; where this file decides final wording
that differs from a proposal in the requirements, this file wins (see "Changes
against the requirement proposals" at the end).

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

Estimate method (Inter, average glyph advance ≈ 0.56 em lowercase, 0.68 em
uppercase, ×1.04 for weight 600), available text width on a 360dp phone with
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
| `common_deleted_player` | Silinmiş oyunçu | Shown for anonymized players (OQ-014). Duel history, later. |

### error_ (shared errors, server error codes)

| Key | Azerbaijani | Notes |
| --- | --- | --- |
| `error_offline` | İnternet bağlantısı yoxdur. Yenidən cəhd et. | `wrap`. Any network failure before a request reached the server. |
| `error_generic` | Xəta baş verdi. Yenidən cəhd et. | `wrap`. 5xx, timeout (15 s), unknown codes. |
| `error_sign_in_expired` | Giriş müddəti bitib. Yenidən daxil ol. | `wrap`. HTTP 401 after one refresh+retry (AUTH-09), snackbar on welcome. |
| `error_category_not_playable` | Bu kateqoriya hazırda əlçatan deyil. | `wrap`. Server codes `category_not_playable` (409) and `category_not_found` (404). |
| `error_rate_limited` | Qısa müddətdə çox quiz başlatdın. Bir az sonra yenidən cəhd et. | `wrap`. Server code `rate_limited` (429, OQ-022). |
| `error_session_expired` | Quizin vaxtı bitdi. | `wrap`. Server code `session_expired` (409); client then opens the result screen. |
| `error_session_closed` | Bu quiz artıq bitib. | `wrap`. Server code `session_closed` (409). |
| `error_already_answered` | Bu suala artıq cavab vermisən. | `wrap`. Server code `already_answered` (409). Normally not shown: the client keeps the stored result. Log only, show if it ever surfaces. |
| `error_quiz_invalid` | Quizdə xəta baş verdi. Yenidən başla. | `wrap`. Server codes `question_not_in_session`, `invalid_option` (400) — client bugs; also logged to Crashlytics. |

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
| `auth_error_name_length` | Ad %1$d–%2$d simvol olmalıdır. | Field error; 2 and 20 (OQ-011). |
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
| `home_rating` | Reytinq: %1$d | Header, labelLarge, 1 line. |
| `home_profile_cd` | %1$s, reytinq %2$d | Content description of the whole header (merged). |
| `home_settings_cd` | Parametrlər | Content description of the settings icon. |
| `home_duel_title` | Duel | Duel card title. Hidden until the duel ships (OQ-020). |
| `home_duel_hint` | Əminliyini sına, reytinq qazan | Duel card supporting text, `wrap` 2 lines. |
| `home_categories_title` | Kateqoriyalar | Section header, titleMedium. |
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
| `quiz_result_score_cd` | %2$d sualdan %1$d düzgün cavab | Content description of the score block. |
| `quiz_result_message_perfect` | Bütün suallara düzgün cavab verdin! | 10 / 10. |
| `quiz_result_message_great` | Çox yaxşı nəticə! | 7–9. |
| `quiz_result_message_good` | Pis deyil! Səhvlərə bax, növbəti dəfə daha yaxşı olacaq. | 4–6. |
| `quiz_result_message_low` | Başlanğıc üçün yaxşıdır. Səhvlərdən öyrən! | 1–3. |
| `quiz_result_message_zero` | Bu dəfə alınmadı, amma hər sual yeni bilikdir. Yenidən cəhd et! | 0 / 10, encouraging. |
| `quiz_result_expired_note` | Vaxt bitdi. Cavab verilməmiş suallar düzgün sayılmadı. | Shown when the session deadline passed. |
| `quiz_result_not_synced` | Nəticə serverə göndərilə bilmədi. İnternet qayıdanda yenidən göndərəcəyik. | Info banner when `finish` failed. |
| `quiz_result_play_again` | Yenidən oyna | `btn`. |
| `quiz_result_home` | Əsas səhifə | `btn` (outlined). |
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
| `settings_section_theme` | Tema | Section header. |
| `settings_theme_system` | Sistem | `row` radio. |
| `settings_theme_system_supporting` | Telefonun ayarına uyğun | Supporting text under "Sistem". |
| `settings_theme_light` | İşıqlı | `row` radio. |
| `settings_theme_dark` | Qaranlıq | `row` radio. |
| `settings_section_language` | Dil | Section header. |
| `settings_language_value` | Azərbaycan dili | `row`, not interactive in MVP. |
| `settings_section_account` | Hesab | Section header. |
| `settings_signed_in_as` | %1$s | Supporting line with the account email (or Google name). 1 line, ellipsis at the end. |
| `settings_sign_out` | Hesabdan çıx | `row`. |
| `settings_sign_out_title` | Hesabdan çıxmaq istəyirsən? | Dialog title. |
| `settings_sign_out_body` | Nəticələrin və reytinqin hesabında qalır. İstədiyin vaxt yenidən daxil ola bilərsən. | Dialog body. |
| `settings_sign_out_confirm` | Çıx | `dbtn`. |
| `settings_delete_account` | Hesabı sil | `row`, error colour. |
| `settings_section_about` | Haqqında | Section header. |
| `settings_version` | Versiya %1$s (%2$d) | `row`, e.g. "Versiya 1.0.0 (12)". |
| `settings_privacy_policy` | Məxfilik siyasəti | `row`, opens Custom Tab. |
| `settings_licenses` | Açıq mənbə lisenziyaları | `row` (fits at 1.3, 24 chars). |
| `settings_licenses_title` | Lisenziyalar | `bar` title of the licences screen (short form). |
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
