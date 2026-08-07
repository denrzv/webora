# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Project Overview

**Webora Browser** is an Android browser that adapts its native chrome to websites which publish a
**SiteSkin** manifest. For a site with a valid manifest, the browser renders a branded top bar,
bottom navigation and quick actions around the WebView. For every other site it behaves like an
ordinary browser.

The product's central contract:

> The website defines what should be available. The browser decides how it is safely displayed and
> executed.

The app brand is **Webora**; the protocol is **SiteSkin**. They are deliberately separate names —
site owners implement a vendor-neutral protocol, not one browser's private format.

> **Repo naming:** the GitHub repo is currently `denrzv/skinsite` and is to be renamed to
> `denrzv/webora`. Everything inside already says Webora.

## Build Commands

```bash
./gradlew :siteskin-core:test          # pure JVM, needs no Android SDK — start here
./gradlew :siteskin-core:check         # + the Android-dependency leak check
./gradlew test                         # all unit tests
./gradlew :app:assembleDebug           # the task that actually runs D8
./gradlew :app:assembleDebugRelease    # local-testing APK, cleartext HTTP permitted
./gradlew detekt                       # complexity gate
./gradlew :siteskin-lint:run --args="https://site.example"
bash scripts/pre-commit-check.sh       # everything above, in order
```

**Stack:** Kotlin 2.3.20, AGP 9.1.0, minSdk 26, targetSdk 36, Compose BOM 2026.03.01,
kotlinx.serialization, OkHttp 5.

---

## AIDD workflow (mandatory)

See `workflow.md` for the state machine and `PROJECT_RULES.md` for the hard rules.

1. `/idea <TICKET> "Title"` → `Status: PRD_READY`
2. `/plan <TICKET>` → `Status: PLAN_APPROVED`
3. `/tasks <TICKET>` → `Status: TASKLIST_READY`
4. Per task: `/implement <TICKET> TASK-N` → `/pre-commit` → commit `<TICKET> TASK-N: <short>`
5. `/review` → findings become `TASK-FIX-N` micro-tasks
6. `/qa` when green, then `/validate`

A `PreToolUse` hook blocks `Edit|Write` until all three artifacts carry their ready status.
`docs/`, `reports/` and `spec/` are exempt, or writing the PRD would be blocked by the gate that
the PRD exists to satisfy.

The full milestone plan, ticket index and the decisions behind them live in
**`docs/DEVELOPMENT_PLAN.md`**. Read it before proposing architecture.

---

## Architecture

```
:siteskin-core   pure JVM — origin, DTOs, validation, actions, nav matching
:siteskin-lint   CLI wrapping core; the tool site owners run
:app             app.webora.browser — Compose, WebView, OkHttp
```

### The module boundary is the security boundary

`:siteskin-core` has **zero** Android dependencies and is where every security decision lives:
origin binding, scheme allow-listing, action resolution, contrast correction, limit enforcement.
This is not tidiness. It means the security logic is testable with plain JUnit in milliseconds, with
no emulator and no Android SDK — so there is never a reason to skip a security test because the
harness is slow.

Two mechanisms enforce it, because a documented rule is not a rule:
- `:siteskin-core:check` depends on `assertNoAndroidDependencies`, which fails on any `androidx.*`
  or `com.android*` artifact on the compile classpath.
- CI's `core` job installs no Android SDK at all and runs `:siteskin-core:check` with `ANDROID_HOME`
  unset.

Core declares `interface ManifestSource` and consumes bytes. OkHttp lives in `:app`. Core never
learns what a `Context` is.

### Trust pipeline

```
Remote bytes → size guard → JSON parse → DTO → schema validation
             → security validation → normalization → SiteSkinConfiguration
```

`SiteSkinConfiguration` is constructible only via the validator. If you hold one, it passed. Never
add a public constructor or a `copy()` that skips validation — that turns a compile-time guarantee
back into a code-review guarantee.

Parsing success is not validity. A DTO is untrusted remote input that happens to be well-formed.

---

## Java version — two knobs, do not conflate them

