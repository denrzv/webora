# /diagrams

## Purpose
Render architecture diagrams from PlantUML sources.

## Steps
1. Sources in `diagrams/*.puml`.
2. `bash scripts/render-diagrams.sh` → SVG into `diagrams/out/` (gitignored, do not commit).
3. CI workflow uploads the SVGs as build artifacts.

## Outputs
- `diagrams/out/**/*.svg`
