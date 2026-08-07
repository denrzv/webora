# ADR-006: Browser-owned security chrome; the domain is not suppressible

Status: ACCEPTED
Date: 2026-08-07
Amends: concept document §8, §11.3, §60 Q6

## Context

The concept document's manifest schema includes `toolbar.showDomain`, a site-controlled boolean, and
§60 Q6 leaves open whether an integrated site may collapse the address bar entirely.

A SiteSkin manifest already supplies the toolbar title, the brand colours and a logo. If it can
*also* suppress the domain, then everything on screen identifying the site is under the site's
control, and nothing contradicts it.

Concretely: `evil.example` publishes a manifest with `title: "Your Bank"`, a copied logo, and the
bank's palette. The user sees native Android chrome — not a web page, native chrome, which carries
more implicit trust — reading "Your Bank", with no domain anywhere. This is a phishing kit with a
schema.

It is also a commercial risk distinct from the security one. Google Play treats impersonation under
*Deceptive Behavior*, which is enforced by suspension rather than by rejection-and-resubmit. A
browser whose chrome can be dressed as an arbitrary brand is a policy problem the first time anyone
abuses it, and the abuse would not be our bug report to triage — it would be a takedown.

## Decision

1. `toolbar.showDomain` is **not implemented**. The field is ignored if present; the schema does not
   define it.
2. In SiteSkin mode the top bar always renders, in browser-owned typography and contrast:
   - the **registrable domain** (eTLD+1), and
   - the **TLS state indicator**.
3. The manifest may set the toolbar title, subtitle, colours, and a logo confined to a bounded slot
   **beside** the domain — never in place of it.
4. Manifest-supplied colours are contrast-corrected against the browser-owned text before use
   (`CORE-004`). A site cannot render the domain unreadable by choosing a background that matches it.
5. The overflow menu, security sheet and settings entry points are browser-owned and always reachable.

## Consequences

- The brand takeover is slightly less total than the concept mockups imply. The top bar shows both
  brand and domain. This is the correct trade and the mockups should be updated to match.
- We can answer a Play reviewer's "who controls this UI?" with a single screenshot.
- Contrast correction must run before first paint of the skinned chrome, which constrains
  `SKIN-001`'s ordering: validate and correct colours, then theme, then render.

## Alternatives rejected

**Allow `showDomain: false` for verified domains only.** Defers rather than solves — it requires a
verification authority (`ADR-012`), and the failure mode of a mis-issued verification is exactly the
attack above.

**Show the domain only on scroll or on tap.** A security signal that is absent at the moment of
decision is not a security signal.
