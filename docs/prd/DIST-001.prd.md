# DIST-001: Debug APK on a GitHub Release
Status: PRD_READY

## Context / Problem
`SCOPE-001` replaced the Google Play milestone with handing friends an APK. Nothing currently
produces one. `./gradlew :app:assembleDebug` writes a file into `app/build/outputs/`, which exists
on whichever machine ran the build and nowhere a person with a phone can reach.

The gap is distribution, not building. What is missing is a reproducible way to turn a commit into a
downloadable artifact, and the short set of instructions a non-developer needs to get an APK from a
link onto an Android phone — which involves a security prompt they have to be told about in advance,
because Android's "install unknown apps" flow looks alarming when unexpected.

One constraint shapes the whole ticket: **`denrzv/webora` is private.** Release assets on a private
repository require an authenticated GitHub account with access, so a link to one is not something a
friend can open. The build can be automated inside this repository; the *sharing* step cannot be,
without either making the repository public or moving the artifact somewhere else. Pretending
otherwise would ship a workflow that produces an asset nobody can download.

The debug variant is the deliverable by decision, and it carries a property worth naming: `DEVX-001`'s
SiteSkin Inspector is compiled into `debug` and absent from the release variants by construction. A
demo build that can show *why* a manifest was accepted or rejected is more useful to demonstrate
with, not less.

## Goals
1. Turn any commit into a downloadable debug APK through a workflow, so the build is reproducible
   and not a local artifact from one machine.
2. Attach it to a GitHub Release with a fixed, predictable asset name that carries the version and
   the commit it was built from.
3. Write install instructions a non-developer can follow, including the unknown-sources prompt.
4. State plainly that the repository is private and what that means for sharing, with the options
   for resolving it — rather than shipping a link that will not open.
5. Change nothing about the app itself: no signing config, no version bump, no build-type edits.

## Non-goals
- Signing configuration, keystore handling, R8 keep verification, an AAB, or anything else descoped
  with `PLAY-002`.
- Making `denrzv/webora` public. That is the owner's decision; this ticket surfaces it.
- Automatic releases on every push. A demo build is cut deliberately, not on every commit.
- Version bumping, changelog generation or release notes automation. `versionName` stays `0.1.0`.
- Uploading the APK anywhere outside GitHub, or committing a binary into the repository.
- On-device verification. Still no emulator here; whether the APK installs and renders is manual
  evidence the owner gathers.

## User stories
- As the owner, I trigger one workflow and get a Release with an APK attached.
- As the owner, I send a friend a link and a three-line instruction, and they end up with Webora on
  their phone.
- As a friend, I open the link, install, visit the suggested site, and see Bloom Flowers with its
  branded chrome.
- As the owner, I know from the artifact name exactly which commit a given APK came from.

## Acceptance criteria
1. A `workflow_dispatch` workflow builds `:app:assembleDebug` and creates a GitHub Release with the
   APK attached, using only `GITHUB_TOKEN` — no secrets to configure.
2. The asset name carries the version and the short commit SHA, so two APKs from different commits
   are never confused.
3. The workflow runs the same JVM toolchain and Android SDK setup as the existing `android` CI job,
   so a build that passes CI is the build that ships.
4. `docs/INSTALL.md` covers: what the APK is, that it is a debug build, the unknown-sources prompt,
   how to verify the download, and what to open first to see SiteSkin working.
5. The private-repository constraint is documented with its options, and the release notes template
   does not promise a link that a signed-out visitor can open.
6. No change to `app/build.gradle.kts`, any source file, `versionName`, or `versionCode`.
7. A debug APK is produced and its size and identity are recorded as evidence.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: the APK is a **debug** build — `debuggable`, signed with the default debug key,
  and carrying the SiteSkin Inspector. That is appropriate for a build handed to named people and
  inappropriate for general distribution; `INSTALL.md` says so rather than leaving it implied. No
  new permission, no telemetry, and the debug build is unaffected by `debugRelease`'s cleartext
  relaxation, which lives in its own source set.
- Reliability/fallback: the workflow either produces a Release with an attached asset or fails
  loudly. A Release with no asset is worse than no Release.
- Performance: none.
- Accessibility: unchanged; `INSTALL.md` is plain prose with no reliance on screenshots.

## Risks
- **A private repository's release assets need authentication.** A link that 404s for the recipient
  is the most likely way this ticket fails in practice, and no amount of workflow correctness fixes
  it. It must be stated where the owner will read it before sharing.
- **A debug APK looks like a normal app once installed.** Its `applicationId` is suffixed `.debug`,
  so it cannot collide with a future release build — worth confirming rather than assuming.
- **Instructions that skip the scary prompt.** Android's unknown-sources flow is where a
  non-developer stops. If the instructions do not name it in advance, the install fails socially
  rather than technically.
- **Version confusion.** `versionName` is `0.1.0` and `versionCode` is `1` for every build. Without
  the commit in the asset name, two different APKs are indistinguishable.

## Open questions
Whether to make `denrzv/webora` public is the owner's, and is the one thing standing between a
Release and a shareable link. This ticket documents the options and does not decide.
