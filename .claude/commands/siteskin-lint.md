# /siteskin-lint

Validate a live site's manifest with the same validator the browser uses.

```bash
./gradlew :siteskin-lint:run --args="https://bloomflowers.example"
```

Exit 0 means the browser will activate SiteSkin mode for that origin. Anything else prints the
diagnostic codes from `spec/SPEC.md`.

Use it against the demo sites after any change to `:siteskin-core` validation.
