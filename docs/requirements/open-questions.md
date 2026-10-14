# Open questions

Owner: `business-analyst`. Each item: ID, question, options, recommendation, who decides.

## Open

| ID | Question | Recommendation | Decides |
| --- | --- | --- | --- |
| OQ-005 | Hosting account: Oracle Cloud Always Free (card for verification only) or a paid ~5 €/month VM? | Oracle Always Free; paid VM only if the account cannot be created | Sabit |
| OQ-006 | Domain: free DuckDNS subdomain or a bought domain? | DuckDNS until production release | Sabit |
| OQ-007 | Minimum questions per category before the closed test | 60 reviewed per category (duel uses 7 + up to 3 sudden death per match) | Sabit |

## Decided (provisional — set by the tech lead on 2026-10-14, Sabit may change)

| ID | Question | Decision |
| --- | --- | --- |
| OQ-001 | Sudden death when neither player answers correctly | Another sudden-death question, at most 3; still tied → draw (Elo 0.5 each) |
| OQ-002 | "2 missed rounds" → consecutive or total? Elo on forfeit? | 2 in a row; a missed round = no bet and no answer; forfeit = loss for Elo |
| OQ-003 | Does round 7 double the speed bonus? | No. Only bet points (+ and −) are doubled; the speed bonus is never multiplied |
| OQ-004 | Client-reported `elapsedMs` in single-player quiz can be faked | Display only; server enforces one answer per question and a session deadline; single-player never affects Elo |
| OQ-008 | Image storage without cost | Backend's local disk + Caddy, behind `ImageStorage`; S3/R2 only later if needed (`docs/mvp-spec.md` §5) |
| OQ-009 | Where does the question bank live, given the repo is public? | Separate private repo cloned to `content/bank/` |
