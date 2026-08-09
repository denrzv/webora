# BROWSE-003 QA

Status: QA_PASSED

## Scope

First-launch onboarding, persistent completion, returning-user Home launch, Home content, suggestion trust policy, navigation integration, and regression coverage for the hardened browser.

## Scenario results

| # | Scenario | Evidence | Result |
|---|---|---|---|
| 1 | New user launches onboarding | pure launch JVM assertion + composition source compile | PASS |
| 2 | Returning user launches Home | pure launch JVM assertion | PASS |
| 3 | Skip and final action persist completion | preference wrapper/source integration compile | PASS |
| 4 | Carousel describes browsing, SiteSkin, and user control | three browser-owned pages + source review | PASS |
| 5 | Home shows search, recents, favourites, and suggestions | Compose source compile; honest empty-state copy | PASS |
| 6 | Safe suggestion opens Regular mode | catalogue and state JVM assertions | PASS |
| 7 | Unsafe suggestions fail closed | negative JVM table for HTTP, JavaScript, relative, credentials, fragment, malformed | PASS |
| 8 | Home performs no implicit page load | mode-conditional WebView source review | PASS |
| 9 | Android sources and APK | instrumentation Kotlin compile + debug assembly | PASS |
| 10 | Full repository guardrails | `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases

- invalid manifest → N/A — this ticket does not fetch or consume manifests; Home cannot activate Integrated mode.
- origin change / redirect → existing WebView callbacks replace the observed Regular origin after explicit navigation.
- offline with cached manifest → no manifest cache exists; local onboarding/Home still render without network access.
- oversized or malformed payload → N/A for remote payloads; malformed typed/suggested destinations fail before renderer navigation.
- accessibility (TalkBack, font scale) → controls use Material text semantics, explicit labels, headings through typography, and scalable text; runtime TalkBack/font-scale validation remains A11Y-001.
- completion race → the in-memory destination changes immediately while the preference write is asynchronous; a later process reads the committed Boolean.
- screenshot/device execution → unavailable because `adb devices` has no target and `/dev/kvm` is absent; no software-only emulator was provisioned per repository policy.

## Result

All available automated and source-level acceptance evidence passes. Runtime device instrumentation and screenshots are unavailable in this managed checkout and are an environment limitation, not a task failure.
