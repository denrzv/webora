# Review: DEMO-004
Date: 2026-08-15
Status: RESOLVED

## Summary

The documentation tells the M8 story in the intended order: ordinary browsing and local/session
features first, then optional SiteSkin consent, then deterministic restoration of the retained
ordinary tab. It accurately separates installed-app interactions from the four hosted frames and
does not introduce a new product, trust, or evidence seam. No review finding requires a fix task.

## Architecture

| Concern | Assessment |
|---|---|
| Browser/session authority | PASS — the journey uses the existing Home, regular shell, Tabs and tab switcher; it does not imply a second session authority. |
| Local browsing data | PASS — the Favourite is created from a successful observed page and the guide states records are device-local and clearable. |
| Mode handoff | PASS — Bloom activation remains consent/origin-bound and the ordinary tab's chrome returns on selection. |
| System boundary | PASS — Android navigation is OS-owned and explicitly distinguished from Webora's in-app browser controls. |
| Scope | PASS — documentation only; no production, test-harness, protocol, storage, or build behavior changed. |

## Security

| Property | Assessment |
|---|---|
| Origin boundary | PASS — `example.com` and `denrzv.github.io` are distinct exact HTTPS origins and no cross-origin trust is described. |
| Manifest authority | PASS — bounded SiteSkin site navigation is conditional on validation and consent; browser identity/session/local records remain browser-owned. |
| Fail-safe fallback | PASS — unavailable, invalid, or declined Bloom manifests remain regular browsing and the guide rejects Inspector forcing. |
| Evidence integrity | PASS — page decoration is not an acceptance input; four pictured states and uncaptured interactive checks are separated explicitly. |
| Privacy | PASS — recents/history/favourites are described as local, non-telemetry data covered by clear-browsing-data. |

## Findings

None.

## Not findings

- The walkthrough's tab-switch route differs from CI-007's Back/address route. This is intentional:
  both use visible production controls, while the guide demonstrates tab isolation and clearly says
  the hosted frame follows the other route.
- Tabs, Recent sites, and Favourites do not receive new canonical frames. DEMO-004 is a walkthrough
  ticket, and it explicitly refuses to claim those interactions are pictured; CI-007 already owns
  the required ordinary/integrated visual evidence.
- `example.com` is live remote content, but the guide and evidence rely only on browser-observed
  identity, chrome, and rendered availability—not its title, prose, or styling.
- `CLAUDE.md` is unchanged because this ticket applies the already-recorded tab, browsing-record,
  mode-handoff, and evidence conventions rather than establishing a new one.
- A new automated documentation test would add machinery for static prose without improving the
  runtime security controls. Existing source contracts and the full repository gate remain the
  appropriate checks for this documentation-only change.

## Test coverage

| File | Tests/checks | Coverage |
|---|---|---|
| `docs/WALKTHROUGH.md` | term/link checks and source review | three authorities, complete interactive flow, privacy/fallback, exact evidence limits |
| `docs/INSTALL.md` | link and current-label review | discoverability and current Bloom navigation labels |
| `docs/SCREENSHOTS.md` | link/inventory review | four-frame scope versus interactive-only states |
| Repository | `bash scripts/pre-commit-check.sh` | security scan, shell checks, core/app units, release isolation and detekt |

## Verdict

RESOLVED. The documentation is accurate, bounded to shipped behavior, and has no open architecture,
security, privacy, accessibility, or evidence-honesty finding.
