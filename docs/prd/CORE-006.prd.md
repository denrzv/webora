# CORE-006: Navigation active-state matching
Status: PRD_READY

## Context / Problem

Trusted navigation items can declare path patterns, but core does not yet provide the deterministic
active-item decision required by SiteSkin v1. UI code must not invent its own regex-based or
first-item-default behavior for untrusted manifest patterns.

## Goals

- Add a pure-JVM matcher for trusted navigation items and a browser-observed current URL.
- Implement literal paths plus the restricted `*` and `**` grammar without evaluating regex from
  the manifest.
- Apply the specification's exact, longest-literal-prefix, then document-order precedence.
- Fail closed to no active item for malformed current URLs or no match.

## Non-goals

- Rendering navigation or storing active state in the Android app.
- Changing schema validation, normalized navigation items, or the SiteSkin v1 grammar.
- Matching query strings, fragments, hosts, or origins as path content.
- Adding a JavaScript bridge or accepting general regular expressions.

## User stories

- As a user, I see the navigation item that most specifically describes the page I am viewing.
- As a site owner, I can use exact paths and bounded globs with predictable precedence.
- As the browser, I retain the observed URL and selection algorithm rather than accepting an
  active-item instruction from website code.

## Acceptance criteria

1. Exact literal path matches beat every glob match, regardless of item order.
2. `*` matches zero or more characters within one path segment and never crosses `/`; `**` matches
   zero or more whole segments, including none, with all other characters treated literally.
3. Among matching globs, the longest literal prefix wins; remaining ties select the earliest item.
4. Matching uses only the current URL path after URI parsing, ignoring query and fragment.
5. No match or an invalid/non-hierarchical current URL returns no active item, never a default.
6. The matcher consumes trusted `NavigationItem` values, remains pure JVM, and executes manifest
   patterns with bounded linear matching rather than as regular expressions.
7. Unit tests cover precedence, grammar boundaries, literal metacharacters, malformed inputs, and
   a security negative control.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: the browser-observed URL is authoritative; manifest input cannot run regex or
  select a foreign origin.
- Reliability/fallback: malformed input and no match yield no selection without throwing.
- Performance: matching time is bounded by item, pattern, and path lengths; no backtracking regex.
- Accessibility: expose an unambiguous item result so the UI can announce at most one active item.

## Risks

- Ambiguous `**` segment semantics could diverge from the normative examples unless boundary cases
  are pinned by tests.
- Ranking by total pattern length instead of literal prefix would violate deterministic precedence.

## Open questions

None. `SPEC.md` §7.1 fixes the grammar and precedence.
