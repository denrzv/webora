# Tasklist: CORE-002 — DTOs and parsing
Status: TASKLIST_READY

## Tasks
- [x] TASK-1: Add inert schema-shaped DTOs
  - New: `SiteSkinManifestDto.kt` and `SiteSkinManifestDtoTest.kt`.
  - Acceptance: every published schema property has an untrusted DTO member; missing optional/schema-required values can be represented for later validation; the complete Bloom fixture decodes.
  - Named tests: `completeManifestMapsEverySchemaField`, `missingSchemaRequiredFieldsRemainUntrustedData`, `dtoApiDoesNotExposeTrustedConfiguration`.

- [x] TASK-2: Add bounded parser and stable outcomes
  - New: `ManifestParser.kt` and base `ManifestParserTest.kt`.
  - Acceptance: 131,072 bytes may parse, byte 131,073 rejects before decode/read-ahead; malformed JSON and invalid UTF-8 yield stable results without throwing.
  - Named tests: `oversizedInputStopsAtFirstByteBeyondLimit`, `malformedJsonIsRejected`, `invalidUtf8IsRejected`, `parserDoesNotCloseCallerStream`.
  - Negative control: remove the bounded read and read to EOF; the counting stream assertion must fail.
  - Negative-control result: **ran, fails as required.** With the remaining-byte cap replaced by `Int.MAX_VALUE`, `oversizedInputStopsAtFirstByteBeyondLimit` failed at its rejection assertion; restored implementation passes.

- [x] TASK-3: Report unknown fields and prove corpus compatibility
  - Modified: parser/tests and `CLAUDE.md` parsing notes.
  - Acceptance: every valid parsing fixture parses; unknown fields at known object levels are ignored with path-bearing warnings and known siblings survive.
  - Named tests: `allValidParsingFixturesParse`, `unknownFieldsAreIgnoredWithPaths`, `unknownFieldsInsideItemsAreReportedPerOccurrence`.
  - Negative control: enable silent `ignoreUnknownKeys` without the field scan; warning assertions must fail.
  - Negative-control result: **ran, fails as required.** Replacing the shape scan with an empty warning list made `unknownFieldsAreIgnoredWithPaths` fail; restored implementation passes.

- [x] TASK-FIX-1: Contain stream read failures
  - Source: `/review finding 1`.
  - Modified: `ManifestParser.kt`, `ManifestParserTest.kt`, and review report.
  - Acceptance: an `InputStream` throwing `IOException` produces `SS-E-PARSE` rather than escaping; unrelated programming exceptions are not broadly swallowed.
  - Named test: `streamReadFailureIsRejectedWithoutThrowing`.

- [x] TASK-FIX-2: Persist QA and validation evidence
  - Source: `/qa and /validate closeout`.
  - New: `reports/qa/CORE-002.md`.
  - Acceptance: all task boxes and workflow statuses are ready, review is resolved, QA is passed, and the final local gate is green. Branch CI is deferred to the platform PR because this managed checkout has no remote.
