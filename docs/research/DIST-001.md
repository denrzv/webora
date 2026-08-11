# DIST-001: Research
Status: RESEARCH_READY

## Question
What has to exist for a commit to become an APK on someone else's phone, and which part of that
this repository can actually automate.

## Origins involved
None. This ticket ships a binary; it does not touch discovery, validation or any origin. The
installed app's behaviour against `https://denrzv.github.io` is `DEMO-001`'s subject, not this one's.

## Manifest-controlled surface
None.

## Browser-owned remainder
Unchanged. Worth stating because a demo build invites the assumption that something is relaxed for
demonstration: nothing is. `ADR-006`'s non-suppressible domain and TLS indicator, `ADR-011`'s
first-use consent and `PRIV-001`'s zero telemetry are all in this build exactly as specified.

## What the build actually produces

Verified locally with `aapt2 dump badging`:

| Property | Value | Why it matters |
|---|---|---|
| `package` | `app.webora.browser.debug` | The `.debug` suffix from `app/build.gradle.kts` means this cannot collide with a future release build on the same device. Confirmed, not assumed. |
| `versionName` / `versionCode` | `0.1.0` / `1` | Constant for every build, so **the file name is the only thing that can distinguish two APKs**. The commit SHA has to be in the asset name. |
| `minSdkVersion` | 26 | Android 8.0 and up. |
| `targetSdkVersion` | 36 | Shipped in `FOUND-002`; `SCOPE-001` carved it out of the Play descoping precisely so it would not be lost. |
| `uses-permission` | `INTERNET` only | The permission claim in `DEVELOPMENT_PLAN.md` is now confirmed against a built artifact rather than against source. |
| Size | ~14 MB | Debug builds are unminified; fine for a link, worth stating so nobody expects 3 MB. |

## The constraint that decides the shape of this ticket

**`denrzv/webora` is private.** GitHub serves release assets on a private repository only to
authenticated users with access. A friend clicking the link gets a 404, not a download.

That is not fixable inside a workflow. The options, none of which this ticket takes on its own:

| Option | Cost |
|---|---|
| Make `denrzv/webora` public | The whole source becomes public. Also fixes `siteskin-lint.yml`'s cross-repo checkout in the demo repo. |
| Publish the Release in `denrzv/bloom-flowers` (already public) | Puts the app binary in the demo *site*'s repository, which is the wrong home and muddles a repo whose whole point is being small enough to read. |
| Share the APK file directly | Works today, no repository change, but loses the reproducible link. |

The workflow is worth building regardless — it makes the artifact reproducible and removes "which
machine built this" from the question — but the sharing step needs a decision recorded where the
owner will see it before sending a link.

## Relevant code

| Path | Why it matters |
|---|---|
| `.github/workflows/ci.yml` | The `android` job's toolchain setup is what a release workflow must mirror, so the build that ships is the build CI proved. |
| `app/build.gradle.kts` | `applicationIdSuffix = ".debug"`, `versionName`, `versionCode`. Not modified. |
| `app/src/debug/java/.../inspector/` | `DEVX-001`'s panel, present in this variant by construction and absent from release variants — a feature of the demo build, and asserted by `assertInspectorAbsentFromReleaseVariants`. |

## Prior art
- `SCOPE-001` chose the debug APK and descoped signing/R8/store assets.
- `DEVX-001` established that inspector availability comes from the variant source set, not
  `BuildConfig.DEBUG` — which is why "debug build" reliably means "inspector present".
- `PRIV-001` produced `docs/privacy/DATA_SAFETY.md`; nothing in this build contradicts it.

## Risks
- **A link nobody can open.** → Documented with options; the release notes must not imply otherwise.
- **Instructions that omit the unknown-sources prompt.** → It is where non-developers stop; name it
  before they hit it.
- **Two indistinguishable APKs.** → `versionCode` never changes, so the commit SHA goes in the file
  name.
- **A Release created with no asset attached.** → The workflow must fail rather than publish an
  empty Release.

## Open questions
Making `denrzv/webora` public is the owner's decision and the only thing between a Release and a
shareable link.
