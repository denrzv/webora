# BROWSE-005 research

Status: RESEARCH_READY

## Existing seams

`HardenedWebViewClient` currently returns `true` for every non-HTTP(S) top-level navigation and has a pure `shouldKeepNavigationInWebView` policy seam. `HardenedWebView` constructs the client and controller but has no `WebChromeClient`, download listener, or external-navigation callback. `MainActivity` owns the Compose root and is the appropriate Activity Result API composition root. `BrowserScreen` owns browser state and can present confirmation without giving the page direct access to Android.

The app already depends on AndroidX Activity Compose and Material 3, so SAF launchers and a browser-owned confirmation dialog need no new dependency. Android `DownloadManager` is available at minSdk 26 and can use public Downloads without adding a dangerous permission. `ACTION_DIAL`, `ACTION_SENDTO`, and `ACTION_VIEW` provide narrower external contracts than generic intent parsing; `Intent.parseUri` must not be used.

## Origins and remote influence

The current top-level URL and download URL are page-controlled remote input. File chooser accept strings are also page-controlled hints, not authority. A page may request one of the supported flows, but it cannot choose an Android package/component, intent flags/extras, a download filesystem path, runtime permissions, or an unrestricted picker contract.

HTTP(S) navigation stays renderer-owned. Only exact `mailto`, `tel`, and `geo` schemes may become typed external-navigation data, and launch requires a separate browser-owned confirmation. Download policy accepts absolute HTTP(S) URLs only. Upload results accept `content:` URIs selected by the system picker, at most one.

## Relevant code

| Path | Why it matters |
|---|---|
| `web/HardenedWebViewClient.kt` | Top-level scheme interception and pure policy seam. |
| `web/HardenedWebView.kt` | WebView client, chrome client, and download-listener wiring. |
| `browser/BrowserScreen.kt` | Browser-owned confirmation UI and capability callbacks. |
| `MainActivity.kt` | Activity Result launchers and Android service adapters. |
| `browser/AddressResolver.kt` | Existing strict HTTP(S) resolution policy. |
| `AndroidManifest.xml` | Network/download declarations; no new dangerous permission should appear. |

## Risks and obligations

- Generic intents can smuggle components or fallback URLs: parse only a closed scheme set and construct each intent explicitly.
- Silent app switching is deceptive: interception emits pending data only; confirmation is a distinct user action.
- `DownloadManager.Request` can throw on malformed/unsupported input: validate in pure policy before construction and surface a browser-owned failure.
- Accept types may be wildcards, malformed, or huge: normalize against a small MIME allow-list, bound input count/length, and fall back to a conservative picker type.
- WebChromeClient callbacks can leak or remain unresolved: cancellation and replacement must complete the old callback with null.
- A selected URI is capability-bearing: accept only `content:` and do not request persistable access when the page needs a one-shot upload.

## Open questions

Authentication/cookie forwarding and multiple-file upload are deliberately deferred because each expands the privacy/capability contract. This ticket implements safe baseline transfers only.
