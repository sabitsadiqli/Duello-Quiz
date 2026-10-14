# Open questions

Owner: `business-analyst`. Each item: ID, question, options, recommendation, who decides.

## Open

| ID | Question | Recommendation | Decides |
| --- | --- | --- | --- |
| OQ-005 | Hosting account: Oracle Cloud Always Free (card for verification only) or a paid ~5 €/month VM? | Oracle Always Free; paid VM only if the account cannot be created | Sabit |
| OQ-006 | Domain: free DuckDNS subdomain or a bought domain? | DuckDNS until production release | Sabit |
| OQ-007 | Minimum questions per category before the closed test | 60 reviewed per category (duel uses 7 + up to 3 sudden death per match) | Sabit |
| OQ-010 | Is email verification required for email/password accounts? | Send a verification email, show a banner, do not block play | Sabit |
| OQ-011 | Display name for email/password users (no Google profile) | Ask for a name at sign-up (2–20 chars); never derive it from the email | Sabit |
| OQ-012 | Single-player quiz score formula | Score = number of correct answers (0–10); no time component | Sabit |
| OQ-013 | Categories with fewer than 10 active questions | Server omits them from `GET /categories`; `POST /quiz/sessions` returns 409 | Sabit |
| OQ-014 | Account deletion: what is deleted, what is kept, how long | Delete profile, quiz data and Firebase user; anonymize the user in opponents' matches; immediate, backups roll off within 30 days | Sabit |
| OQ-015 | Where are the privacy policy and the deletion-request page hosted (zero cost)? | GitHub Pages from this public repo (`docs/legal/`) | Sabit |
| OQ-016 | Quiz: how is a question timeout sent, and does the timer pause in the background? | Send `optionId = null`; timer never pauses (wall clock) | Sabit |
| OQ-017 | Quiz: resume after process death or app relaunch? | No resume in the MVP; server finalizes at the deadline | Sabit |
| OQ-018 | Password policy for email sign-up | Minimum 8 characters, no composition rules (Firebase password policy) | Sabit |
| OQ-019 | Same email used with Google and with password | Keep Firebase default "one account per email"; no linking UI in the MVP | Sabit |
| OQ-020 | Duel entry on home before the duel ships | Hide the entry until the duel is released (build flag); no "coming soon" | Sabit |
| OQ-021 | Question selection and repetition across quiz sessions | Random 10 distinct active questions per session; least-recently-seen later | Sabit |
| OQ-022 | Rate limit for starting quiz sessions | 30 sessions per user per hour, then 429 | Sabit |
| OQ-023 | Reveal/explanation time counts against the 230 s session deadline | Auto-advance after 1.5 s; explanations only in the end review | Sabit |

## Details for open items

Added by `business-analyst` on 2026-10-14 while writing `auth.md`, `home.md`,
`quiz.md` and `settings.md`. Each item lists options, trade-offs and the
recommendation; the row above is the short form.

### OQ-010 Email verification
- A. Not required, no email sent. Simplest; typos in the email make password reset
  impossible for those users; throw-away accounts.
- B. Send a verification email, show a banner, do not block play. Catches typos, no
  friction; some unverified accounts remain.
- C. Required before playing. Fewest fake accounts; extra step on first launch, and
  Firebase emails can land in spam → drop-off for a new app.
- Recommendation: B. Blocks AUTH-08 only. Decides: Sabit.

### OQ-011 Display name for email/password users
`docs/mvp-spec.md` §3 creates the profile with a display name, but email accounts
have none, and duel opponents see it.
- A. "Ad" field at sign-up (2–20 chars, trimmed). One more field; real names from
  the start.
- B. Generate "Oyunçu 4821". Zero friction; impersonal, and users cannot change it
  until a profile-edit feature exists.
- C. Use the email local part. Leaks personal data to opponents — not acceptable.
- Recommendation: A, with B as fallback for any account without a name. A rule for
  offensive names is needed later (profile feature). Decides: Sabit.

### OQ-012 Quiz score formula
`docs/mvp-spec.md` §1 lists "score" and "correct/total" but does not define score.
Per OQ-004, `elapsedMs` cannot be used for scoring.
- A. Score = correct count (0–10). Honest and simple; duplicates correct/total.
- B. 100 points per correct answer (0–1000). Feels like the duel; same information.
- C. Server-timed speed bonus. Needs per-question server timestamps; latency makes
  it unfair on weak connections.
- Recommendation: A (show only "7 / 10"). Decides: Sabit.

### OQ-013 Categories with fewer than 10 active questions
- A. Server omits them from `GET /categories`. Users never see a broken category; a
  new category appears only when it is ready.
- B. Return them with `playable = false` and show them disabled ("Tezliklə"). Shows
  the roadmap; disabled tiles look broken and need extra UI states.
- C. Serve fewer than 10 questions. Breaks §1 and the deadline formula.
- Recommendation: A, plus 409 `category_not_playable` from `POST /quiz/sessions` for
  the race. The threshold equals the questions per session (§1). Decides: Sabit.

### OQ-014 Account deletion scope and retention
Google Play requires in-app deletion and a web resource for apps with accounts.
- Always deleted: `users` row, quiz sessions and their answers, the Firebase user.
- Matches — A. Anonymize: keep match rows, show the player as "Silinmiş oyunçu";
  opponents' history and Elo stay consistent. B. Hard-delete all matches of the
  user; simplest privacy story, opponents lose history.
