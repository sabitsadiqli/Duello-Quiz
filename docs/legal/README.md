# docs/legal — public legal pages

Static pages served by GitHub Pages from `docs/` (OQ-015):
`https://sabitsadiqli.github.io/Duello-Quiz/legal/`.

| File | Play Console field | Owner |
| --- | --- | --- |
| `privacy-policy.html` | App content → Privacy policy URL | business-analyst (text), designer (layout) |
| `account-deletion.html` | Data safety → Delete account URL | business-analyst (text), designer (layout) |
| `index.html` | Store listing → Website (optional) | designer |
| `legal.css`, `favicon.svg` | — | designer (`design/screens/web-legal.md`) |

Text status (2026-10-25): written by `business-analyst` in Azerbaijani and English,
tone "sən" (OQ-041). **Not reviewed by a lawyer.** Sabit reviews it before the URLs go
into Play Console.

## Before publishing (checklist)

1. `grep -n '{{' docs/legal/*.html` prints nothing: `{{CONTACT_EMAIL}}` replaced
   by the public contact address (OQ-097, also used in Play Console).
2. `grep -n 'class="placeholder"' docs/legal/*.html` prints nothing.
3. The release build matches the pages:
   - no `com.google.android.gms.permission.AD_ID` in the merged manifest and
     `google_analytics_adid_collection_enabled = false` (OQ-D25, OQ-100);
   - Firebase Analytics data retention set to **2 months**, data-sharing settings and
     Google signals off (OQ-100);
   - no Crashlytics, or the "CRASHLYTICS" blocks in `privacy-policy.html` are
     uncommented and the table below updated (OQ-101).
4. An admin path exists to delete an account requested by email (OQ-102).
5. GitHub Pages is switched on (OQ-D19) and the Android `PRIVACY_POLICY_URL` points to
   `.../Duello-Quiz/legal/privacy-policy.html` (OQ-D18).
6. When anything below changes (new SDK, new table, new retention), update both pages,
   this table and the date at the top of both pages in the same PR.

## Data the app handles (source of the pages)

| Data | Where stored | Retention | Confirmed in |
| --- | --- | --- | --- |
| Email, password, Google profile (name, email, photo URL, Google ID), Firebase UID | Firebase Authentication (email never in our DB) | Until account deletion | `android/core/auth/build.gradle.kts`, `backend/.../auth/Firebase.kt`, `docs/mvp-spec.md` §3 |
| Display name, avatar URL, rating, created/updated time, Firebase UID | `users` | Until account deletion | `V1__initial_schema.sql` |
| Quiz sessions, session questions, answers (option, correct, elapsed ms, time) | `quiz_sessions`, `quiz_session_questions`, `quiz_answers` | Until account deletion (cascade) | `V1__initial_schema.sql` |
| Duel matches, rounds, answers (bet, defaulted, option, correct, answered ms, bonus, points, missed), ratings before/after, forfeit cause | `matches`, `match_rounds`, `match_answers` | Anonymized on deletion (`ON DELETE SET NULL`), kept for opponents | `V1__initial_schema.sql`, `V4__match_forfeit_cause.sql`, OQ-014, OQ-048 |
| Daily attempts and answers | `daily_attempts`, `daily_answers` | Until account deletion (cascade) | `V5__retention.sql` |
| XP total, streak, freeze, category XP, active days, awards (incl. opponent ref.), XP ledger | `user_progress`, `user_category_xp`, `user_active_days`, `progress_awards`, `xp_events` | Until account deletion (cascade); `opponent_id` set to NULL in the opponent's rows | `V5__retention.sql` |
| FCM token, locale, created / last seen | `push_tokens` | Sign-out, invalid, 60 days unused, account deletion | `V5__retention.sql`, `application.conf` `tokenStaleDays = 60` |
| Reminder switches | `notification_settings` | Until account deletion | `V5__retention.sql` |
| Notification send log (type, day, outcome) | `notification_log` | 90 days, or account deletion | `V5__retention.sql`, `application.conf` `logRetentionDays = 90` |
| Daily question sets | `daily_challenges`, `daily_challenge_questions` | Kept (not personal) | `V5__retention.sql` |
| Server logs (time, path, status, internal user ID; IP only in Caddy error entries) | Docker JSON logs on the VM | Size rotation 5 × 20 MB; not in backups | `infra/README.md`, `infra/prod/Caddyfile`, `backend/src/main/resources/logback.xml` |
| Database + media backups | VM disk (Hetzner, Germany) + off-VM copy in Cloudflare R2, EU (OQ-103) | 14 days (script refuses > 30) | `infra/README.md` §10 |
| Analytics events, app-instance ID, device / app info, Analytics user ID = `users.id` | Firebase Analytics | 2 months (to be set, OQ-100) | `android/app/build.gradle.kts`, `docs/requirements/auth.md` §5 |
| Crash reports | — (Crashlytics not in the build yet) | — | `android/gradle/libs.versions.toml` (no Crashlytics) |

