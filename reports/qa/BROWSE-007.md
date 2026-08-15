# QA Report: BROWSE-007
Status: QA_PASSED

## Scope

Local successful main-frame history recording, deterministic Home recents, persistent URL-keyed
favourites, browser-owned favourite actions, and history-versus-favourites clear semantics.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Canonical page identity | Store tests with ports, paths, query and fragments | PASS — canonical full URL/origin retained; fragment removed. |
| 2 | Unsafe/corrupt data | JVM scheme, credentials, size, corruption and version controls | PASS — rejected or independently dropped. |
| 3 | Successful versus failed completion | WebView-client success and start→error→finish→next-success tests | PASS — failed finish suppressed; next success emitted. |
| 4 | Recent ordering and duplicates | Repeated URL visits with injected clock | PASS — history retained, newest distinct recents projected. |
| 5 | Bounds | 220 visits and 120 favourites | PASS — 200/100 stores and ten recents enforced. |
| 6 | Favourite persistence | Fake preferences across store recreation | PASS — canonical URL survives and removes explicitly. |
| 7 | Home populated/empty states | Compiled Compose tests | PASS (compiled) — exact URLs and remove callback asserted. |
| 8 | Browser favourite command | Compiled Compose test | PASS (compiled) — dynamic label invokes browser callback. |
| 9 | Clear browsing data | Store and aggregate cleaner JVM tests | PASS — history clears, favourites remain, failures aggregate. |
| 10 | No Webora network operation | Dependency/diff inspection | PASS — no client, permission or endpoint added. |
| 11 | Full guardrails | `bash scripts/pre-commit-check.sh` | PASS — all repository checks green. |

## Edge cases

- invalid manifest → regular browser mode: PASS — unchanged; recording consumes observed page data,
  not a manifest or SiteSkin activation.
- origin change / redirect: PASS — final successful observed URL is canonicalized and each page start
  resets the tab completion/failure guard.
- offline with cached manifest: PASS — cached discovery is orthogonal; failed main-frame loads do not record.
- oversized or malformed payload: PASS — N/A for manifest bytes; stored URLs/titles and collections
  are bounded and malformed records are tested.
- accessibility (TalkBack, font scale): PASS (compiled) — actions use existing 48 dp wrappers and
  vertical cards; device runtime is unavailable.
- empty collections: PASS — empty copy appears only for an empty corresponding collection.
- duplicate visits: PASS — history retains visits while recents keeps the newest URL entry.
- clear failure: PASS — marks the aggregate incomplete and continues later adapters.
- favourite clear choice: PASS — confirmation says favourites stay and a store test proves it.

## Environment limitations

`adb devices` reports no connected device and `/dev/kvm` is absent. Per repository policy no
software-only emulator was provisioned. Instrumentation compiles, but runtime instrumentation,
TalkBack/200% exercise, packet capture, and a perceptual screenshot are unavailable. Structural
inspection proves no new transport call site or dependency; it does not claim a device packet trace.

## Result

Status: QA_PASSED

All runnable scenarios and the complete gate pass. Device-only evidence is an environment limitation.
