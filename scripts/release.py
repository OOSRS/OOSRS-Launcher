#!/usr/bin/env python3
"""Collect the launcher JAR and its independent self-update metadata."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--check-tag")
args = parser.parse_args()
version = re.search(r'version = "([0-9.]+)"', (root / "build.gradle.kts").read_text()).group(1)
if args.check_tag:
    if args.check_tag != "v" + version:
        raise SystemExit("Release tag does not match the launcher source version")
    raise SystemExit(0)
out = root / "release-output"
out.mkdir(exist_ok=True)
for old in out.iterdir():
    if old.is_file():
        old.unlink()
jar = root / f"build/libs/openosrs-launcher-{version}.jar"
shutil.copy2(jar, out / jar.name)
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
(out / "update.properties").write_text(f"version={version}\nasset={jar.name}\nsha256={digest}\njava=21\n")
(out / "SHA256SUMS").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in sorted(out.iterdir()) if p.is_file()))
print(f"Prepared OpenOSRS Launcher {version}: {out}")
