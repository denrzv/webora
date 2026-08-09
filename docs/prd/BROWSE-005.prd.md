# BROWSE-005 — External navigation, downloads, and uploads

Status: PRD_READY

## Problem

Regular browsing currently fails closed for every non-HTTP(S) navigation and provides no browser-owned path for downloads or HTML file uploads. Users need these ordinary browser capabilities without allowing remote pages to dispatch arbitrary intents, silently launch other apps, or gain Android permissions.

## Scope

- Classify top-level navigation into renderer-owned HTTP(S), explicitly supported external schemes, and denied input.
- Require browser-owned confirmation before launching supported external destinations and fail safely when no handler exists.
- Route downloads through Android `DownloadManager` with validated HTTP(S) requests and browser-owned user feedback.
- Route WebView file chooser requests through the Storage Access Framework with bounded, allow-listed MIME types and cancellation handling.
- Keep all Android intent construction and activity-result plumbing in thin app-layer adapters around pure, unit-tested policy.

## Out of scope

SiteSkin action dispatch, runtime permissions, background download UI, arbitrary intents or packages, upload capture from camera/microphone, multiple-file selection, download authentication/cookie forwarding, and custom download storage.

## Acceptance criteria

1. Top-level HTTP(S) remains in the WebView; supported `mailto`, `tel`, and `geo` destinations require explicit browser-owned confirmation before an external activity is launched; every other scheme is denied.
2. External navigation uses closed browser-owned intent shapes, never accepts manifest/page-supplied Android intent fields, and handles cancellation or a missing handler without crashing or changing browser mode.
3. HTTP(S) download requests enqueue through `DownloadManager` only after validation, use a browser-controlled destination and notification policy, and reject unsupported or malformed URLs safely.
4. WebView file chooser requests launch a browser-owned SAF picker with normalized allow-listed MIME types, return at most one selected content URI, and reliably resolve cancellation and superseded callbacks.
5. Pure JVM tests include negative controls for arbitrary schemes, malformed download URLs, unsafe MIME types, and unconfirmed external launches; Android test sources compile.
6. No website-controlled flow grants a permission, directly calls a phone number, opens arbitrary components, or bypasses user confirmation.
7. `bash scripts/pre-commit-check.sh` passes.
