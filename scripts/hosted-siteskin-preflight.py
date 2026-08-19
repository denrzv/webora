#!/usr/bin/env python3
"""Fail fast when the hosted Bloom deployment is stale or violates CI-010's contract."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

EXPECTED_SOURCE_REPOSITORY = "denrzv/bloom-flowers"
EXPECTED_DOCK = ["catalog", "cart", "profile"]
EXPECTED_BOTTOM = ["home", "catalog", "cart", "profile"]
EXPECTED_QUICK = ["call-shop"]
FETCH_ATTEMPTS = 3
FETCH_RETRY_DELAY_SECONDS = 2
FETCH_TIMEOUT_SECONDS = 20


def fetch_json(url: str, destination: Path) -> dict[str, Any]:
    last_error: Exception | None = None
    for attempt in range(1, FETCH_ATTEMPTS + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "webora-ci-010"})
            with urllib.request.urlopen(request, timeout=FETCH_TIMEOUT_SECONDS) as response:
                payload = response.read()
            destination.write_bytes(payload)
            data = json.loads(payload.decode("utf-8"))
            if not isinstance(data, dict):
                raise ValueError(f"Expected a JSON object from {url}")
            return data
        except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
            last_error = error
            if attempt < FETCH_ATTEMPTS:
                time.sleep(FETCH_RETRY_DELAY_SECONDS)

    raise RuntimeError(f"Could not fetch valid JSON from {url}: {last_error}") from last_error


def write_summary(path: Path, lines: list[str]) -> None:
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(path.read_text(encoding="utf-8"), end="")


def validate_freshness(
    origin: str,
    expected_source_sha: str,
    deployment: dict[str, Any],
    summary_path: Path,
) -> None:
    actual_repository = deployment.get("sourceRepository")
    actual_sha = deployment.get("sourceSha")

    errors: list[str] = []
    if actual_repository != EXPECTED_SOURCE_REPOSITORY:
        errors.append(
            "sourceRepository expected "
            f"{EXPECTED_SOURCE_REPOSITORY!r}, got {actual_repository!r}"
        )
    if actual_sha != expected_source_sha:
        errors.append(
            f"sourceSha expected {expected_source_sha!r}, got {actual_sha!r}"
        )

    lines = [
        f"origin={origin}",
        f"source_repository_expected={EXPECTED_SOURCE_REPOSITORY!r}",
        f"source_repository_actual={actual_repository!r}",
        f"source_sha_expected={expected_source_sha}",
        f"source_sha_actual={actual_sha}",
        f"freshness_verdict={'PASS' if not errors else 'FAIL'}",
    ]
    lines.extend(f"error={error}" for error in errors)
    write_summary(summary_path, lines)

    if errors:
        print("Hosted Bloom deployment is stale or points at the wrong source.", file=sys.stderr)
        print(f"Expected bloom-flowers@main: {expected_source_sha}", file=sys.stderr)
        print(f"Deployed sourceSha: {actual_sha}", file=sys.stderr)
        print(
            "Run 'Publish Bloom Flowers' in denrzv/denrzv.github.io, wait for Pages deployment, "
            "then rerun Android screenshots.",
            file=sys.stderr,
        )
        raise SystemExit(1)


def validate_contract(
    origin: str,
    manifest: dict[str, Any],
    summary_path: Path,
) -> None:
    presentation = manifest.get("presentation") or {}
    if not isinstance(presentation, dict):
        presentation = {}

    actual_hub = presentation.get("hub")
    actual_dock = presentation.get("dock")
    bottom_navigation = manifest.get("bottomNavigation") or []
    quick_actions = manifest.get("quickActions") or []

    bottom_ids = [item.get("id") for item in bottom_navigation if isinstance(item, dict)]
    quick_ids = [item.get("id") for item in quick_actions if isinstance(item, dict)]

    errors: list[str] = []
    if actual_hub != "drawer":
        errors.append(f"presentation.hub expected 'drawer', got {actual_hub!r}")
    if actual_dock != EXPECTED_DOCK:
        errors.append(f"presentation.dock expected {EXPECTED_DOCK!r}, got {actual_dock!r}")

    missing_bottom = [item for item in EXPECTED_BOTTOM if item not in bottom_ids]
    if missing_bottom:
        errors.append(f"bottomNavigation missing ids {missing_bottom!r}; actual={bottom_ids!r}")

    missing_quick = [item for item in EXPECTED_QUICK if item not in quick_ids]
    if missing_quick:
        errors.append(f"quickActions missing ids {missing_quick!r}; actual={quick_ids!r}")

    lines = [
        f"origin={origin}",
        f"presentation.hub={actual_hub!r}",
        f"presentation.dock={actual_dock!r}",
        f"bottomNavigation={bottom_ids!r}",
        f"quickActions={quick_ids!r}",
        f"contract_verdict={'PASS' if not errors else 'FAIL'}",
    ]
    lines.extend(f"error={error}" for error in errors)
    write_summary(summary_path, lines)

    if errors:
        print("Hosted SiteSkin manifest is incompatible with CI-010.", file=sys.stderr)
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--origin", required=True)
    parser.add_argument("--expected-source-sha", required=True)
    parser.add_argument("--artifacts-dir", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    origin = args.origin.rstrip("/")
    expected_source_sha = args.expected_source_sha.strip()
    artifacts_dir: Path = args.artifacts_dir
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    if len(expected_source_sha) != 40 or any(
        character not in "0123456789abcdef" for character in expected_source_sha.lower()
    ):
        raise SystemExit(f"Invalid expected Bloom SHA: {expected_source_sha!r}")

    deployment_path = artifacts_dir / "hosted-deployment.json"
    freshness_summary = artifacts_dir / "hosted-deployment-validation.txt"
    deployment = fetch_json(f"{origin}/deployment.json", deployment_path)
    validate_freshness(origin, expected_source_sha, deployment, freshness_summary)

    manifest_path = artifacts_dir / "hosted-siteskin.json"
    contract_summary = artifacts_dir / "hosted-siteskin-validation.txt"
    manifest = fetch_json(f"{origin}/.well-known/siteskin.json", manifest_path)
    validate_contract(origin, manifest, contract_summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
