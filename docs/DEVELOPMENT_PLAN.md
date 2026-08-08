# Webora Browser — development plan (AIDD bootstrap)

Status: APPROVED
Date: 2026-08-07

> **Repository note — resolved.** This plan was approved naming `denrzv/webora` as the app repo.
> Attaching that repo was blocked when the plan was written, so the bootstrap was drafted against
> `denrzv/skinsite` and this section used to describe a pending rename. That is now moot: the
> scaffolding was committed to **`denrzv/webora`** directly, and `denrzv/skinsite` never received a
> commit, so there is nothing to rename and nothing to migrate. Every identifier already said
> Webora — `applicationId app.webora.browser`, Gradle root project `Webora`.

## Context

You have a product concept (`siteskinbrowserandroidconcept.md`) for an Android browser that reads a
declarative manifest published by a website and reshapes its own native chrome — branded top bar,
bottom navigation, quick actions — around a WebView that still renders the real site. Regular sites
get an ordinary browser. Two repos are empty and waiting: **`denrzv/webora`** (the app + the spec)
and **`denrzv/bloom-flowers`** (the reference integration). `denrzv/skinsite` is abandoned and needs
no further work. `denrzv/hermes-agent` is reference-only — it carries the AIDD workflow (artifact
templates, `Status:` gates, hook-enforced ordering, ticket naming) that this project should adopt.

See the repository note above — the bootstrap ultimately landed in `webora` as intended.

This session produces the planning artifacts and the repo bootstrap. No feature code. The outcome
is: both repos scaffolded, quality gates green and provably real, an epic-level ticket index with
PRDs, ADRs recording the security-critical decisions, and the SiteSkin manifest specified as a real
contract — so implementation can start with `/implement SPEC-001 TASK-1` and never touch an
undecided question again.

---

## Naming: **Webora Browser, powered by SiteSkin** *(settled)*

App brand is Webora; the protocol stays SiteSkin. The reason the split is worth keeping is that
**site owners implement the protocol, not the app** — nobody wants to publish
`/.well-known/webora.json` for one vendor's browser, and a neutral protocol name is what makes the
spec adoptable by a second implementer later.

| Thing | Name |
|---|---|
| Android app / Play listing | **Webora Browser** |
| Tagline | One browser. A native-like experience for every website. |
| `applicationId` | `app.webora.browser` |
| Gradle root project | `Webora` |
| Protocol / spec | **SiteSkin Manifest v1.0** |
| Discovery URL | `https://<origin>/.well-known/siteskin.json` |
| Core module | `:siteskin-core` (package `dev.siteskin.core`) |
| Repos | `denrzv/webora` (app + spec), `denrzv/bloom-flowers` (reference site) |

**Caveat I cannot clear from here:** run USPTO TESS + EUIPO searches and a Play Store name search
before you file. `Arora` was a (dead, 2011) Qt browser — worth noting, not worth worrying about.
The name is reversible up to store listing; baked into `applicationId`, it is not reversible after
publishing.

---

## Two decisions that change the concept document

Both belong in ADRs and PRDs, not in a footnote, so I am flagging them before you approve.

**1. `toolbar.showDomain` must not be site-configurable.** Concept §8 lets the manifest set
`showDomain: false`. Combined with a site-supplied logo and title, that is a phishing kit: a
manifest on `evil.example` renders native chrome reading "Your Bank" with Your Bank's logo and no
domain anywhere on screen. This is simultaneously the product's sharpest security flaw and its
sharpest Google Play risk (Deceptive Behavior / impersonation is a suspension-grade violation, not
a rejection-and-resubmit one). The registrable domain and the TLS indicator stay visible in SiteSkin
mode, always, in browser-controlled typography. The site gets the colours, the title, and a bounded
logo slot *next to* the domain — never instead of it. → `ADR-006`, `HARDEN-002`.

**2. First-use confirmation before SiteSkin mode activates.** Concept §60 Q15 leaves this open. Make
it yes: the first time an origin's manifest validates, a bottom sheet says "bloomflowers.example
wants to customise this browser's navigation and appearance" with Allow / Not now / Never for this
site. It is one tap per site, it makes the trust boundary legible to the user, it gives you a clean
answer for a Play reviewer asking who controls the UI, and it is the enforcement point for the
per-site opt-out you need anyway (§60 Q10). → `ADR-011`, `PRIV-001`.

---

## Architecture

