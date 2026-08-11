# DIST-001: Implementation plan
Status: PLAN_APPROVED

## Overview
One workflow and one document. Build `:app:assembleDebug` on demand, attach it to a GitHub Release
under a name carrying the version and commit, and write install instructions a non-developer can
follow. No application change.

## Flow
`workflow_dispatch` → checkout → JDK + Android SDK (mirroring CI's `android` job) →
`:app:assembleDebug` → rename to carry version and short SHA → create Release with the asset.

## Data
No trust boundary, no storage, no keys. `GITHUB_TOKEN` with `contents: write` is the only credential,
and it is issued per run by Actions rather than configured as a secret.

## Security
- The artifact is a **debug** build: debuggable, default debug key, `DEVX-001` inspector present.
  Appropriate for named recipients, not for general distribution, and `INSTALL.md` states this
  rather than leaving it to be discovered.
- No signing config is added, so there is no keystore and no secret to leak.
- The application id is `app.webora.browser.debug`, confirmed against the built APK, so installing it
  cannot overwrite or be overwritten by a future release build.
- `INTERNET` is the only permission, confirmed against the built APK.
- `debugRelease`'s cleartext relaxation is in its own source set and does not apply here.

## File-by-file plan

### New: `.github/workflows/release-apk.yml`
`workflow_dispatch` only, with an optional `notes` input. Mirrors the `android` CI job's toolchain
setup. Renames the APK to `webora-<versionName>-<shortsha>-debug.apk`, then uses
`softprops/action-gh-release` pinned to a major tag with `GITHUB_TOKEN`. Fails if the APK is missing
rather than publishing an empty Release.

### New: `docs/INSTALL.md`
What the build is and is not, the unknown-sources prompt named in advance, how to check the download,
what to open first to see SiteSkin working, and the private-repository caveat with its options.

### Modified: `docs/ROADMAP.md`
Tick `DIST-001`.

## Tests
No unit test applies — the deliverable is a build artifact and a document. Evidence instead:

| Check | Method |
|---|---|
| APK builds | `./gradlew :app:assembleDebug` — done, 14 MB |
| Identity is as claimed | `aapt2 dump badging` — `app.webora.browser.debug`, `0.1.0`/`1`, minSdk 26, targetSdk 36, `INTERNET` only |
| Workflow is well-formed | YAML parse |
| Gate | `bash scripts/pre-commit-check.sh` |

**Not verified here:** that the APK installs and runs. No device or emulator in this environment;
that is the owner's manual step, and `INSTALL.md` is written to make it a short one.

## Rollout / versioning
`versionName` stays `0.1.0` and `versionCode` stays `1`. Bumping them is a real decision for when
there is a second build worth telling apart by version rather than by commit.

`workflow_dispatch` requires the workflow file to be on the default branch, so the workflow cannot be
triggered until this branch merges to `main`.

## Open questions
Repository visibility, per the research note.
