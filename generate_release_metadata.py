#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--notes", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    apk = Path(args.apk)
    notes_file = Path(args.notes)
    output = Path(args.output)

    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")
    if args.version_code <= 0:
        raise SystemExit("versionCode must be positive")
    if not notes_file.is_file():
        raise SystemExit(f"Release notes not found: {notes_file}")

    digest = hashlib.sha256()
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)

    notes = []
    for raw in notes_file.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.lower().startswith("armyrist "):
            continue
        line = line.lstrip("-•").strip()
        if line:
            notes.append(line)

    payload = {
        "schemaVersion": 1,
        "versionName": args.version_name,
        "versionCode": args.version_code,
        "releaseType": "stable",
        "apkAsset": apk.name,
        "apkSha256": digest.hexdigest(),
        "releaseNotes": notes,
    }

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Generated {output}")
    print(f"APK SHA-256: {payload['apkSha256']}")

if __name__ == "__main__":
    main()
