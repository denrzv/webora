# ADR-004: Configuration is bound to an origin

Status: ACCEPTED
Date: 2026-08-08
Amends: ADR-004 short form in `docs/adr/README.md`

## Context

A SiteSkin manifest controls native browser chrome. Applying one site's configuration to another
origin would let delegated subdomains, sibling sites, or an HTTP endpoint impersonate the HTTPS
site whose configuration was reused. Origin comparison therefore has to be one browser-owned,
canonical operation rather than a collection of caller-specific string checks.

`ADR-006` also requires browser chrome to show a registrable domain. That display value needs the
Public Suffix List (PSL), but it must never weaken the full-host comparison used for origin binding.

## Decision

1. An origin is the canonical tuple `(scheme, host, port)`. Only `http` and `https` are accepted;
   default ports are normalized, hosts are lower-case ASCII after IDN conversion, and equality
   compares all three tuple members.
2. Subdomains, parent domains, sibling domains, different schemes, and non-default ports are
   different origins. Configuration and manifest redirects are never inherited across them.
3. One trailing DNS root dot is stripped because `shop.example.` and `shop.example` name the same
   host. More than one produces an empty label and is rejected.
4. IPv4 and bracketed IPv6 literals are valid origins for local development. Their registrable
   domain is the complete literal and their mixed-script signal is false.
5. Registrable-domain display uses both the ICANN and PRIVATE sections of a bundled PSL snapshot.
   It is a display property only and does not participate in origin equality.
6. When no PSL rule matches, the complete host is displayed. This deliberately differs from the
   PSL algorithm's implicit `*` rule: an anti-impersonation affordance must fail toward showing
   more of an unknown host, not collapse distinct hosts to the same final two labels.
7. URL parsing uses `java.net.URI`; `java.net.URL` is forbidden because its equality operation may
   perform blocking DNS resolution and leak a lookup.

## PSL snapshot and refresh

The bundled file is
`siteskin-core/src/main/resources/dev/siteskin/core/origin/public_suffix_list.dat`, fetched only
from `https://publicsuffix.org/list/public_suffix_list.dat` as directed by its upstream header.

- Upstream version: `2026-07-25_14-20-03_UTC`
- Upstream commit: `e1b8015c3b2f0f4f8c18659c2480fc1a22c07b20`
- SHA-256: `084a5674d77c1d14900b16da5fc8afee9765af2f00a638552a8c7aa18f44ae81`

To refresh it, download that canonical URL without stripping comments or either section, inspect
the diff and upstream version, replace the resource, then update the version and SHA-256 constants
in `PublicSuffixList.kt` and this ADR. Run `PublicSuffixListTest` and the full pre-commit check. The
test pins both the digest and the `VERSION:` line so an unrecorded or partial replacement fails.

## Consequences

- Every security comparison operates on a canonical value that callers cannot construct directly.
- A stale PSL can display the wrong number of labels, but cannot make two origins compare equal.
- The verbatim snapshot retains its MPL-2.0 notice and provenance and includes PRIVATE suffixes such
  as `github.io`.
- Unknown suffixes may display more labels than the standard PSL algorithm would return. This is
  intentional because the value is an identity affordance, not a cookie boundary.

## Alternatives rejected

**Trust registrable domains or subdomains.** Hosting platforms and delegated subdomains routinely
place unrelated parties beneath one registrable domain. Treating them as one origin enables native
chrome impersonation.

**Use only the ICANN PSL section or a curated suffix set.** This merges unrelated tenants on private
suffixes and silently fails for suffixes absent from the curated set.

**Apply the PSL default `*` rule when nothing matches.** This can render unrelated unknown-suffix
hosts identically. Showing the complete host is the safer failure direction.
