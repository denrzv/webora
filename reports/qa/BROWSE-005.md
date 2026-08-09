# QA Report: BROWSE-005
Status: QA_PASSED

## Scope

External-navigation classification and confirmation, HTTP(S)-only downloads, allow-listed SAF uploads, WebView integration, Android compilation, lint, and repository guardrails.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | HTTPS remains renderer-owned | JVM unit test | PASS |
| 2 | Mail, telephone, and map requests become inert typed data | JVM unit test | PASS |
| 3 | Arbitrary schemes are consumed without launching | JVM negative-control test | PASS |
| 4 | Subframes cannot prompt external navigation | JVM negative-control test | PASS |
| 5 | Downloads accept absolute HTTP(S) and reject other/malformed URLs | JVM negative-control test | PASS |
| 6 | Upload hints and returned URIs remain within browser-owned allow-lists | JVM negative-control test | PASS |
| 7 | Activity result, WebChromeClient, DownloadManager, resources, and APK compile | Gradle compile/assemble | PASS |
| 8 | Runtime external apps, DownloadManager, picker, visual, and TalkBack inspection | Connected-device check | NOT RUN — no device and no `/dev/kvm`; instrumentation was compiled per managed-cloud policy. |

## Edge cases

- invalid manifest → N/A — these regular-browser capabilities do not consume SiteSkin manifests.
- origin change / redirect → PASS — external capability requests do not change browser mode; HTTP(S) redirects remain renderer-owned.
- offline with cached manifest → N/A — no manifest/cache change; unavailable external handlers and failed enqueue operations return safely.
- oversized or malformed payload → PASS — bounded MIME arrays and malformed URI inputs fail closed; no payload body is parsed.
- accessibility (TalkBack, font scale) → Compile PASS; runtime inspection is unavailable without a connected device or KVM.

## Result

Status: QA_PASSED
Notes: Automated acceptance checks pass. Runtime instrumentation and screenshot capture are an environment limitation, not a product-test failure.
