# SPEC-003: Research
Status: RESEARCH_READY

## Question
How can a live-origin CLI fetch and validate the complete SiteSkin contract through one core-owned
activation pipeline, with deterministic diagnostics and exit codes, without creating a second
validator or weakening the origin boundary?

## Origins involved
- The sole trusted input to discovery is the user-requested origin. It must canonicalize as one
  absolute HTTPS `SiteOrigin`; paths, queries, fragments, userinfo, and unsupported schemes are not
  alternate ways to select a manifest.
- Discovery targets that exact origin's `/.well-known/siteskin.json`. Redirects may stay on the
  exact scheme/host/effective-port tuple for at most two hops; subdomains and port changes are
  different origins.
- The manifest is validated against the original serving origin. A same-origin redirect can change
  the resource path but cannot change which origin receives the configuration.
- Corpus tests need loopback HTTP because MockWebServer does not provide a production certificate.
  This is a test seam, not a public relaxation of the HTTPS CLI contract.

## Manifest-controlled surface
The response can supply only the declarative fields defined by SiteSkin. It can influence branding,
labels, bounded navigation/actions, and origin-checked URLs after core validation. It cannot choose
the fetch origin, redirect policy, byte/time limits, diagnostic disposition, exit status, terminal
formatting, browser security chrome, native intents, or executable code.

## Browser/tool-owned remainder
The tool owns argument parsing, HTTPS discovery, exact-origin redirects, response lifetime, and
terminal output. Core must own byte bounding, JSON decoding, layer ordering, schema/security rules,
normalization, diagnostic codes, and the only construction path to `SiteSkinConfiguration`. Exit 0
means core returned a trusted configuration; warnings and dropped items are still printed but do
not turn an activatable configuration into failure.

## Relevant code
| Path | Why it matters |
|---|---|
| `siteskin-lint/src/main/kotlin/dev/siteskin/lint/Main.kt` | Current placeholder prints a URL and exits 0 without validating. |
| `siteskin-lint/build.gradle.kts` | Application entry point and existing core/OkHttp dependencies; no tests yet. |
| `siteskin-core/.../manifest/ManifestParser.kt` | Owns the 128 KiB bound, strict UTF-8, DTO decode, and unknown-field warnings. |
| `siteskin-core/.../ManifestValidation.kt` | Owns version-before-schema validation and stable protocol diagnostics. |
| `siteskin-core/.../SecurityValidator.kt` | Only path from schema-valid JSON to trusted origin-bound configuration. |
| `siteskin-core/.../origin/SiteOrigin.kt` | Canonical full-origin type suitable for validating the CLI argument and redirect hops. |
| `siteskin-core/.../SiteSkin.kt` | Publishes discovery path/limits and the `ManifestSource` seam. |
| `spec/diagnostics.json` | Authoritative layer order and dispositions. |
| `spec/fixtures/**` | Complete activation/diagnostic contract, including non-rejecting “invalid” fixtures. |
| `spec/SPEC.md` §§2–4, 10–14 | Discovery, pipeline order, dispositions, diagnostics, canonical result, CLI promise. |

## Existing seams and gaps
- `ManifestParser` and `SchemaValidator` are individually production code, but there is no public
  core orchestrator that executes `parse → version/schema → security` and merges diagnostics.
  Therefore neither the future browser nor the CLI can currently use “the same code path.”
- `ManifestParser.Parsed` exposes a DTO but not the parsed `JsonElement` required by both validators.
  Re-serializing the DTO would erase unknown fields and can change representation; parsing twice
  risks drift. The integrated pipeline needs one bounded read/JSON parse whose raw element and DTO
  feed their respective stages.
- Typed DTO decoding currently classifies some structurally wrong JSON as `SS-E-PARSE`, while the
  normative pipeline assigns well-formed but wrong-shaped JSON to `SS-E-SCHEMA-INVALID`. The core
  orchestration work must preserve raw JSON through schema validation before DTO conversion.
