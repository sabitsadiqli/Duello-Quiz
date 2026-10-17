# Public web pages (Google Play legal pages)

Stories: SET-03 (privacy policy link), SET-06 (web deletion request). Decisions:
OQ-014 (deletion scope), OQ-015 (GitHub Pages from `docs/legal/`).
`docs/mvp-spec.md` §3a. Files: `docs/legal/` (static HTML + one CSS file).
Tokens: `design/tokens.json` (colours copied into `docs/legal/legal.css`).

## Questions and decisions

Open (need Sabit):
- **OQ-D18 — URL mismatch.** The repository is `sabitsadiqli/Duello-Quiz`, so the
  pages live at `https://sabitsadiqli.github.io/Duello-Quiz/legal/...`. The Android
  default `PRIVACY_POLICY_URL` in `android/app/build.gradle.kts` points to
  `.../DuelloQuiz/legal/privacy-policy.html` (no hyphen) and would 404.
  android-dev must change it to `.../Duello-Quiz/legal/privacy-policy.html`.
- **OQ-D19 — GitHub Pages must be switched on** by Sabit: repository Settings →
  Pages → Deploy from a branch → `main` / `/docs`. `docs/.nojekyll` is added so
  GitHub serves the files as they are (no Jekyll build over the Markdown in
  `docs/`). Side effect: every file under `docs/` becomes reachable on the Pages
  site (the repo is public anyway; no secrets live there).
- **OQ-D20 — contact email and developer name** shown on the pages (and in Play
  Console). Which address is public? A dedicated address (e.g. a new Gmail) keeps
  the personal one private.
- **OQ-D21 — English text.** SET-06 asks for Azerbaijani **and English** on the
  deletion page. Both pages have an English section placeholder (`lang="en"`); the
  privacy policy's English part is optional. Remove the section if Sabit decides
  Azerbaijani only.
- **OQ-D22 — minimum age / target audience** for the "Uşaqlar" section must match
  the Play Console "Target audience and content" answer.

Decided:
- No JavaScript, no external requests (no web fonts, no analytics, no CDN): the
  pages load instantly, work behind any firewall and add nothing to the privacy
  policy. Font stack: `Inter` if installed locally, else the system UI font
  (`system-ui`, Roboto, Segoe UI, Noto Sans), all of which cover Azerbaijani Latin.
- Light/dark via `prefers-color-scheme` only (no toggle: would need JS).
- Brand mark is inline SVG with CSS classes, so its colours follow the theme
  exactly like `design/brand/logo-mark-light.svg` / `-dark.svg` (gap ring =
  page background). Favicon = `design/brand/app-icon-full.svg` (fixed colours).
- All links are relative (`privacy-policy.html`, `legal.css`), so the pages work
  under `/Duello-Quiz/legal/`, on a custom domain later, and opened as local files.
- Legal text placeholders: every section the business-analyst writes is a visible
  `.placeholder` box ("Mətn hazırlanır", warning colours) preceded by an HTML
  comment `<!-- TEXT: what goes here -->`. A visible box makes an unfinished page
  impossible to mistake for the final one. Replace the `<p class="placeholder">`
  element with real paragraphs; keep or delete the comment.
- Before the URLs go into Play Console: no `.placeholder` left
  (`grep -n 'class="placeholder"' docs/legal/*.html` must print nothing) and both
  `[tarix]` dates set.

## Purpose

Meet Google Play's requirements (a public privacy policy URL and a public
account-deletion URL that names the app) at zero cost, look like the app, and
read well on a phone.

## Pages and URLs

| File | URL | Use |
| --- | --- | --- |
| `docs/legal/index.html` | `https://sabitsadiqli.github.io/Duello-Quiz/legal/` | Short app intro + links (Play "Website" field if wanted) |
| `docs/legal/privacy-policy.html` | `.../legal/privacy-policy.html` | Play Console privacy policy URL; in-app `settings_privacy_policy` and `auth_privacy_link` |
| `docs/legal/account-deletion.html` | `.../legal/account-deletion.html` | Play Console Data safety → "Delete account URL" |
| `docs/legal/legal.css` | — | Shared styles, token colours |
| `docs/legal/favicon.svg` | — | Copy of `design/brand/app-icon-full.svg` |

## Layout (all pages, top to bottom, 360px first)

