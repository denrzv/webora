# BROWSE-001: Implementation plan
Status: PLAN_APPROVED

## Flow and trust boundary

An untrusted HTTP(S) URL enters a Compose host. The browser creates the WebView, applies a constant
browser-owned hardening policy before any load, installs a client that rejects local-file
navigation, and only then loads the URL. Page and future manifest data have no reference to policy
configuration and cannot weaken it. JavaScript runs only inside the renderer; no Android object is
published into its namespace. Safe Browsing is enabled only through the provider feature API.

## Changes

1. Define an immutable internal settings model and pure URL classification, plus a thin Android
   wrapper that maps every setting to WebSettings and conditionally enables Safe Browsing.
2. Add a reusable Compose `HardenedWebView` backed by AndroidView. Install the restrictive client,
   apply hardening before loading, and avoid duplicate loads during recomposition.
3. Wire MainActivity to an HTTPS starting page so the runnable app exercises the host.
4. Add JVM policy/navigation tests and an Android framework test that reads back each setting.
5. Reconcile ROADMAP's completed core markers; close BROWSE-001 only after review, QA, and validate.

## Security checks

Tests must fail if file access, universal/file URL access, content access, or mixed content is
relaxed; if JavaScript is disabled; or if `file:` becomes an allowed WebView navigation. A source
check ensures the forbidden `addJavascriptInterface` API is absent from production Kotlin.

## Validation

Run focused app unit tests, app assembly, Detekt, the repository pre-commit gate, review, QA, and the
validate command. The Android instrumentation test is compiled in the regular gate and is runnable
on an attached emulator/device.