- `SecurityValidator` returns no diagnostic when the caller supplies an invalid serving origin.
  The CLI must reject that argument before fetch; it must not invent a protocol diagnostic for a
  caller error.
- Diagnostic types are duplicated between the parser package and core validation. The pipeline
  needs a single outward result without changing stable wire spellings.
- OkHttp is already intentionally outside core. It defaults to following redirects, so the CLI
  must install explicit redirect handling rather than accepting cross-origin/default unlimited
  behavior.

## Disposition and exit semantics
`invalid/` means “produces diagnostics,” not “must reject.” Registry disposition is authoritative:
transport/parse/version/schema codes reject, while security codes currently drop an item or warn.
Thus corpus expectations containing a canonical `result` must exit 0 after printing diagnostics;
expectations without a result must exit non-zero. This resolves the backlog shorthand without
contradicting `SPEC.md` §10 or rejecting safe degraded configurations.

Operational CLI failures (usage, malformed origin, DNS/TLS/timeout, and non-success HTTP) are not
manifest diagnostics and have no `SS-*` code in `diagnostics.json`. They should use concise
tool-owned messages and documented non-zero statuses; inventing protocol codes would violate the
registry/fixture completeness rule.

## Testing map
- Pure core pipeline tests should drive every corpus body and compare trusted/rejected outcome plus
  exact diagnostic codes/pointers; mutation controls must show stage-order and origin checks fail
  when bypassed.
- CLI unit tests should inject transport/output rather than call `exitProcess`, covering usage,
  origin parsing, result rendering, and status mapping.
- MockWebServer tests should cover the well-known path, same-origin redirects, cross-origin and
  over-hop refusal, status failures, timeouts, and bounded response handling.
- A corpus HTTP harness can serve each fixture at the origin declared by its `.expected.json` only
  if the CLI's internal runner accepts an already-observed test origin/transport. Public CLI HTTPS
  enforcement remains independently asserted.

## Risks
- **False “will activate” promise:** assembling validators only in the CLI creates drift. Mitigate
  with a core-owned total pipeline used by the command and later browser source.
- **Wrong-layer diagnostics:** DTO decoding before schema validation turns structural failures into
  parse failures. Preserve raw JSON and short-circuit in registry order.
- **Redirect origin escape:** OkHttp convenience redirects can fetch attacker bytes. Disable them
  and enforce canonical exact-origin comparison per hop with a two-hop cap.
- **Fixture-directory fallacy:** failing every file under `invalid/` would contradict drop/warn
  dispositions. Derive activation from the canonical-result/trusted configuration.
- **Leaking hostile content:** never echo bodies, URLs beyond the user-supplied origin, exception
  internals, or response headers in normal output.
- **Unbounded execution:** enforce connect/read/call timeouts and retain core's 131,073-byte probe.

## Open questions resolved for planning
- Keep protocol diagnostics and CLI operational errors separate; do not register synthetic `SS-*`
  codes without normative fixtures.
- Expose a testable runner returning an integer; keep `main` as a thin `exitProcess` wrapper.
- Use the Gradle application plugin's generated `siteskin-lint` scripts as the initial distribution
  artifact, with `run` for development; native-image packaging is out of scope.
- Permit HTTP only through an internal/test-injected transport seam. The user-facing command
  accepts HTTPS origins only, matching `SPEC.md` §2.

## Question
What the plan needs decided before it can commit to a trust boundary and a file list.

## Origins involved
- serving origin(s)
- asset origin(s), and why they are same-origin

## Manifest-controlled surface
What a website can influence if this ships as scoped.

## Browser-owned remainder
What must stay browser-controlled, and the affordance that enforces it.

## Relevant code
| Path | Why it matters |
|---|---|

## Prior art
ADRs, spec sections, fixtures and tickets that already decided part of this.

## Risks
- risk → the plan's obligation in response

## Open questions
Carried into `/plan` as explicit unknowns, not silently resolved here.
