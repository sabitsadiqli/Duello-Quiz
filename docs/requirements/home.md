# Home — requirements

Spec: `docs/mvp-spec.md` §1, §2, §6, §7. Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

## 1. Goal

Home is the hub a signed-in player returns to after every quiz and duel. It must
show, within a second on a low-end phone, what can be played (categories from the
server, never hard-coded), the way into a duel, who the player is and their rating,
and the way into settings. It must stay usable on a weak or missing connection by
explaining what is not available instead of showing a blank screen.

## 2. User stories

| ID | Story | Blocked by |
| --- | --- | --- |
| HOME-01 | As a player, I want to see the list of playable categories, so that I can choose what to play. | OQ-013 (filter rule) |
| HOME-02 | As a player, I want to tap a category to start its quiz, so that I can play immediately. | — |
| HOME-03 | As a player, I want a clear entry to the duel, so that I can find an opponent. | OQ-020 |
| HOME-04 | As a player, I want to see my name, avatar and rating, so that I know my standing. | OQ-011 (name) |
| HOME-05 | As a player on a bad connection, I want home to show the last known data and tell me I am offline, so that the app does not look broken. | — |
| HOME-06 | As a player, I want an entry to settings, so that I can change theme or sign out. | — |

### HOME-01 Category list

Data: `GET /v1/categories` with `Accept-Language` from the app locale (`az`).

- Given the server returns categories, When home loads, Then only categories with
  `is_active = true` are listed, ordered by `sort_order` ascending (ties broken by
  `slug`), each with its localized name and icon. The server applies the filter and
  order; the client keeps the order it receives.
- Given a category's `icon_key` has no matching bundled icon (new category added on
  the server), When it is shown, Then a generic category icon is used; the app does
  not crash and needs no update.
- Given a category has fewer than 10 active questions, When the list is built, Then
  it is handled as decided in OQ-013 (recommended: the server omits it).
- Given a localized name is missing for the requested language, When the server
  builds the response, Then it falls back to `az`.
- Given a long name (e.g. "Coğrafiya" in a large font scale), When shown on a 360 dp
  screen at font scale 1.3, Then it wraps to at most 2 lines and is never cut
  mid-word without an ellipsis.
- Loading: Given no cached list and the request is running, When home is shown, Then
  skeleton placeholders are shown (no spinner-only blank screen).
- Empty: Given the server returns an empty list, When home loads, Then "Hələ ki
  kateqoriya yoxdur. Bir az sonra yenidən yoxlayın." is shown with "Yenilə".
- Error: Given the request fails with 5xx or times out after 15 s and there is no
  cache, When home loads, Then "Kateqoriyaları yükləmək alınmadı." with "Yenidən
  cəhd et" is shown.
- Refresh: Given home is visible, When the user pulls to refresh or returns to home
  from a quiz/duel, Then the list is reloaded in the background without hiding the
  current list.
- Abuse: Given the user pulls to refresh repeatedly, When a request is already
  running, Then no second request is started.

### HOME-02 Start a category quiz

- Given a category is shown, When the user taps it, Then the quiz flow (QUIZ-01)
  starts for that category; the tap shows immediate feedback (ripple + loading on
  the card).
- Given the user double-taps a card or taps two cards quickly, When the first
  request is running, Then only one quiz session is created and only one quiz screen
  opens.
- Given the device is offline, When the user taps a category, Then "Quiz üçün
  internet lazımdır." is shown and no screen change happens.
- Given the server answers that the category is not playable any more (inactive or
  too few questions, QUIZ-08), When the user taps it, Then "Bu kateqoriya hazırda
  əlçatan deyil." is shown and the list is refreshed.

### HOME-03 Duel entry

- Given the duel feature is available (OQ-020), When home is shown, Then a prominent
  "Duel" entry with a one-line hint ("Əminliyini sına, reytinq qazan") is shown
  above the category list.
- Given the user taps it, When online, Then the duel flow starts (requirements in
  `duel.md`, not part of this file).
- Given the device is offline, When the user taps it, Then "Duel üçün internet
  lazımdır." is shown.
- Given the duel feature is not available yet in this build (per OQ-020), When home
  is shown, Then no duel entry is visible (no dead "coming soon" button).

### HOME-04 Profile and rating summary

Data: `GET /v1/me` (creates the profile on first call, AUTH-06).

- Given the profile loaded, When home is shown, Then the header shows the avatar,
  display name and "Reytinq: 1000" (the user's current Elo).
- Given no avatar URL (email account), When the header is shown, Then a circle with
  the first letter of the display name is shown; for a failed image load the same
  fallback is used.
- Given a long display name, When shown on 360 dp, Then it is truncated with an
  ellipsis on one line.
- Given the profile request fails and a cached profile exists, When home is shown,
  Then the cached values are shown; without cache the header shows placeholders and
  the error is handled together with HOME-05.
- Given the user returns from a finished duel, When home is shown again, Then the
  rating is refreshed.
- The header is a summary only in the MVP; tapping it does nothing or opens a
  read-only profile if a profile feature is approved later (not in this file).

### HOME-05 Offline and cached state

- Given home loaded successfully once, When the categories and profile arrive, Then
  they are cached locally (Room/DataStore) for the signed-in user.
- Given the device is offline at launch and a cache exists, When home is shown, Then
  cached categories and profile are shown with a banner "İnternet yoxdur. Məlumatlar
  yenilənməyib." and actions that need the network show the offline message.
- Given offline and no cache, When home is shown, Then a full-screen offline state
  "İnternet bağlantısı yoxdur." with "Yenidən cəhd et" is shown.
- Given the connection returns, When the app detects it, Then the banner disappears
  and data is refreshed automatically once.
- Given the user signs out, When another account signs in, Then the previous
  account's cache is not shown (AUTH-07).
- Given a slow network (request > 3 s), When a cache exists, Then the cache is shown
  immediately and replaced when the response arrives (no flicker of empty state).

### HOME-06 Settings entry

- Given home is shown, When the user taps the settings icon (top app bar, content
  description "Parametrlər"), Then the settings screen opens (SET-*).
- Given settings is closed with back, When home is shown again, Then scroll position
  and loaded data are kept.

## 3. Business rules

- Categories are database rows (`docs/mvp-spec.md` §6: `categories` with
  `name_i18n`, `icon_key`, `sort_order`, `is_active`). No category names or lists in
  app code; only the icon set for known `icon_key` values is bundled.
- Language for names: `Accept-Language`, fallback `az` (`docs/mvp-spec.md` §4).
- Rating shown is the Elo from `docs/mvp-spec.md` §2 (start 1000).
- The back button on home exits the app (home is the root of the signed-in graph).

## 4. Out of scope

Leaderboards, friends, daily challenges, per-category progress or best scores,
notifications, profile editing, ads or purchases.

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `home_viewed` | `source`: `launch` \| `quiz_end` \| `duel_end` \| `back`, `from_cache` (bool) | Home becomes visible |
| `category_selected` | `category_slug`, `position` (index in list) | Category tapped |
| `duel_entry_tapped` | `online` (bool) | Duel entry tapped |
| `categories_load_failed` | `error_type`: `offline` \| `timeout` \| `server` | Category request failed |
| `settings_opened` | — | Settings entry tapped |

## 6. Open questions

OQ-011 (display name), OQ-013 (categories with fewer than 10 active questions),
OQ-020 (duel entry before the duel ships).
