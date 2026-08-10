# Review: DEMO-001
Date: 2026-08-10
Status: RESOLVED

## Summary

The ticket did what a demo ticket is supposed to do and rarely does: it found a defect that no
amount of fixture work could have found. `home` declared no `match`, every guard in the corpus was
satisfied, and the reference integration's own landing page highlighted nothing. That is the
ticket's real output; the site around it is what made the question askable.

Scope is honest about its one blocked criterion (`siteskin-lint` against a live origin needs DNS
that does not exist yet) rather than quietly reporting it green, and the workflow that would run it
is manual so it cannot claim success prematurely.

One finding, in `tools/check-routes.py`: it accepts paths that escape the repository root. Harmless
in this repository and not harmless in the repositories `INTEGRATION.md` invites people to copy it
into.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | Untouched. No `:siteskin-core` production change; the only new core file is a test. |
| Protocol surface | Unchanged, as scoped. `spec/diagnostics.json`, `spec/versions.json`, the schema and both allow-lists are byte-identical — verified by `git diff --stat` against the merge base. |
| Where the new guard lives | `ReferenceIntegrationNavTest` is in `:siteskin-core` because `NavMatcher` is, and the assertion is about what the browser does. The filesystem half (`check-routes.py`) is in the other repository because only that repository has the filesystem. Correct split. |
| Fixture-vs-deployment origins | `bloomflowers.example` stays in `spec/`, `bloomflowers.webora.app` appears only in shipped values and docs. The manifest body names no origin at all, which is what lets one file be both fixture and served copy. |
| Route layout | Directory layout is argued from host behaviour rather than taste, and the argument is reproducible — the three-host table in the research note was verified for the `python3 -m http.server` row during TASK-3. |
| Duplication of `NET-003` limits in `check-routes.py` | Accepted. The alternative is a dependency from the demo repository onto the browser, which would defeat the "one file, no toolchain" claim the reference exists to make. The numbers are commented as a copy with the reason. |

## Security

| Property | Assessment |
|---|---|
| Origin binding | Unchanged. The reference site is validated on the same path as any other origin; being in `defaultSuggestedSites` confers no trust, and the catalogue is compiled and resource-keyed as `BROWSE-003` established. |
| Browser-owned chrome | Unchanged and, more usefully, now *documented from the site owner's side*. `INTEGRATION.md` §7 states that no field hides the domain or TLS indicator and that none will be added. |
| Allow-lists | Untouched. The reference uses `internal_url` and `phone`, both already allow-listed, and five already-known icon names. |
| Permission escalation | None. The `phone` quick action is the deliberate demonstration that a site expecting a permission does not get one. |
| Third-party surface in the demo | None. No CDN, hosted font, icon set, analytics, cookie, form, storage or service worker; verified by grep over every `src`/`href` in the four pages. The single off-origin reference is a hyperlink to the guide on GitHub, which is a user-initiated navigation and not a subresource request. |
| Manifest change | Additive within `1.0` — an OPTIONAL `match` array on one item. No version implication; `SPEC.md` §4 is untouched. |
| Five-copy integrity | Verified end to end: `cmp` confirms byte-identity between fixture and served copy, and the recomputed hash equals `BLOOM_FLOWERS_SHA256`. Both independent guards were confirmed to fail on a one-sided edit. |

## Findings

### FINDING-1 · Medium · route check accepts paths outside the repository
**File:** `denrzv/bloom-flowers:tools/check-routes.py:78` (`served_file`)

`served_file` joins a manifest-supplied path onto the repository root and asks whether the result is
a file. `Path.is_file()` resolves `..` against the real filesystem, so a manifest path that climbs
out of the repository is reported as *served*:

```
$ cat .well-known/siteskin.json
{ …, "site": { "homeUrl": "/../../../etc/hostname" },
  "bottomNavigation": [{ …, "action": { "type": "internal_url", "url": "/../../../etc/passwd" } }] }

$ python3 tools/check-routes.py
[routes] OK -- 2 manifest path(s) all resolve to served files
```

In *this* repository the exposure is nil: the manifest is checksum-pinned to a fixture that
webora's `UrlResolver` tests already prove contains no traversal. But the guard's whole purpose is
to catch what the checksum cannot, and `INTEGRATION.md` §5 explicitly invites site owners to copy
the script into repositories where no checksum and no `UrlResolver` exist. There it silently
converts a traversal into a pass.

Current:

```python
    relative = path.lstrip("/")
    if relative in ("", "/"):
        candidates = [root / "index.html"]
    else:
        base = root / relative
        candidates = [base, base / "index.html"]

    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None
```