```
webora/
├── siteskin-core/          pure JVM (java-library) — NO Android dependency
│   └── dev.siteskin.core
│       ├── origin/         SiteOrigin, UrlResolver, IdnGuard
│       ├── dto/            *Dto  — kotlinx.serialization, untrusted
│       ├── validate/       SchemaValidator, SecurityValidator, Diagnostics
│       ├── model/          SiteSkinConfiguration — trusted, construct-only-via-validator
│       ├── action/         ActionResolver → ResolvedAction (sealed)
│       └── nav/            NavMatcher
├── siteskin-lint/          tiny JVM CLI wrapping siteskin-core (site-owner tool)
├── app/                    app.webora.browser — Compose, WebView, OkHttp
├── spec/                   SPEC.md, siteskin-1.0.schema.json, fixtures/
├── docs/                   AIDD artifacts (prd/ plan/ tasklist/ adr/ research/)
├── reports/                qa/ review/ security/
└── scripts/ .claude/ .github/ config/detekt/
```

The point of `:siteskin-core` being pure JVM: every security-critical decision — origin binding,
scheme allow-listing, action resolution, contrast correction, limit enforcement — is testable with
plain JUnit at millisecond speed, with no emulator and **no Android SDK at all**. That matters
concretely here: this container has JDK 21 and Gradle but no Android SDK, so `:siteskin-core:test`
runs today while `:app` needs SDK provisioning (see Verification).

Core defines `interface ManifestSource` and takes bytes; OkHttp lives in `:app` and implements it.
Core never learns what a `Context` is.

### Java version — two different knobs, don't conflate them

"Current LTS" is **Java 25** (Sept 2025; the LTS cadence is 17 → 21 → 25). Gradle runs on it from
9.1.0. But for an Android project there are two independent settings and only one of them is free:

| Knob | Value | Constrained by |
|---|---|---|
| **Gradle toolchain** — the JDK that runs the build and compiles | **25 LTS** | Gradle + AGP version. Free to raise. |
| **`:app` bytecode target** — `sourceCompatibility` / `targetCompatibility` / `jvmTarget` | **21**, verified at bootstrap | D8/R8 dexing, *not* the build JDK |
| **`:siteskin-core` bytecode target** | **21** — same as `:app` | It is dexed into the APK, so it inherits `:app`'s ceiling even though it is a plain JVM module |

The distinction matters because raising the toolchain is safe and raising the bytecode target is
not: Android's official JDK table still tops out at Java 17 for core-library support, and Google
publishes no explicit D8/R8 maximum class-file version. Kotlin's `JvmTarget` enum does carry
`JVM_21` and `JVM_25`, and `jvmTarget = 21` is widely used on AGP 8.2+, so 21 is the realistic
ceiling today — but it is an empirical question, not a documented one.

So `FOUND-002` **verifies rather than assumes**: set the toolchain to 25, attempt `jvmTarget = 21`,
run `./gradlew :app:assembleDebug` (which invokes D8), and record the outcome in the ticket. If D8
rejects it, fall back to 17 and note why. Do not skip this — a dexer failure surfaces at assemble
time, long after the code compiles cleanly, which is exactly when it is most annoying to discover.

`:siteskin-core`'s *tests* still run on the toolchain JDK, so you get Java 25 where it costs
nothing. Core-library desugaring stays on regardless (`isCoreLibraryDesugaringEnabled = true`).

One environment consequence: this container has JDK 21, so a toolchain of 25 means Gradle resolves
and downloads it via the foojay resolver on first build. The `FOUND-003` SessionStart hook installs
JDK 25 alongside the Android SDK so cold sessions do not pay that download.

**Key stack choices** (differ from hermes-agent deliberately):
- **kotlinx.serialization**, not Moshi — Moshi's codegen is KSP-on-Android; core is pure JVM.
- **No Robolectric**, MockK only — carry over hermes' convention of splitting every Android-touching
  object into a thin public wrapper plus an `internal` pure function the tests call directly
  (`KeepaliveScheduler.useExactAlarm(sdkInt, canScheduleExactAlarms)` is the model).
- **MockWebServer** for fetch/cache tests — distinct ports are distinct origins, which is precisely
  what origin-binding tests need and what shared-host static hosting cannot give you.

---

## Milestones and ticket index

Ticket ids follow hermes convention: `<DOMAIN>-<NNN>`, zero-padded, prefixes coined per theme.
Reserved-forward ids are legitimate (hermes cites `RELIABILITY-003` before it exists).

