# Bootstrap — remaining manual steps

The `FOUND-*` scaffolding is in place. The session that first generated it had write-side git,
`chmod`, and settings-file operations blocked by a permission control, so several steps were left
manual; a later session restored the files into `denrzv/webora` and completed the ones it could.

Status of each step is marked below. What remains is genuinely manual — it needs credentials, an
Android SDK, or a deliberate choice about granting tool permissions.

## 1. Repository name — ✅ done, no action

The original bootstrap landed in `denrzv/skinsite` because attaching `denrzv/webora` was blocked,
and step 1 used to be "rename the repo on GitHub". That is moot: the files now live in
**`denrzv/webora`** directly, and `denrzv/skinsite` never received a commit. Nothing to rename,
nothing to migrate.

## 2. Generate the Gradle wrapper — ✅ done

`gradlew`, `gradlew.bat` and `gradle/wrapper/` are committed. Recorded here because the version
number is a trap worth remembering:

```bash
gradle wrapper --gradle-version 9.3.1
chmod +x gradlew
```

**9.3.1, not 9.1.0.** AGP 9.1.0 refuses to apply on anything older —
`Failed to apply plugin 'com.android.internal.version-check': Minimum supported Gradle
version is 9.3.1`. The AGP version and the Gradle version are unrelated numbers that
happen to look alike.

## 3. Make the scripts executable — ✅ done

`scripts/*.sh` and `gradlew` are committed mode `100755`. If you ever add a script, the second line
is the one that matters — without it the bit is not recorded in git and CI checkouts get a
non-executable file:

```bash
chmod +x scripts/new-script.sh
git update-index --chmod=+x scripts/new-script.sh
```

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

## 6. Verify the toolchain — 🟡 partly done

```bash
# ✅ PASSES. Core is Android-free — verified on a container with no SDK at all.
ANDROID_HOME= ANDROID_SDK_ROOT= ./gradlew :siteskin-core:check

# ✅ PASSES, all three modules, still with no SDK — :app configures and lints fine
# without one; only assembling needs it.
ANDROID_HOME= ANDROID_SDK_ROOT= ./gradlew detekt :siteskin-lint:build

# ❌ NOT YET RUN — needs an Android SDK. This is the task that actually runs D8.
# See CLAUDE.md § Java version.
./gradlew :app:assembleDebug

# ❌ NOT YET RUN — needs gitleaks, shellcheck and an SDK for the `test` step.
bash scripts/pre-commit-check.sh
```

**The toolchain 25 / `jvmTarget` 21 combination is still unproven**, because only `assembleDebug`
invokes D8 and no session has had an SDK yet. Run it first in any session that has one. If it fails
on the bytecode target, drop `jvmTarget` and `sourceCompatibility` / `targetCompatibility` from 21
to 17 in `app/build.gradle.kts` and `siteskin-core/build.gradle.kts` — and note there is now a
**third** place the number appears: `tasks.withType<Detekt>().jvmTarget` in the root
`build.gradle.kts`. Record the working combination in the `FOUND-002` tasklist. Java 21 is the
expected ceiling but Google publishes no explicit D8 maximum, so it is an empirical result.

## 7. Prove the gates are real — ✅ done, both

A gate you have never seen fail is indistinguishable from a gate that does nothing. Both were run
with a negative control; re-run them after any change to the gate machinery itself.

**Detekt** — added a throwaway function at cyclomatic complexity 13, threshold 10:

```
DetektNegativeControl.kt:5:14: The function negativeControl appears to be too complex
based on Cyclomatic Complexity (complexity: 13). Defined complexity threshold for
methods is set to '10' [CyclomaticComplexMethod]
> Analysis failed with 14 weighted issues.
BUILD FAILED
```

Deleted; the build returns to green. The gate fails on the rule the config actually names, which
also confirms the `CyclomaticComplexMethod` / deprecated-`ComplexMethod` landmine is dodged.

**Artifact gate** — `scripts/gate-workflow.sh` exits 2 with `SPEC-001` active (PRD present, plan and
tasklist absent) and exits 2 for an unknown ticket. It exits 0 today only because
`docs/.active_ticket` reads `BOOTSTRAP`, which is the deliberate bypass — remove that once
`SPEC-001` starts, or the gate protects nothing.

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
