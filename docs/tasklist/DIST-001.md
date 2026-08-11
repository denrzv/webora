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

- [x] TASK-FIX-1: Bump `versionCode` on every commit so a demo build installs over the last one
  - Source: field observation — `versionCode` was constant at `1`, so Android refused to install
    one demo build over another and every update needed an uninstall first.
  - Modified: `app/build.gradle.kts`, `.github/workflows/release-apk.yml`, `docs/INSTALL.md`
  - Acceptance: `versionCode` is the commit count reachable from `HEAD`, overridable with
    `-PweboraVersionCode=`; derived through `providers.exec` so the configuration cache still
    records it as an input; the release workflow checks out with `fetch-depth: 0` and fails
    explicitly if the resolved count is ≤ 1. `versionName` is untouched, so the workflow's version
    parse still works.
  - Tests: built and read back with `aapt2 dump badging` — `versionCode='135'` matching
    `git rev-list --count HEAD`, and `versionCode='999'` with `-PweboraVersionCode=999`.
  - Negative control: a `--depth 1` clone of this repository reports a commit count of **1** against
    **135** for a full clone. That is the exact failure `fetch-depth: 0` prevents, and the reason the
    workflow now refuses to publish when the count is ≤ 1 — without the guard, a future checkout
    change would silently reinstate a constant `versionCode` and break the upgrade path again.
  - Deviation: `versionName` was left at `0.1.0` rather than given the commit SHA. The release name,
    tag and asset name already carry it, and embedding it would break the workflow's
    `grep versionName` parse for no gain.
  - Note: the sharing question is settled — the repository stays private, and the APK is passed on
    as a file or by granting repository access. `INSTALL.md` records that instead of the previous
    three-option table.
