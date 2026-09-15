#!/usr/bin/env python3
"""Fetch immutable user-owned SDK sources; never copy local SDKs or binaries."""
import json
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[2]
for name, source in json.loads((root / "tools/dsh-build/sources.json").read_text()).items():
    target = root / ".dsh-build" / name
    if target.exists():
        raise SystemExit(f"Refusing to overwrite existing source directory: {target}")
    subprocess.run(["git", "clone", "--no-checkout", source["url"], str(target)], check=True)
    subprocess.run(["git", "-C", str(target), "checkout", "--detach", source["commit"]], check=True)
    actual = subprocess.check_output(["git", "-C", str(target), "rev-parse", "HEAD"], text=True).strip()
    if actual != source["commit"]:
        raise SystemExit(f"SDK revision mismatch: {name}")