Fix — resolve, then require containment, so a path that leaves the tree is "not served" rather than
"served":

```python
    relative = path.lstrip("/")
    if not relative:
        candidates = [root / "index.html"]
    else:
        base = root / relative
        candidates = [base, base / "index.html"]

    for candidate in candidates:
        resolved = candidate.resolve()
        if not resolved.is_relative_to(root):
            continue  # climbed out of the site; a static host would not serve this either
        if resolved.is_file():
            return resolved
    return None
```

`root` is already `.resolve()`d in `main`. The `relative in ("", "/")` branch also carries a dead
arm — `lstrip("/")` cannot leave a `"/"` — folded into the same fix.

## Not findings

- **`check-routes.py` requires a `match` pattern's literal prefix to be a served route.** For a
  pattern like `/products/*/reviews` that prefix (`/products/`) need not be a page, so the check
  could report a false failure on a site organised that way. Kept deliberately: on this site every
  literal prefix *is* a real route, a false failure is loud and one line to fix, and the alternative
  — checking only glob-free patterns — would have skipped `"/cart/**"`, which is the pattern whose
  prefix is most worth checking. A guard that errs loudly beats one that errs silently.
- **`"/cart/**"` selecting `/cart` looks like an off-by-one.** It is specified: `**` matches zero or
  more whole segments, so a trailing `**` is satisfied by an already-complete path. Pinned by
  `ReferenceIntegrationNavTest` and called out in both `INTEGRATION.md` §4 and `CLAUDE.md`.
- **The logo is committed as a binary alongside its generator.** Both are needed: the site serves
  the PNG, and the script is what makes it reviewable. Reproducibility was verified — regenerating
  yields a byte-identical file (`a0fc2a9b…`), so the pair cannot drift unnoticed.
- **`SuggestedSite` now points at a host that does not resolve yet.** Deliberate, and better than
  the alternative it replaced: `bloomflowers.example` is *reserved* and can never resolve, while
  `bloomflowers.webora.app` becomes correct when DNS lands. A failed navigation is `BROWSE-004`'s
  existing error path, not a new one.
- **`README.md` presents the live URL as live.** Accepted for a demo repository whose `CNAME` is
  committed; the claim becomes true with a DNS record and no further change.
- **`siteskin-lint.yml` checks out `denrzv/webora` without a token.** Fine while that repository is
  public; if it is ever private the job fails loudly at checkout, which is the correct failure for a
  manually triggered job.
- **`INTEGRATION.md` duplicates limits and allow-lists that `SPEC.md` owns.** Intentional — it is
  the adoption document, and sending a site owner to a 700-line normative spec to learn that a label
  is capped at 32 characters is the failure this ticket exists to fix. Every value was read out of
  `SPEC.md` and `SiteSkinLimits` while writing, not recalled.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `nav/ReferenceIntegrationNavTest.kt` | 3 | Eight routes against expected ids; four undescribed paths select nothing; the route table is asserted to cover every item in the manifest, so a shrunken fixture cannot narrow the test silently. |
| `spec/SpecCorpusTest.kt` | existing | SHA pin updated; negative-controlled from both sides. |
| `SecurityConformanceTest` | existing | Canonical result — the third guard on the five copies, found during implementation rather than planned. |
| `OriginCorpusTest` | existing | Still green after the fixture edit; every manifest URL resolves in-origin. |
| `tools/check-routes.py` | run locally + CI job | 11 manifest paths; negative-controlled by hiding `catalog/index.html` (fails 3 ways) and by corrupting the PNG signature. |
| `manifest-guard.yml` | CI | `sha256sum --check`; negative-controlled by a one-word edit. |
| Snippets in `INTEGRATION.md` | throwaway run | Both accepted by `SiteSkinValidator` with zero diagnostics. |

**Not covered, and correctly so:** on-device rendering of mockup screen 3, the consent dialog, and
tab-highlight transitions. `AGENTS.md` rules out a software-only emulator here; recorded in QA as an
environment limitation rather than a passing claim.

## Verdict

**RESOLVED.** `FINDING-1` was fixed in `DEMO-001 TASK-FIX-1` (`denrzv/bloom-flowers`, commit
`e446521`) and re-verified from three directions: the traversal manifest now fails both of its
paths, the real site still passes all 11, and the pre-existing hidden-`catalog/index.html` control
still fires. Everything else was sound; the finding touched one function in the demo repository and
neither the browser, the protocol nor the corpus.
