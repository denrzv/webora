# Cloud development environments

Webora uses one provider-neutral bootstrap for cloud development environments:

```bash
bash scripts/bootstrap.sh
```

The script provisions the Android SDK required by `:app`, preserves existing
`local.properties` values while setting `sdk.dir`, and can optionally prepare
Gradle. It is strict and returns a non-zero exit code when provisioning fails.

## Claude Code

Claude Code keeps its existing SessionStart hook:

```text
scripts/session-start.sh -> scripts/bootstrap.sh
```

`session-start.sh` is intentionally a best-effort adapter. A bootstrap failure
does not abort the Claude session because `:siteskin-core` is Android-free.

## Codex Cloud

Before creating a Codex environment, make `main` the repository default branch.
Codex builds its environment cache from the repository default branch before it
checks out the branch selected for a chat.

Recommended environment settings:

- Repository: `denrzv/webora`
- Runtime / Java: JDK 25
- Setup script:

```bash
WEBORA_BOOTSTRAP_PREPARE_GRADLE=1 bash scripts/bootstrap.sh
```

- Maintenance script:

```bash
WEBORA_BOOTSTRAP_PREPARE_GRADLE=1 bash scripts/bootstrap.sh
```

Running the same idempotent bootstrap as maintenance lets a resumed cached
container reconcile SDK/tooling state after Codex checks out the chat branch.

### Internet access

The setup and maintenance phases need network access to download the Android SDK,
Gradle distribution, plugins, and dependencies. Codex setup scripts have network
access automatically.

For the agent phase, start with the `Common dependencies` allowlist and restrict
HTTP methods to `GET`, `HEAD`, and `OPTIONS`. This is sufficient for normal Gradle
and Maven/Google dependency downloads while avoiding unrestricted outbound access.
If the project proves fully buildable from the cached setup state, agent internet
access can be disabled later.

### Secrets

No secrets are required for normal debug builds and unit tests. Release-signing
credentials must remain in Codex environment secrets or another secret store and
must never be committed to the repository.

## Optional bootstrap overrides

The defaults match the current Android project:

```text
WEBORA_COMPILE_SDK=36
WEBORA_BUILD_TOOLS=36.0.0
WEBORA_CMDLINE_TOOLS_VERSION=13114758
WEBORA_BOOTSTRAP_PREPARE_GRADLE=0
```

Override them only when the project toolchain changes. Keep the repository build
configuration and bootstrap defaults aligned in the same change.
