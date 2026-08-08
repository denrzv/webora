# Project rules — Webora Browser

## Mandatory
- Follow the AIDD order: `/idea` → `/researcher` → `/plan` → `/tasks` → `/implement` → `/pre-commit` → `/review` → `/qa` → `/validate`.
- One TASK per commit. Message: `<TICKET> TASK-N: <short>`.
- Run `bash scripts/pre-commit-check.sh` before every commit.
- After every completed `TASK-N` or `TASK-FIX-N` commit, push the current ticket branch before starting the next task. A task is not handoff-safe until its commit is visible on the remote.
- If a task checkpoint cannot be pushed, stop and report the blocked checkpoint instead of continuing with another task.
- Treat every manifest as untrusted remote input. Parsing success is never trust.
- Every website-controlled native capability needs an explicit browser-owned contract.
- Security-sensitive behaviour requires explicit tests, including negative cases.

## Forbidden
- Arbitrary native code execution driven by remote configuration.
- A general-purpose JavaScript bridge (`addJavascriptInterface`) in MVP.
- Hiding or restyling browser security affordances (domain, TLS state) from a manifest.
- Trusting subdomains, cross-origin assets, or non-allow-listed URI schemes.
- Blocking WebView rendering on manifest discovery.
- Silent failure: an invalid manifest degrades to regular browser mode, never a broken page.

## Defaults
- HTTPS required for SiteSkin mode.
- Unknown action type → drop that item, keep the rest of the manifest.
- Unknown major schema version → reject, regular browser mode.
- Zero telemetry unless the user opts in.
