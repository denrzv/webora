# BROWSE-007: Recents, history and favourites
Status: PRD_READY

## Context / Problem

Webora's Home screen still shows placeholder empty cards for Recent sites and Favourites even after
the user browses. The browser also has no durable, user-controlled record of visited pages or saved
destinations. M8 needs useful local browsing data without weakening Webora's zero-telemetry stance
or allowing page-authored labels to become trusted navigation commands.

## Goals

- Persist successful main-frame visits locally with canonical HTTP(S) URL, canonical origin,
  bounded display title, and visit time.
- Present the newest visit per canonical URL as Home recents while retaining the bounded underlying
  visit history.
- Let users explicitly add and remove favourites keyed by canonical URL and open the exact stored
  URL from Home.
- Make Clear browsing data remove history/recents while explicitly retaining favourites.
- Introduce no sync, analytics, recommendation, or other Webora-controlled network traffic.

## Non-goals

- A full searchable History screen, folders/tags, cross-device sync, accounts, or incognito mode.
- Favicon fetching or any browser-owned metadata request.
- Letting manifests choose history/favourite actions, keys, order, timestamps, or destinations.
- Changing WebView's own platform history contract or redesigning the persistent shell (`UX-011`).

## User stories

- As a user, I see sites I actually visited in newest-first order when I return Home.
- As a user, repeated visits do not crowd Home with duplicates, but the local history still records
  those visits.
- As a user, I can save or remove the current page and the saved destination survives restart.
- As a user clearing browsing data, I understand that history is removed and favourites are kept.

## Acceptance criteria

1. A successful ordinary or integrated main-frame page completion records a canonical HTTP(S) URL,
   canonical origin, bounded safe display title, and visit timestamp; failed, non-main-frame, blank,
   unsupported-scheme, and Home navigations do not create records.
2. Home Recent sites shows at most ten newest distinct canonical URLs with deterministic duplicate
   handling, while the persisted history retains at most 200 visits.
3. The current page exposes explicit Add favourite / Remove favourite browser-owned actions; the
   favourite is keyed by canonical URL, persists across store recreation, and opens that exact URL.
4. Home sections show useful empty states only when empty and otherwise expose accessible,
   browser-authored Open/Remove controls whose destinations come only from validated stored records.
5. Clear browsing data removes persisted history and therefore Home recents, explicitly states and
   preserves favourites, and reports incomplete clearing if the history clear fails.
6. Store and UI tests cover malformed persisted input, unsupported schemes, duplicate visits,
   bounded titles/collections, deterministic ordering, favourite identity, and clear semantics.
7. Browsing/history/favourite operations add no network client, permission, sync, telemetry, or
   Webora-controlled request.
8. `bash scripts/pre-commit-check.sh` passes.