| Knob | Value | Constrained by |
|---|---|---|
| Gradle toolchain (runs the build) | **25 LTS** | Gradle/AGP. Free to raise. |
| `jvmTarget` / source & target compatibility | **21** | D8/R8 dexing, *not* the build JDK |

`:siteskin-core` is a plain JVM library but is dexed into the APK, so it inherits `:app`'s ceiling.

If you change the bytecode target, verify with `./gradlew :app:assembleDebug` — that is the task
that invokes D8. `compileDebugKotlin` succeeding proves nothing about dexability, and the failure
otherwise surfaces at assemble time, long after the code looks fine.

---

## Security notes

### Browser-owned chrome (ADR-006)

The registrable domain and TLS indicator are **always visible in SiteSkin mode**, in browser-owned
typography, and no manifest field can suppress them. The concept document's `toolbar.showDomain`
is deliberately **not** implemented as site-configurable.

The reason is concrete: a manifest supplies a title, colours and a logo. Allow it to also hide the
domain and a manifest on `evil.example` renders chrome reading "Your Bank" with a bank logo and no
contradicting signal anywhere on screen. That is both the sharpest security hole in the design and
a Google Play *Deceptive Behavior* violation, which is suspension-grade rather than
rejection-and-resubmit.

The site gets colours, a title, and a bounded logo slot *beside* the domain — never instead of it.

### First-use consent (ADR-011)

The first time an origin's manifest validates, the user is asked before the chrome changes. Allow /
Not now / Never for this site. This is the enforcement point for the per-site opt-out, and it is
what makes the trust boundary legible rather than implicit.

### Allow-lists, never deny-lists

Schemes (`https`, `mailto`, `tel`, `geo`), action types, icon names, asset MIME types. An unknown
action type drops that item and keeps the rest of the manifest; an unknown *major* schema version
rejects the whole manifest and falls back to regular browsing.

### Failure is always graceful

An invalid manifest never breaks browsing. Every failure path ends in regular browser mode with the
page still rendering. `ADR-010`.

### Discovery never blocks rendering

The page load and the manifest fetch are concurrent. The chrome transitions when validation
succeeds. Never gate `onPageStarted` on a network call. `ADR-009`.

---

## Testing

JUnit 4 + MockK. **No Robolectric** — deliberate. Consequences:

- Android-touching objects split into a thin public wrapper reading real framework state, plus an
  `internal` pure function the tests call directly. Test the pure function.
- MockWebServer for anything network-shaped. Distinct ports are distinct origins, which is exactly
  what origin-binding tests need.
- `mockkObject` is a **partial** mock: unstubbed functions run the real implementation. Stub every
  function a test path touches.

**Security tests need a negative control.** Revert the protection, confirm the test fails, restore
it. A test that passes with and without the fix is not evidence — it is decoration. Record the
negative-control result in the tasklist.

The conformance corpus in `spec/fixtures/` is the contract. Validation code is written to satisfy
it, not the other way round. Adding a validation rule means adding a fixture first.

---

## Detekt

Wired in the root `build.gradle.kts` for all subprojects, so a new module cannot silently escape the
gate. `scripts/pre-commit-check.sh` invokes it **unconditionally** — never guard it behind
`tasks --all | grep -q detekt`, which lets an unwired plugin masquerade as a passing build.

Two config landmines, both pre-fixed here:
- `complexity > ComplexMethod` is a deprecated name; with `warningsAsErrors: true` it fails the
  whole run. Use `CyclomaticComplexMethod`.
- `naming > FunctionNaming > ignoreAnnotated: ['Composable']` — otherwise every composable trips
  PascalCase.

The baseline starts empty. Anything added to it is a reviewed exception with a ticket.

---

## Non-negotiable constraints

- The manifest is data, never code. No `addJavascriptInterface`, no dynamic loading, no arbitrary
  intents.
- No website gains an Android permission by publishing a manifest. A "Call" action opens
  `ACTION_DIAL`; it does not grant `CALL_PHONE`.
- HTTPS required for SiteSkin mode. Only `debugRelease` relaxes cleartext, and only in its own
  source set.
- Subdomains are not trusted. Assets are same-origin only.
- No telemetry without explicit opt-in.
- Never block the main thread on network or image decoding.
