# SPEC-003: siteskin-lint CLI
Status: PRD_READY

## Context / Problem

Site owners need a command-line answer to whether the manifest served by an origin will activate
in Webora. The repository has the production parser, schema validator, and security validator, but
the existing `:siteskin-lint` module is only a scaffold. A separate CLI validation implementation
would inevitably drift from browser behavior and defeat the conformance corpus's purpose.

## Goals

- Provide `siteskin-lint https://site.example` as a JVM command-line tool for site owners.
- Fetch the origin's `/.well-known/siteskin.json` and run the same core validation pipeline used by
  the browser.
- Print stable diagnostic codes and return a process status suitable for local use and CI.
- Prove the command's behavior against the complete shared conformance corpus through a local HTTP
  fixture server.

## Non-goals

- Android UI, an in-app integration inspector, or browser runtime activation.
- Manifest caching, conditional requests, redirect policy for the Android fetcher, or offline use.
- Reimplementing parsing, schema, origin, security, normalization, action, or navigation rules.
- Validating arbitrary local files as a public CLI mode unless research shows it is required to
  exercise the live-origin contract safely.

## User stories

- As a site owner, I can lint my deployed origin and learn whether its manifest will activate.
- As a CI maintainer, I can rely on a stable zero/non-zero exit contract and machine-recognizable
  diagnostic codes.
- As a Webora maintainer, I can change validation policy once in core and have the browser and CLI
  remain aligned.

## Acceptance criteria

1. Running `siteskin-lint https://site.example` fetches
   `https://site.example/.well-known/siteskin.json` and validates it through the production
   `:siteskin-core` parsing, schema, and security-validation path.
2. A manifest that produces an activatable trusted configuration exits `0`; Bloom Flowers' valid
   fixture is covered as the positive control.
3. Fetch, argument, parse, version, schema, and rejecting security failures exit non-zero and print
   every applicable stable diagnostic code without exposing the manifest body.
4. Every `spec/fixtures/invalid/*.json` case is served by a local fixture server and the CLI exits
   non-zero with its expected rejecting diagnostic code; drop-item and warning-only fixtures follow
   the trusted validator's activation result rather than being rejected merely because their
   fixture directory is named `invalid`.
5. The CLI contains no second implementation of SiteSkin validation policy and `:siteskin-lint`
   depends on `:siteskin-core` without introducing Android dependencies into core.
6. Network behavior is bounded by explicit timeouts and response-size enforcement, and unsupported
   input schemes or malformed origins fail safely without a stack trace in normal output.
7. Automated tests cover argument handling, discovery URL construction, HTTP success/failure,
   diagnostic rendering, exit codes, the full corpus, and security negative controls.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: treat responses as untrusted bytes, require an explicit supported origin
  scheme, never print manifest contents, and share core's origin binding and allow-lists.
- Reliability/fallback: network and validation failures produce deterministic diagnostics and a
  non-zero status rather than an uncaught exception.
- Performance: bound connection/read time and bytes consumed; do not buffer beyond core's manifest
  limit.
- Accessibility: keep output plain-text, ordered, and understandable in terminals and CI logs.

## Risks

- “Exit non-zero for every invalid fixture” conflicts with fixtures whose registered disposition is
  warning or drop-item; activation semantics must follow the production result, not directory name.
- A CLI-owned JSON or security check could silently diverge from browser behavior.
- Redirects or URL joining could validate bytes against the wrong serving origin.

## Open questions

- Which transport library and redirect policy best keep the CLI small while preserving an explicit
  origin boundary?
- Should operational failures use existing SiteSkin diagnostic codes, a separately documented CLI
  error vocabulary, or both?
- What distribution entry point is practical for this repository: Gradle `run`, an application
  start script, or a packaged executable archive?