### M0 — Foundation *(this session)*
| Ticket | Scope |
|---|---|
| `FOUND-001` | AIDD scaffolding: `docs/{prd,plan,tasklist,adr,research}`, templates, `.active_ticket`, `reports/*/template.md`, `.claude/commands/*` (17, single location — hermes duplicates them at root; don't), `workflow.md`, `PROJECT_RULES.md`, `conventions.md`, `AGENTS.md`, `CLAUDE.md` |
| `FOUND-002` | Gradle: version catalog, `:app` + `:siteskin-core` + `:siteskin-lint`, AGP/Kotlin/Compose BOM, compileSdk/targetSdk **36**, minSdk 26, **JDK 25 toolchain / jvmTarget 21 (verified against D8)** |
| `FOUND-003` | Gates: `scripts/{pre-commit-check,gate-workflow,gate-pretool,ensure-docs,security-check}.sh`, `.claude/settings.json` hook on **PreToolUse**, detekt wired **unconditionally**, `.pre-commit-config.yaml`, gitleaks, CI (`ci.yml`, `security.yml`) on JDK 25, SessionStart hook installing Android SDK + JDK 25 |
| `FOUND-004` | ADR-001…ADR-012 |

Three hermes drifts to fix rather than inherit: detekt gated behind
`if ./gradlew -q tasks --all | grep -q detekt` silently no-ops when unwired (it did, for that repo's
whole history); the `complexity > ComplexMethod` rule name is deprecated and with
`warningsAsErrors: true` fails the run — use `CyclomaticComplexMethod`; and `SIGNING.md` documents a
release signing config `app/build.gradle.kts` never implemented. Also set
`naming > FunctionNaming > ignoreAnnotated: ['Composable']` from day one.

### M1 — SiteSkin API (spec first, then TDD against it)
| Ticket | Scope |
|---|---|
| `SPEC-001` | **SiteSkin Manifest v1.0**: `spec/SPEC.md` (normative), `spec/siteskin-1.0.schema.json`, and the conformance corpus — `fixtures/valid/*.json` + `fixtures/invalid/*.json`, each invalid case paired with its expected stable diagnostic code (`SS-E-ORIGIN-MISMATCH`, `SS-E-SCHEME-DENIED`, `SS-W-CONTRAST-CORRECTED`, …). **The corpus is the contract; the Kotlin is written to satisfy it.** |
| `SPEC-002` | ✅ Versioning & compatibility policy: `SPEC.md` §§4.1–4.5 (layer ordering, breaking-change rules with a security carve-out, deprecation lifecycle), the `spec/versions.json` decision table, and four version fixtures. Fixed two defects in the published schema — leading-zero versions, and a trailing-newline bypass in every `^…$` pattern |
| `SPEC-003` | `siteskin-lint` CLI — `siteskin-lint https://site.example` — the tool a site owner runs. Same validator as the browser, so "passes lint" means "will activate" |
| `CORE-001` | `SiteOrigin` + URL resolution: scheme/host/port, IDN→punycode, mixed-script homograph guard, default ports, relative-path resolution |
| `CORE-002` | DTOs + kotlinx.serialization parsing; byte-size guard before parse; unknown fields ignored-with-warning; parse success ≠ trust |
| `CORE-003` | ✅ `SchemaValidator` over parsed JSON → `ManifestValidationResult(errors, warnings)`, executing the version table and structural corpus with stable diagnostic codes |
| `CORE-004` | `SecurityValidator` + normalisation → trusted `SiteSkinConfiguration`: origin binding, scheme allow-list (`https`/`mailto`/`tel`/`geo`), icon allow-list, colour parse + WCAG AA contrast correction, limit clamping |
| `CORE-005` | Action model: `internal_url`, `external_url`, `phone`, `email`, `map`, `share`, `home`, `refresh`, `open_menu` → sealed `ResolvedAction`; unknown type drops the item, never the manifest |
| `CORE-006` | `NavMatcher` — active-item detection, exact path + `/cart/**` glob |

### M2 — Browser foundation *(the product must be a real browser first)*
`BROWSE-001` WebView host + hardening (JS on, `setAllowFileAccess(false)`, no
`addJavascriptInterface`, SafeBrowsing on, mixed content blocked) · `BROWSE-002` `BrowserMode`
sealed state machine, URL/search bar, back/forward/reload, predictive back · `BROWSE-003` Home +
onboarding (mockup screens 1–2: recents, favourites, suggested integrations) · `BROWSE-004`
Regular-mode chrome, `SecurityIndicator`, overflow menu, error pages (screen 6) · `BROWSE-005`
External navigation confirmation, downloads via `DownloadManager`, uploads via SAF, non-http scheme
handling.

### M3 — SiteSkin runtime
`NET-001` Fetcher: HTTPS-only, 128 KB cap, timeouts, **same-origin redirects only (max 2)**,
concurrent with page load, never blocking render · `NET-002` Cache keyed on
`origin + schemaVersion`, ETag/Last-Modified, TTL `min(Cache-Control, 24h)`, offline reuse, never
applied cross-origin · `NET-003` Brand assets: same-origin only, MIME allow-list (PNG/WebP; **no
SVG in v1**), byte + dimension caps, off-main-thread decode, monogram fallback · `SKIN-001` M3
theming from validated branding + dark derivation + contrast guard · `SKIN-002` SiteSkin top bar
with immovable domain + TLS indicator (screens 3–5) · `SKIN-003` Bottom nav, quick-action FAB, side
menu · `SKIN-004` Mode transitions, origin-change deactivation, redirect policy.

### M4 — Hardening, privacy, demo
`HARDEN-001` Adversarial corpus: `javascript:`/`file:`/`content:`/`intent:`/`data:` schemes,
oversized + deeply-nested manifests, redirect loops, IDN homographs, duplicate ids, over-limit
collections · `HARDEN-002` Brand-impersonation controls (the two decisions above) · `PRIV-001` Zero
default telemetry, global + per-site SiteSkin toggle, clear-browsing-data, Data-safety mapping ·
`A11Y-001` TalkBack, font scaling, 48dp targets, contrast, no-colour-alone · `DEVX-001` SiteSkin
Integration Inspector, debug builds only (concept §31) · `DEMO-001` **Bloom Flowers reference
integration** (own repo, own origin) · `DEMO-002` PixelPlay / Daily Journal / Example News on
distinct origins — the cross-origin skin-swap and skin-drop cases.

### M5 — Google Play
`PLAY-001` Compliance sweep · `PLAY-002` Real release signing + R8 keeps + versioning ·
`PLAY-003` Store listing, Data safety form, internal testing track.

---

## `denrzv/bloom-flowers` — the reference integration

Empty repo; greenfield. Built as **plain HTML/CSS/JS, no framework** — its job is to be the document
a site owner reads, so the diff between "responsive site" and "SiteSkin-integrated site" must be one
readable file. Matches mockup screen 3 (pink `#D94F8A`, hero, Best Sellers grid, Home/Catalog/Cart/
Profile nav, Call quick action).

```
bloom-flowers/
├── .well-known/siteskin.json     the entire integration — one file
├── index.html  catalog.html  cart.html  account.html
├── assets/siteskin/logo.png      512×512 PNG, < 64 KB
├── INTEGRATION.md                "add SiteSkin to your site in 15 minutes"
└── .github/workflows/            Pages deploy + `siteskin-lint` in CI
```

`INTEGRATION.md` is a deliverable, not a README — it is the artifact that proves the protocol is
adoptable by someone who is not us.

### Hosting — you are buying a domain, so build for real origins

This matters more than it looks: GitHub Pages puts every project site on `denrzv.github.io`, so all
four demos would share **one origin** — which destroys the origin-separation demo that is the whole
security story. A real domain with per-demo subdomains fixes it, and subdomains are themselves
distinct origins, so the setup demonstrates concept §26 ("subdomains are not trusted automatically")
rather than papering over it.

Suggested layout once you have the domain — say `webora.app`:

| Origin | Serves | Why |
|---|---|---|
| `webora.app` | Product page + **privacy policy** | Play requires a reachable privacy-policy URL on the listing *and* in-app (`PLAY-003`) |
| `bloomflowers.webora.app` | Bloom Flowers demo (`bloom-flowers` repo) | Primary reference integration, mockup screen 3 |
| `pixelplay.webora.app` | PixelPlay Games demo | Proves SiteSkin isn't e-commerce-specific (screen 4) |
| `journal.webora.app` | Daily Journal demo | Productivity case (screen 5) |
| `news.webora.app` | Example News — **no manifest** | The negative control (screen 6) |

Two properties this buys that a single origin cannot: navigating Bloom → PixelPlay must *swap*
skins, and Bloom → News must *drop* to regular mode. Those are the two transitions in the concept's
MVP success scenario (§46 steps 14–15) and the acceptance criteria for `SKIN-004`.

HTTPS is mandatory for SiteSkin mode (`ADR-001`), so the demo hosting must serve valid TLS —
GitHub Pages or Cloudflare Pages both issue certificates for custom subdomains automatically.

Serving all five from one repo is simplest (`bloom-flowers` grows a `sites/` directory and one
Pages deploy per subdomain); if you would rather keep Bloom clean as the reference, the other three
can live in `webora/demo/` with a `serve.mjs` for local work. I will assume the first and flag it
in `DEMO-002` — tell me if you prefer the split.

---

## Google Play compliance — the parts that actually bite

Deadline: **new apps must target API 36 from 31 Aug 2026** — three weeks out. So `compileSdk = 36`
and `targetSdk = 36` land in `FOUND-002`'s first commit, not as a `PLAY-001` cleanup. `minSdk = 26`
(Android 8.0) — high enough for a modern WebView baseline and per-app `ANDROID_ID`, low enough to
cover effectively every device still receiving updates.

| Risk | Mitigation | Ticket |
|---|---|---|
| **Policy 4.3 Minimum Functionality.** Enforcement tightened in 2026; reviewers pattern-match "WebView app" and reject. | Webora is a general-purpose browser — arbitrary URL entry, history, favourites, downloads, default-browser role — and the SiteSkin engine is substantial native functionality. Ship M2 genuinely working before submitting; prepare a reviewer note. | `PLAY-001` |
| **Deceptive Behavior / impersonation.** Site-controlled chrome is a suspension-grade risk if a manifest can dress the browser as another brand. | Non-suppressible domain + TLS indicator; bounded logo slot; first-use consent. | `HARDEN-002` |
| Permissions | No `CALL_PHONE` (`ACTION_DIAL` only), no `QUERY_ALL_PACKAGES`, no storage perms (SAF + `DownloadManager`). `INTERNET` only. | `PLAY-001` |
| Data safety / privacy policy | Zero telemetry by default makes this form nearly empty — a real advantage. Privacy policy URL required in-app and on the listing. | `PRIV-001`, `PLAY-003` |
| Android 15+ platform requirements | Edge-to-edge enforced, predictive back, 16 KB page sizes (no NDK deps planned). | `PLAY-001` |
| Signing | Implement the config, don't just document it (hermes' gap): `WEBORA_UPLOAD_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD` resolved env → gradle property → `local.properties`. AAB output. | `PLAY-002` |

