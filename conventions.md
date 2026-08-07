# Conventions — Webora Browser

## Kotlin

- `Flow` + `suspend`, never `LiveData`.
- Sealed hierarchies over boolean flag sets. If two flags can contradict each other, the state is
  modelled wrong — see `ADR-008`.
- Trusted domain objects are constructible only through their validator. A `SiteSkinConfiguration`
  that exists is a configuration that passed security validation; make the illegal state
  unrepresentable rather than documenting that it must not happen.
- `internal` for anything not crossing a module boundary.
- No DI framework. Dependencies are constructed explicitly at the composition root.

## Module boundaries

- `:siteskin-core` has **zero** Android dependencies. Not `android.*`, not `androidx.*`, not
  `Context`. It is a `java-library`, and CI enforces that its tests pass with `ANDROID_HOME` unset.
- Core defines `interface ManifestSource` and consumes bytes. OkHttp lives in `:app`.
- Security decisions live in core, never in a Composable and never in a `WebViewClient` callback.

## Testing

- JUnit 4 + MockK. **No Robolectric** — deliberate.
- Because there is no Robolectric, every Android-touching object splits into a thin public wrapper
  that reads real framework state, plus an `internal` pure function the tests call directly:

  ```kotlin
  fun shouldActivate(context: Context): Boolean =            // wrapper, untested
      shouldActivate(origin(context), prefs(context).enabled)

  internal fun shouldActivate(origin: SiteOrigin, enabled: Boolean): Boolean = // tested
      enabled && origin.isHttps
  ```

- MockWebServer for anything network-shaped. Distinct ports are distinct origins, which is exactly
  what origin-binding tests need.
- Security behaviour needs a **negative control**: a test that fails when the protection is removed.
  A test that passes both with and without the fix is not evidence.
- `mockkObject` is a *partial* mock — unstubbed functions call the real implementation. Stub every
  function a test path touches.

## Security and privacy

- Manifest is untrusted input at every stage. Parsing success is not validity.
- Allow-lists, never deny-lists — for schemes, action types, icon names, MIME types.
- No secrets in logs. No page URLs, no manifest bodies at INFO or above in release builds; wrap
  verbose operational logging in `if (BuildConfig.DEBUG)`.
- No telemetry without explicit opt-in.

## Complexity

Detekt gates the build. Cognitive complexity ≤ 15, cyclomatic ≤ 10, method length ≤ 40 lines.
The baseline file is for pre-existing debt only — new violations fail the build, and burning down
the baseline is its own ticket.

## Hygiene

- No commented-out code. Git remembers.
- No TODOs without a ticket id.
- Public API gets KDoc; private implementation gets comments only where the *why* is non-obvious.
- Match the density and idiom of surrounding code.
