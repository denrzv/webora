# CORE-006: Research
Status: RESEARCH_READY

## Question
How can core select one active trusted navigation item from the browser-observed current URL while
implementing the restricted SiteSkin glob grammar without regex execution or Android dependencies?

## Origins involved
- The current page has an HTTP(S) origin observed by the browser, but matching considers only its
  parsed path. The origin must not be interpreted as matchable path text.
- Navigation patterns are origin-relative and already schema/security validated before becoming
  `NavigationItem` values. No asset origin or network access is involved.

## Manifest-controlled surface
The manifest may provide zero or more `match` path patterns on each normalized navigation item and
choose their order. It can therefore propose which item corresponds to a path, subject to the fixed
grammar and browser-owned precedence. It cannot supply an active boolean, regex, URL authority,
query/fragment rule, or executable callback.

## Browser-owned remainder
The browser supplies the observed current URL and owns parsing, grammar interpretation, ranking,
the no-selection fallback, and eventual UI rendering. A pure `NavMatcher` returning at most one
trusted item makes the decision explicit without letting the manifest directly mutate chrome.

## Relevant code
| Path | Why it matters |
|---|---|
| `spec/SPEC.md` §7.1 | Normative path-only grammar and deterministic precedence. |
| `spec/siteskin-1.0.schema.json` | Proves patterns are origin-relative strings beginning with one `/`. |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/model/SiteSkinConfiguration.kt` | Defines trusted `NavigationItem.match` and preserves document order. |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/SiteOrigin.kt` | Existing defensive `java.net.URI` parsing idiom; origins remain separate from paths. |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/TrustedModelApiTest.kt` | Guards trusted-model construction boundaries. |
| `conventions.md` | Requires pure JVM core and security negative controls. |

## Prior art
- `SPEC.md` §7.1 fixes `*`, `**`, literal metacharacters, exact-over-glob, longest literal prefix,
  document-order ties, and no default.
- CORE-004 already normalizes and bounds the navigation collections and match lists. CORE-006 must
  consume that trusted model rather than repeat validation.
- ADR-003/ADR-005 in `docs/adr/README.md` keep manifest data declarative and exclude a JavaScript
  bridge. A handwritten token matcher preserves that boundary.
- `java.net.URI`, not `URL`, is the repository origin-parsing rule; it avoids DNS behavior and
  provides raw path/query/fragment separation.

## Risks
- Catastrophic or exponential matching → use an iterative token/segment algorithm, never compile
  manifest strings as regex, and test adversarial repeated wildcards.
- `**` accidentally matching partial segments → define it only as a complete segment token and pin
  zero-, one-, and many-segment cases.
- Exact precedence misclassified when a pattern contains literal regex metacharacters → classify
  only `*` as glob syntax and treat `?`, brackets, braces, and backslashes literally.
- Current URL parsing failure or opaque URI → return no item and never throw/default.
- Cross-origin page selection → accept the observed URL for path extraction only; origin activation
  remains the app/runtime's responsibility and is out of scope.

## Open questions
Whether `**` is valid outside a complete segment is not stated explicitly by the token table's
surface grammar. The phrase "whole path segments" and sole planned form `/cart/**` imply that only
a complete `**` segment receives recursive semantics; other adjacent stars can follow `*` rules.
The plan must state and test this interpretation rather than silently expanding the language.
