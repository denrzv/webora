# Security Review: CORE-004
Status: PASS

## Scope

Origin-bound security normalization of untrusted SiteSkin manifest values into the only public
trusted configuration type.

## Threat model

- Attacker: hostile website publishing crafted URLs, actions, icons, colours, identifiers, and
  unbounded UI content.
- Attacker: delegated subdomain or alternate-port service attempting to inherit a parent skin.
- Asset: browser capability boundary, legibility of browser-owned chrome, and isolation between
  origins.

## MASVS focus areas (relevant only)

- Platform interaction: PASS — output actions are inert data; no intents, permissions, bridge, or
  native execution is introduced.
- Network security: PASS for this seam — HTTPS exact-origin/effective-port policy is fail-closed;
  fetching, TLS transport, redirects, and MIME enforcement remain NET tickets.
- Data minimization / PII: PASS — no logging, telemetry, storage, or network emission.
- Resilience: PASS — bounded collections/strings, deterministic colour work, localized item drops,
  and no Android/runtime dependency.

## Findings & recommendations

The review's diagnostic-order finding was resolved by TASK-FIX-1 and a combined regression test.
CORE-002 must preserve the documented adapter contract: prepend unknown-field warnings and never
expose a second trusted constructor. CORE-005 must keep normalized values inert until its sealed
allow-listed resolution step.

## Security DoD
Status: PASS