Content column: max width 40rem (~70 characters per line, a comfortable reading
measure; wider than the app's `maxContentWidth` because this is long-form text),
centred, side gutter `spacing.layout.screenGutter` (16px), 24px from 600px up.

1. Skip link "Məzmuna keç" (visible on keyboard focus only, `primary` /
   `onPrimary`, pill).
2. **Header** (bottom border `outlineVariant`): brand link = mark 32px + "Duello
   Quiz" (18px, weight 600, `onSurface`) → `index.html`; nav with two pill links
   "Məxfilik siyasəti", "Hesabın silinməsi" (14px weight 600, `onSurfaceVariant`;
   current page `primaryContainer` / `onPrimaryContainer`, `aria-current`). Each
   link ≥ 48px high. At 360px the nav wraps under the brand; never truncates.
3. **Main**, 32px top / 48px bottom:
   - `h1` 28/36 weight 600 (`type.headlineMedium`), wraps.
   - Meta line "Son yenilənmə: …" 14px `onSurfaceVariant` (legal pages).
   - Sections: `h2` 22px (`type.titleLarge`), body 16/24 (`type.bodyLarge`).
   - Links `primary`, underlined (never colour alone).
4. **Footer** (top border `outlineVariant`): links to the other pages, "© 2026
   Duello Quiz" + developer/contact placeholder, 14px `onSurfaceVariant`.

### index.html
Hero (centred): mark 96px (`size.splashLogo`), `h1` "Duello Quiz", lead
`auth_welcome_tagline` text. "Tətbiq haqqında": one sentence + three bullets
(category quizzes, Əminlik duel, rating and statistics). Placeholder comment for
the Play Store link. "Sənədlər": two link cards (`surfaceContainerLow`,
`shape.large`, title `primary`, supporting `onSurfaceVariant`), stacked below
600px, two columns above.

### privacy-policy.html
`h1`, date, intro placeholder, table of contents card (`surfaceContainerLow`,
`shape.large`, numbered anchor links), then 12 numbered sections, each `h2` +
placeholder: Biz kimik, Hansı məlumatları toplayırıq, Məlumatlardan necə istifadə
edirik, Başqa oyunçular nəyi görür, Üçüncü tərəf xidmətləri, Saxlama müddəti,
Təhlükəsizlik, Hesabın və məlumatların silinməsi (fixed sentence with the in-app
path + link to the deletion page, then a placeholder), Uşaqlar, Hüquqların,
Dəyişikliklər, Əlaqə. English summary section at the end (`lang="en"`, top border).

### account-deletion.html
`h1` "Duello Quiz hesabının silinməsi" (names the app, a Play requirement), date,
intro placeholder; "Tətbiqdə hesabı sil" with four numbered steps whose bold UI
labels match `copy/az.md` (`home_settings_cd`, `settings_section_account`,
`settings_delete_account`, `settings_delete_confirm_button`,
`settings_delete_dialog_confirm`); "Tətbiq olmadan silmə sorğusu", "Nələr
silinir", "Nələr qalır və nə qədər" placeholders; a "Diqqət" callout
(`primaryContainer` / `onPrimaryContainer`); English section.

## Components

| Component | Colours (both themes) | Shape |
| --- | --- | --- |
| Page | `surface` / `onSurface`, secondary text `onSurfaceVariant` | — |
| Nav pill (current) | `primaryContainer` / `onPrimaryContainer` | `shape.full` |
| Nav pill (hover) | `surfaceContainer` / `onSurface` | `shape.full` |
| TOC card, link card | `surfaceContainerLow`; text `onSurface`, `onSurfaceVariant`, links `primary` | `shape.large` |
| Callout | `primaryContainer` / `onPrimaryContainer` | `shape.large` |
| Placeholder | `bannerWarningContainer` / `onBannerWarningContainer`, 2px dashed border in the text colour | `shape.medium` |
| Focus ring | 3px `primary`, 2px offset | — |
| Dividers | `outlineVariant` (decorative only) | — |

All text pairs are in `design/tools/contrast.py` (group "Web legal pages") and pass
4.5:1 in both themes.

## States

| State | What the user sees |
| --- | --- |
| Light / dark | Follows the OS (`prefers-color-scheme`); `theme-color` meta matches `surface`. |
| Draft (placeholders present) | Orange dashed "Mətn hazırlanır" boxes; must not be submitted to Play. |
| Final | Placeholders replaced by text; dates set. |
| Offline / slow network | One HTML + one CSS + one SVG favicon, all same-origin, ~20 KB total. |
| Print | Nav, skip link and placeholders hidden; black on white. |
| Loading / empty / error | Not applicable (static files). A wrong URL shows GitHub's 404 page. |

## Interactions and accessibility

- `lang="az"` on `<html>`, `lang="en"` on English sections.
- Landmarks: `header`, `nav` ("Səhifələr"), `main#main`, `footer`; one `h1` per page;
  sections labelled by their `h2`.
- Decorative SVG marks are `aria-hidden`; the brand link text is "Duello Quiz".
- Touch targets ≥ 48px for nav, footer links and cards.
- No layout shift, no animation except a 150ms background fade on hover, disabled
  with `prefers-reduced-motion: reduce`.
- Font sizes are in rem, so text follows the browser text size; works at 200 % zoom
  without horizontal scrolling at 360px.

## String keys

Not applicable: the pages are not part of the app; Azerbaijani text is inline.
UI labels quoted in the deletion steps come from `copy/az.md` and must be updated
together with it.
