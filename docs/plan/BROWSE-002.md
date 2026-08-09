# BROWSE-002 implementation plan

Status: PLAN_APPROVED

## Flow and trust boundary

User text enters a pure browser-owned resolver. It emits only an absolute HTTP(S) renderer URL or an encoded HTTPS search URL. A controller sends navigation commands to the already-hardened WebView. WebView callbacks report URL/loading/history observations into immutable UI state. Those observations can produce only Home or Regular mode; Integrated remains constructible only with trusted core values through an explicit future activation event.

Page content owns its document and history entries, but it cannot choose the search provider, enable a scheme, alter hardening, synthesize Integrated mode, or override system-back precedence. Redirect origin changes replace the observed Regular origin rather than retaining prior-origin identity.

## Changes

1. Add sealed browser modes and a pure state reducer with events for navigation observations and future trusted integration activation.
2. Add a strict address resolver for explicit HTTP(S), host-like HTTPS promotion, encoded search, and denied/malformed input.
3. Extend the hardened host with a stable controller and browser observation callbacks while retaining its immutable client policy.
4. Build Material 3 browser chrome with address entry and back/forward/reload controls, and connect activity back handling to live WebView history.
5. Add focused JVM tests and compile Android instrumentation sources; update architecture notes and roadmap only after review/QA.

## Security checks

Negative tests must prove `javascript:`, `file:`, `content:`, credentials, control characters, and malformed destinations never become renderer commands. State tests must prove page observations cannot synthesize Integrated mode and origin changes replace Regular origin.

## Validation

Run focused app unit tests after each code change, then `bash scripts/pre-commit-check.sh` before every task commit. Compile instrumentation tests. Run device tests and capture a screenshot only when a connected device exists; otherwise record the managed-cloud limitation.
