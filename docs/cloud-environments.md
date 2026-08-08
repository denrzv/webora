# Cloud development environments

Webora uses one provider-neutral bootstrap for cloud development environments:

```bash
bash scripts/bootstrap.sh
```

The script provisions the Android SDK required by `:app`, installs the local
quality-gate tools used by `scripts/pre-commit-check.sh`, preserves existing
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

### Checkpoints and pull requests

Codex Cloud may provide a managed checkout without a configured push-capable Git
remote. Do not add an `origin`, GitHub token, or other credentials from repository
scripts just to make `git push` work.

The shared AIDD workflow therefore uses two checkpoint modes:

- Normal git checkout: one task per commit, then push and verify each task commit.
- Managed cloud without push capability: one task per local commit and continue in
  the same session; before ending the session or handing off, persist accumulated
  commits with the platform-provided PR/export/sync mechanism.

Codex shows the resulting diff when a cloud task finishes and provides a platform
flow to open a pull request. If no durable PR/export/sync checkpoint can be
created, report the blocked handoff instead of pretending the work is persisted.

### Internet access

The setup phase needs network access to download the Android SDK, Gradle tooling,
Gitleaks, ShellCheck, plugins, and dependencies. Codex setup scripts have network
access automatically.

For the agent phase, start with the `Common dependencies` allowlist and restrict
HTTP methods to `GET`, `HEAD`, and `OPTIONS`. This is sufficient for normal Gradle
and Maven/Google dependency downloads while avoiding unrestricted outbound write
access. If the project proves fully buildable from the cached setup state, agent
internet access can be disabled later.

### Secrets

No secrets are required for normal debug builds and unit tests. Codex environment
secrets are available to setup scripts but are removed before the agent phase, so
do not design agent-phase release signing around those secrets. Keep release
signing in a dedicated CI/release path or another mechanism that exposes signing
credentials only at the signing boundary.

## Optional bootstrap overrides

The defaults match the current project/tooling:

```text
WEBORA_COMPILE_SDK=36
WEBORA_BUILD_TOOLS=36.0.0
WEBORA_CMDLINE_TOOLS_VERSION=13114758
WEBORA_GITLEAKS_VERSION=8.29.1
WEBORA_SHELLCHECK_VERSION=0.11.0
WEBORA_BOOTSTRAP_PREPARE_GRADLE=0
```

Override them only when the project toolchain changes. Keep the repository build
configuration and bootstrap defaults aligned in the same change.
