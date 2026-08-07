# /detekt-gates

## Purpose
Keep the Detekt complexity gate real.

## Steps
1. Confirm the plugin is applied in every module's build file, not just the root.
2. Config lives at `config/detekt/detekt.yml`; baseline at `config/detekt/baseline.xml`.
3. **Verify with a negative control.** Add a throwaway file with a deliberate violation, confirm
   `./gradlew detekt` fails, then delete it. A gate that has been baselined into silence looks
   exactly like a passing build.
4. `scripts/pre-commit-check.sh` invokes detekt unconditionally — keep it that way.

## Known config landmines
- `complexity > ComplexMethod` is a deprecated rule name. With `warningsAsErrors: true` it fails
  the whole run. Use `CyclomaticComplexMethod`.
- Without `naming > FunctionNaming > ignoreAnnotated: ['Composable']`, every `@Composable` is
  flagged for PascalCase.

## Outputs
- Deterministic complexity gate, demonstrably able to fail.
