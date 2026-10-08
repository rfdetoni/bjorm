#!/usr/bin/env python3
"""Guard BJORM Maven reactor versions before publishing releases."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parents[1]
MODULES = ("bjorm-core", "bjorm-processor", "bjorm-examples", "bjorm-spring-boot", "bjorm-benchmarks")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PATTERN = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")


def pom_version(path: Path) -> str:
    root = ET.parse(path).getroot()
    node = root.find("m:version", NS)
    if node is None:
        node = root.find("m:parent/m:version", NS)
    if node is None or not node.text:
        raise ValueError(f"Maven version missing: {path}")
    return node.text.strip()


def check(expected: str) -> None:
    for path in (BASE / "pom.xml", *(BASE / p / "pom.xml" for p in MODULES)):
        actual = pom_version(path)
        if actual != expected:
            raise ValueError(f"{path.relative_to(BASE)}: expected {expected}, got {actual}")


def main() -> None:
    if len(sys.argv) < 2:
        raise ValueError("Usage: release_version.py check-snapshot [version] | check-release VERSION | prepare VERSION")
    cmd = sys.argv[1]
    supplied = sys.argv[2] if len(sys.argv) > 2 else None
    if cmd == "check-snapshot":
        expected = supplied or pom_version(BASE / "pom.xml")
        if not expected.endswith("-SNAPSHOT") or not PATTERN.fullmatch(expected.removesuffix("-SNAPSHOT")):
            raise ValueError(f"Invalid SNAPSHOT version: {expected}")
        check(expected)
    elif cmd == "check-release":
        if not supplied or not PATTERN.fullmatch(supplied):
            raise ValueError("Release version must be MAJOR.MINOR.PATCH")
        check(supplied)
    elif cmd == "prepare":
        if not supplied or not PATTERN.fullmatch(supplied):
            raise ValueError("Release version must be MAJOR.MINOR.PATCH")
        check(supplied + "-SNAPSHOT")
        major, minor, patch = (int(x) for x in supplied.split("."))
        print(f"next_snapshot={major}.{minor}.{patch + 1}-SNAPSHOT")
    else:
        raise ValueError(f"Unknown command: {cmd}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, ET.ParseError) as exc:
        print(exc, file=sys.stderr)
        sys.exit(1)