- Timing: immediate in the database; backups keep data until they are overwritten.
- Recommendation: A; immediate deletion; the web page states that backups are
  overwritten within 30 days (devops must keep backup retention ≤ 30 days). The
  backend deletes the Firebase user with the Admin SDK, so no re-authentication on
  the device. Needs `DELETE /v1/me` in the API. Decides: Sabit.

### OQ-015 Privacy policy and deletion page hosting
Play requires a privacy policy URL and a deletion URL; zero cost is a goal.
- A. GitHub Pages from this public repo (`docs/legal/`). Free, versioned.
- B. A page served by Caddy on the backend host. Free; depends on server uptime and
  the domain (OQ-006).
- C. A Google Sites page. Free; not version-controlled.
- Recommendation: A. The text is a legal statement and needs Sabit's review.
  Decides: Sabit.

### OQ-016 Quiz timeout and backgrounding
`POST /quiz/sessions/{id}/answers` requires `optionId`; a timeout has none. The
session deadline is absolute (start + 230 s).
- Timeout — A. Send `optionId = null`; the server stores "not answered" and returns
  `correctOptionId` (one endpoint, immediate reveal). B. Send nothing; the server
  treats missing answers as not answered at finish (no immediate reveal).
- Background — A. Timer keeps running (wall clock): consistent with the absolute
  deadline, limits looking answers up. B. Pause: kinder, but the server deadline
  still runs and pausing invites searching for answers.
- Recommendation: timeout A, background A. Decides: Sabit.

### OQ-017 Resume after process death
- A. No resume. Simple; a quiz killed during a phone call is lost (short, no Elo).
- B. Resume from local state before the deadline. Needs persisted session, answer
  state and per-question start times; little value because the timer keeps running
  anyway (OQ-016).
- Recommendation: A; the server finalizes abandoned sessions after the deadline.
  Decides: Sabit.

### OQ-018 Password policy
- A. Firebase default (6 chars). Weak.
- B. 8+ characters, no composition rules (current NIST guidance).
- C. 8+ with upper/lower/digit/symbol. More friction, little gain.
- Recommendation: B, set in the Firebase password policy (free) and mirrored in the
  client validation. Decides: Sabit.

### OQ-019 Same email with Google and password
With Firebase's default "one account per email", password sign-up fails for an
email that already has a Google account, and a Google sign-in may replace an
unverified password provider for the same email.
- A. Keep the default; show "Bu e-poçtla artıq hesab var" and suggest Google.
  No linking UI.
- B. Build account linking. Better UX for a rare case; more flows and tests.
- Recommendation: A for the MVP. Decides: Sabit.

### OQ-020 Duel entry before the duel ships
The duel comes in build-order step 5, after home.
- A. Hide the entry until the duel is released (build flag). Clean UI.
- B. Show a disabled "Tezliklə" entry. Teases the feature; looks like a dead button.
- Recommendation: A. Decides: Sabit.

### OQ-021 Question selection and repetition
With ~60 questions per category (OQ-007), random selection repeats often.
- A. Random 10 distinct active questions per session. Simplest.
- B. Prefer questions the user has not seen recently (needs per-user answer history,
  which the review needs anyway).
- Note: the quiz returns `correctOptionId`, so frequent quiz players learn answers
  that also appear in duels. Separate quiz/duel pools would halve the bank; not
  recommended for the MVP.
- Recommendation: A now; B after the closed test if repetition is a complaint.
  Decides: Sabit.

### OQ-022 Quiz session rate limit
Each answer reveals the correct option, so a script could harvest the bank.
- A. No limit. Simplest; harvesting is cheap.
- B. 30 sessions per user per hour (a human plays at most ~15 per hour at ~4 min
  each), then 429. Stops scripts, never hits real players.
- Recommendation: B; the number is Sabit's call. Decides: Sabit.

### OQ-023 Reveal and explanation time vs the session deadline
The deadline (10 × 20 s + 30 s grace, `docs/mvp-spec.md` §1) includes the time
between questions. If the user reads an explanation after each answer, ten reveals
can use up the 30 s grace and later questions get cut off.
- A. Auto-advance after a short reveal (1.5 s, correct option highlighted);
  explanations only in the end review. Fits the deadline and §1 ("review of wrong
  answers with explanations").
- B. Tap "Növbəti" after reading the explanation, with a per-question server
  deadline instead of one session deadline. Better learning; changes the decided
  OQ-004 model.
- C. Keep tap-to-continue and raise the grace. Changes a game number.
- Recommendation: A (the designer may tune the reveal within 1–2 s). Decides: Sabit.

## Decided (provisional — set by the tech lead on 2026-10-14, Sabit may change)

| ID | Question | Decision |
| --- | --- | --- |
| OQ-001 | Sudden death when neither player answers correctly | Another sudden-death question, at most 3; still tied → draw (Elo 0.5 each) |
| OQ-002 | "2 missed rounds" → consecutive or total? Elo on forfeit? | 2 in a row; a missed round = no bet and no answer; forfeit = loss for Elo |
| OQ-003 | Does round 7 double the speed bonus? | No. Only bet points (+ and −) are doubled; the speed bonus is never multiplied |
| OQ-004 | Client-reported `elapsedMs` in single-player quiz can be faked | Display only; server enforces one answer per question and a session deadline; single-player never affects Elo |
| OQ-008 | Image storage without cost | Backend's local disk + Caddy, behind `ImageStorage`; S3/R2 only later if needed (`docs/mvp-spec.md` §5) |
| OQ-009 | Where does the question bank live, given the repo is public? | Separate private repo cloned to `content/bank/` |