## Play Console Data safety summary

Not legal advice. "Collected" = leaves the device. Google Firebase, Hetzner and Cloudflare act as
our service providers, which Play does not count as "sharing". "Optional" in Play's
sense = the user can use the app without it.

General answers:

| Question | Answer |
| --- | --- |
| Does the app collect or share any required user data types? | Yes |
| Is all user data encrypted in transit? | **Yes** (HTTPS/TLS, WSS for the duel) |
| Do you provide a way for users to request that their data be deleted? | **Yes**: in the app ("Parametrlər → Hesabı sil") and `https://sabitsadiqli.github.io/Duello-Quiz/legal/account-deletion.html` |
| Account creation | Yes, username/email + password and Google sign-in ("OAuth") |
| Delete account URL | `https://sabitsadiqli.github.io/Duello-Quiz/legal/account-deletion.html` |
| Data shared with third parties | **None** |
| Ads | **No ads** |
| Independent security review | No |
| Committed to the Families Policy | No — target audience 13+ (OQ-105) |

Data types (everything not listed: **not collected**):

| Play data type | Collected | Shared | Required / optional | Purposes | Encrypted in transit | Deletable |
| --- | --- | --- | --- | --- | --- | --- |
| Personal info → Name | Yes (display name) | No | Required | App functionality, Account management | Yes | Yes (in app / email) |
| Personal info → Email address | Yes (Firebase Auth) | No | Required | App functionality, Account management | Yes | Yes |
| Personal info → User IDs | Yes (Firebase UID, internal account ID, Google account ID on Google sign-in) | No | Required | App functionality, Account management, Analytics | Yes | Yes |
| Personal info → Other info | Yes (Google profile photo **URL**, Google sign-in only) — or skip if Sabit treats it as part of Name/User IDs | No | Optional (Google sign-in only) | App functionality | Yes | Yes |
| App activity → App interactions | Yes (quiz, daily and duel answers and results, rating, XP, streak; Analytics events) | No | Required | App functionality, Analytics, Fraud prevention, security and compliance | Yes | Yes (Analytics part expires after 2 months) |
| App info and performance → Crash logs | **No** today; **Yes** once Crashlytics ships (OQ-101) | No | Required | Analytics | Yes | — |
| App info and performance → Diagnostics | **No** today; **Yes** once Crashlytics ships | No | Required | Analytics | Yes | — |
| Device or other IDs | Yes (Analytics app-instance ID, FCM token / Firebase installation ID). **Not** the Advertising ID (OQ-D25) | No | Required for Analytics; FCM token only with notifications allowed | Analytics, App functionality (notifications) | Yes | Yes (token deleted with the account; Analytics IDs expire after 2 months) |
| Location (approximate, precise) | No (Analytics derives country/city from IP on Google's side and does not store the IP; check Google's per-SDK answers at https://firebase.google.com/docs/android/play-data-disclosure before submitting — Sabit to confirm) | — | — | — | — | — |
| Financial info, Health, Messages, Photos and videos, Audio, Files and docs, Calendar, Contacts, Web browsing, Installed apps | No | — | — | — | — | — |

Notes for the form:
- "Ephemeral processing" does not apply to any type above: all are stored.
- If Crashlytics is added, update the two "App info and performance" rows and the
  "CRASHLYTICS" blocks of `privacy-policy.html` in the same release.
- If the off-VM backup target changes from Cloudflare R2 (OQ-103), update the provider
  in `privacy-policy.html` §5; the form does not change (service provider, not sharing).
