# BROWSE-001: Research
Status: RESEARCH_READY

## Existing surface

The app currently has one Compose `MainActivity` rendering a bootstrap screen. AndroidX WebKit
1.15.0 and Compose UI are already dependencies, the manifest already grants `INTERNET`, production
cleartext is disabled, and no WebView or JavaScript bridge exists. The project deliberately forbids
Robolectric, so framework interaction must remain a thin wrapper over pure policy functions and a
small instrumentation assertion.

## Decisions and API map

- ADR-001 selects the independently updated system Android WebView.
- ADR-005 forbids `addJavascriptInterface`; no bridge API or page-owned native object is introduced.
- `WebView.settings` owns JavaScript, file/content access, file-URL access, and mixed-content flags.
- `WebSettingsCompat.setSafeBrowsingEnabled` is guarded by
  `WebViewFeature.isFeatureSupported(SAFE_BROWSING_ENABLE)` for provider compatibility.
- Compose `AndroidView` owns creation and URL updates. The host does not own browser history/state;
  BROWSE-002 will supply that state.

## Trust and origin boundary

The current page is untrusted website input. It may execute ordinary in-page JavaScript, but it
cannot modify browser settings, obtain an Android bridge, read local/content resources, opt into
mixed content, or navigate the renderer to `file:`. HTTP(S) remains renderer-owned. Manifests are
not read in this ticket and influence none of these controls. Browser code exclusively constructs
and applies an immutable policy.

## Files and tests

- `app/src/main/java/app/webora/browser/web/WebViewHardening.kt`: immutable policy, pure navigation
  classification, and thin framework application.
- `app/src/main/java/app/webora/browser/web/WebViewHost.kt`: Compose AndroidView host and hardened
  client.
- `app/src/main/java/app/webora/browser/MainActivity.kt`: replace bootstrap copy with the initial
  hardened host.
- `app/src/test/.../WebViewHardeningTest.kt`: JVM assertions for each policy and URL decision.
- `app/src/androidTest/.../WebViewHardeningInstrumentedTest.kt`: real WebSettings assertions.
- `docs/ROADMAP.md`: reconcile already completed core tickets and later record this ticket.

## Risks

Deprecated file-URL setters are still required because their unsafe behavior must be explicitly
pinned on supported API levels. Safe Browsing availability depends on the installed provider, so
unsupported providers retain platform defaults rather than crashing. Recreating or reloading on
every recomposition would damage browsing, so updates load only when the requested URL differs.
