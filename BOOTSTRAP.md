# Bootstrap — remaining manual steps

The `FOUND-*` scaffolding is in place, but a few steps could not be completed automatically because
the session that generated this repo had write-side git, `chmod`, and settings-file operations
blocked by a permission control. None of them are complicated; all of them are one-time.

Do these in order. Everything after step 1 is verifiable.

## 1. Rename the GitHub repository

This repo is `denrzv/skinsite`. It should be **`denrzv/webora`** — every identifier inside it
already says Webora (`applicationId app.webora.browser`, Gradle root project `Webora`).

GitHub → Settings → Repository name → `webora`. GitHub keeps redirects for the old name, so existing
clones and remotes continue to work. The empty `denrzv/webora` repo must be deleted or renamed first,
since the name is taken.

Then update your local remote:

```bash
git remote set-url origin https://github.com/denrzv/webora
```

## 2. Generate the Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` is committed, but the `gradlew` scripts and
`gradle-wrapper.jar` are not — generating them requires running Gradle.

```bash
gradle wrapper --gradle-version 9.1.0
chmod +x gradlew
```

Nothing builds until this is done; CI expects `./gradlew` to exist.

## 3. Make the scripts executable

```bash
chmod +x scripts/*.sh
git update-index --chmod=+x scripts/*.sh
```

The second line matters: without it the executable bit is not recorded in git and CI checkouts get
non-executable scripts. (`scripts/pre-commit-check.sh` is invoked as `bash scripts/...` everywhere,
so this is belt-and-braces — but the hooks call the scripts directly.)

## 4. Install the Claude Code settings

```bash
cp docs/claude-settings.example.json .claude/settings.json
```

Strip the leading comment block — `settings.json` must be valid JSON.

This wires two hooks: `SessionStart` runs `scripts/session-start.sh` (provisions the Android SDK in
cloud sessions), and `PreToolUse` runs `scripts/gate-pretool.sh` (the AIDD artifact gate). It was
left as an example rather than installed directly because it grants tool permissions, and that is a
decision to make deliberately rather than inherit.

## 5. Install the pre-commit config

```bash
cp docs/pre-commit-config.example.yaml .pre-commit-config.yaml
pipx install pre-commit && pre-commit install
```

Same reasoning: it defines executable hooks. CI's `guardrails` job runs `pre-commit run --all-files`
and will fail until the file exists at the root.

## 6. Verify the toolchain

```bash
# Core is Android-free — this must pass with no SDK installed at all.
ANDROID_HOME= ANDROID_SDK_ROOT= ./gradlew :siteskin-core:check

# This is the task that actually runs D8. See CLAUDE.md § Java version.
./gradlew :app:assembleDebug

./gradlew detekt
bash scripts/pre-commit-check.sh
```

If `assembleDebug` fails on the bytecode target, drop `jvmTarget` and `sourceCompatibility` /
`targetCompatibility` from 21 to 17 in `app/build.gradle.kts` and
`siteskin-core/build.gradle.kts`, and record the outcome in the `FOUND-002` tasklist. Java 21 is
the expected ceiling but Google publishes no explicit D8 maximum, so it is an empirical result.

## 7. Prove the gates are real

Both of these are worth doing once. A gate you have never seen fail is indistinguishable from a
gate that does nothing.

**Detekt:** add a throwaway file with a deliberate violation (a method with cyclomatic complexity
over 10 is easiest), confirm `./gradlew detekt` fails, delete it.

**Artifact gate:** set `docs/.active_ticket` to `SPEC-001`, confirm
`bash scripts/gate-workflow.sh` exits 2 because the plan and tasklist are still `DRAFT`. That is the
gate working. It currently reads `BOOTSTRAP`, which bypasses it — remove that once `SPEC-001`
starts.

## 8. Then start the real work

```
/idea SPEC-001 "SiteSkin Manifest v1.0"
```

The PRD is already written at `docs/prd/SPEC-001.prd.md` (`Status: PRD_READY`), so this becomes
`/plan SPEC-001`.

---

## Also outstanding

- **`denrzv/bloom-flowers`** is empty. Its scaffolding (`INTEGRATION.md`, `.well-known/siteskin.json`)
  is `DEMO-001`; the manifest there is also acceptance criterion 7 of `SPEC-001`.
- **Domain purchase and DNS.** See `docs/DEVELOPMENT_PLAN.md` § Hosting. Four distinct demo origins
  are what make `SKIN-004`'s transition tests possible, and the apex must serve the privacy policy
  before `PLAY-003`.
- **Launcher icons** are omitted from `AndroidManifest.xml` on purpose — they are a `PLAY-003`
  deliverable and placeholder art would only be thrown away.