Sources: [Play target API levels](https://support.google.com/googleplay/android-developer/answer/11926878),
[Developer Program Policy](https://support.google.com/googleplay/android-developer/answer/16933379).

---

## Concept §60 open questions — decided

Recorded in `docs/adr/` so implementation never re-litigates them.

| # | Question | Decision |
|---|---|---|
| 1 | HTTPS required? | Yes for SiteSkin mode. `debugRelease` variant relaxes for local dev (hermes `HTTP-DEV-001` pattern) |
| 2 | Manifest redirects? | Same-origin only, max 2 hops |
| 3 | Trust subdomains? | No |
| 4 | Cross-origin assets? | No — same-origin only in v1 |
| 5 | Active nav state? | Browser-computed: exact path, then longest glob match |
| 6 | Collapse address bar? | **No** — see decision 1 above |
| 7 | Minimum indicator? | TLS state + registrable domain, browser typography, always |
| 8 | Theme cache? | Key `origin + schemaVersion`; TTL `min(Cache-Control, 24h)` |
| 9 | Global SiteSkin off? | Yes, Settings |
| 10 | Per-site off? | Yes, overflow menu + the first-use sheet |
| 11 | Dark variants? | v1.1. v1.0 derives dark from primary |
| 12 | Localisation? | v1.1 — `labels` keyed by BCP-47 |
| 13 | Badges in MVP? | No — needs the JS bridge |
| 14 | Signed manifests? | No. `ADR-012` records the future path |
| 15 | First-use confirmation? | **Yes** — see decision 2 above |
| 16 | Verified-domain indicator? | Post-MVP |
| 17 | Developer validation page? | Yes — `siteskin-lint` (`SPEC-003`) + in-app inspector (`DEVX-001`) |
| 18 | Cookie isolation? | Standard WebView cookie jar in v1 |
| 19 | Incognito? | Post-MVP |
| 20 | Uploads/downloads? | SAF + `DownloadManager` (`BROWSE-005`) |

ADR set: 001 WebView engine · 002 `.well-known` discovery · 003 manifest-as-data · 004 origin
binding · 005 no JS bridge in MVP · 006 **browser-owned security chrome, non-suppressible domain** ·
007 allow-listed actions · 008 sealed `BrowserMode` · 009 non-blocking discovery · 010 always fall
back to regular mode · 011 **first-use consent** · 012 signed manifests deferred.

---

## What this session writes

**`denrzv/webora`**, branch `claude/review-work-artifacts-unjbah` (the branch the restoring session
was assigned; the originally-planned `claude/skinsite-browser-plan-naouua` was never pushed):
- **`docs/DEVELOPMENT_PLAN.md` — this document, saved in full**, so it is referable from the repo
  rather than from a session transcript. It is the project's north star: the naming decision, the
  two concept amendments, the milestone/ticket index, the §60 answer table, the Java and Play
  constraints. `docs/ROADMAP.md` stays as the short milestone tracker that gets ticked off; this
  file is the reasoning behind it and changes only when a decision changes.
- `docs/` — templates, `.active_ticket`, twelve `adr/ADR-0NN.md`, and a
  `prd/<TICKET>.prd.md` at `Status: PRD_READY` for every epic above
- `spec/SPEC.md` at draft — the normative text lands under `SPEC-001`
- `.claude/commands/` (17, mirroring hermes verbatim where they apply), `.claude/settings.json`
  with the **PreToolUse** gate
- `scripts/`, `.github/workflows/`, `.pre-commit-config.yaml`, `.gitleaks.toml`
- `gradle/libs.versions.toml`, `settings.gradle.kts`, `config/detekt/detekt.yml`, and skeletons for
  `:app` / `:siteskin-core` / `:siteskin-lint` that **compile and pass an empty test run**
- `CLAUDE.md`, `AGENTS.md`, `PROJECT_RULES.md`, `conventions.md`, `workflow.md`

**`denrzv/bloom-flowers`**, same branch name: repo skeleton, `INTEGRATION.md` outline, and the
`.well-known/siteskin.json` that `SPEC-001` will validate against. Full site under `DEMO-001`.

`denrzv/skinsite` is left untouched — nothing to migrate, it never received a commit.

Commits follow `<TICKET> TASK-N: <short>`; squash-merge to main as `<TICKET>: <title>`. No PR unless
you ask.

**Stops before:** any `:siteskin-core` implementation, any Composable, the normative spec body, the
Bloom Flowers pages. Those are M1+ and run through the `/idea → /plan → /tasks → /implement` loop.

---

## Verification

1. **Gates are real, not baselined into silence.** hermes validated its detekt gate with a negative
   control; do the same. Add a throwaway file with a deliberate violation, confirm
   `./gradlew detekt` fails, delete it. Same for `gate-workflow.sh`: with `docs/.active_ticket` set
   to a ticket whose PRD is `Status: DRAFT`, an `Edit` must be blocked (exit 2); flip to
   `PRD_READY`+`RESEARCH_READY`+`PLAN_APPROVED`+`TASKLIST_READY` and it must pass.
2. **Android SDK provisioning.** This container has JDK 21 + Gradle but **no Android SDK**, so
   `:app` cannot assemble here today. `FOUND-003` adds a SessionStart hook (per the
   `session-start-hook` skill) that installs `cmdline-tools` + `platforms;android-36` +
   `build-tools;36.0.0` and writes `local.properties`. Verify by `./gradlew :app:assembleDebug` in a
   fresh session.
3. **Core is genuinely Android-free** — `./gradlew :siteskin-core:test` must pass with
   `ANDROID_HOME` unset. If it needs the SDK, a dependency leaked; that is a bug in `FOUND-002`.
4. **Java target is verified, not assumed** — `./gradlew :app:assembleDebug` must succeed with
   toolchain 25 / `jvmTarget = 21`, because that task is what actually runs D8. `compileDebugKotlin`
   passing proves nothing about dexability. Record the working combination in `FOUND-002`.
5. `bash scripts/pre-commit-check.sh` green end to end (gitleaks, shellcheck, `./gradlew test`,
   detekt).
6. CI green on the pushed branch — `ci.yml` guardrails + tests, `security.yml` osv-scanner.
7. **Artifact completeness:** every ticket in the index has a `docs/prd/<TICKET>.prd.md` whose
   acceptance criteria end with the command gate, matching hermes' convention, and
   `docs/DEVELOPMENT_PLAN.md` is committed.
