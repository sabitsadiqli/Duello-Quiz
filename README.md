# Duello Quiz

Azərbaycan dilində Android quiz tətbiqi: kateqoriya quiz-ləri və real vaxtda 1v1
"Əminlik" duelləri. Layihə Claude Code komandası ilə qurulur: 6 subagent, ortaq
kontekst və MVP spesifikasiyası.

## Struktur (monorepo)

| Qovluq | Nədir |
| --- | --- |
| `android/` | Android tətbiqi (Kotlin, Jetpack Compose). Android Studio-da **bu qovluğu** aç |
| `backend/` | Ktor server (hələ yoxdur) |
| `design/` | Tokenlər, ekran spec-ləri, mətnlər (hələ yoxdur) |
| `infra/`, `.github/workflows/` | Docker, CI/CD, deploy (hələ yoxdur) |
| `content/` | Sual formatı, sxem, nümunələr. Əsl sual bazası ayrıca **private** repodadır |
| `docs/` | Spec, tələblər, API kontraktı |

## Komanda (`.claude/agents/`)

| Agent | Nə edir |
| --- | --- |
| `business-analyst` | User story-lər, qəbul meyarları, açıq suallar, spec-in ardıcıllığı |
| `designer` | Tokenlər, light/dark, ekran spec-ləri, Azərbaycan dilində mətnlər |
| `android-dev` | Compose, modullar, auth, duel klienti |
| `backend-dev` | Ktor, PostgreSQL, duel engine, şəkillər, sual importu |
| `devops` | Docker, GitHub CI, deploy, backup, Play release |
| `content-writer` | Sualların yazılması, mənbələr, izahlar, şəkil lisenziyaları |

Agentləri adı ilə çağırmaq olar, məsələn:
- "business-analyst ilə duel üçün user story-ləri yaz"
- "content-writer ilə Coğrafiya kateqoriyasına 20 sual hazırla"

Əsas sessiya (sən + Claude) tech lead rolundadır. Qaydalar: `CLAUDE.md`.

## Vacib

- Repo **public**-dir: secret-lər (keystore, `google-services.json`, `.env`) və
  əsl suallar heç vaxt bura düşmür. Bax `content/README.md`.
- MVP-də xərc sıfır hədəflənir; şəkillər pulsuz saxlanılır (`docs/mvp-spec.md` §5).
- Açıq suallar və müvəqqəti qərarlar: `docs/requirements/open-questions.md`.
