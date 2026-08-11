# DIST-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DIST-001.prd.md`
- Research: `docs/research/DIST-001.md`
- Plan: `docs/plan/DIST-001.md`

## Tasks

- [x] TASK-1: Release workflow and install instructions
  - New: `.github/workflows/release-apk.yml`, `docs/INSTALL.md`
  - Modified: `docs/ROADMAP.md`
  - Acceptance: `workflow_dispatch` builds `:app:assembleDebug` with the same toolchain setup as
    CI's `android` job, renames the APK to carry `versionName` and the short commit SHA, and creates
    a Release with it attached using only `GITHUB_TOKEN`; the job fails rather than publishing a
    Release with no asset. `INSTALL.md` names the unknown-sources prompt in advance, says the build
    is a debug build and what that means, and records that release assets on a private repository
    are not reachable by a signed-out visitor, with the options. No change to `app/build.gradle.kts`
    or any source file.
  - Tests: YAML parse; `bash scripts/pre-commit-check.sh`
  - Evidence: APK built locally — 14 MB, `app.webora.browser.debug`, `versionName` `0.1.0`,
    `versionCode` `1`, `minSdkVersion` 26, `targetSdkVersion` 36, `uses-permission: INTERNET` only,
    read with `aapt2 dump badging` rather than assumed from source.
  - Result: `release-apk.yml` is `workflow_dispatch`-only, mirrors CI's `android` toolchain setup
    step for step, fails with an explicit error if the APK is missing, and pins
    `fail_on_unmatched_files: true` so an empty Release cannot be published. `versionName` is read
    out of `app/build.gradle.kts` rather than duplicated — parse verified against the real file.
  - Note: `workflow_dispatch` requires the workflow file to be on the default branch, so this cannot
    be triggered until the branch merges to `main`. The APK built here locally is the same artifact
    the workflow produces.
  - Deviation: `INSTALL.md` documents the private-repository caveat in a table of three options
    rather than picking one. The choice is repository visibility, which is the owner's and outside
    a distribution ticket — but a link that 404s is the most likely way this ticket fails in
    practice, so it is stated where it will be read before a link is sent.
