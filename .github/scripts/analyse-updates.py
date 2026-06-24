#!/usr/bin/env python3
"""
Analyses the Ben Manes dependency update report and writes a GitHub Actions
step summary. Emits ::warning:: annotations for minor updates and exits with
code 1 if any major updates are found.
"""

import json
import os
import sys


def major(version: str) -> str:
    return version.split(".")[0] if version else ""


def classify(current: str, available: str) -> str:
    return "major" if major(current) != major(available) else "minor"


def available_version(dep: dict) -> str:
    avail = dep.get("available", {})
    return avail.get("release") or avail.get("milestone") or avail.get("integration") or ""


report_path = "build/dependencyUpdates/report.json"
with open(report_path) as f:
    report = json.load(f)

outdated = report.get("outdated", {}).get("dependencies", [])

major_updates = []
minor_updates = []

for dep in outdated:
    current = dep.get("version", "")
    newest = available_version(dep)
    if not newest:
        continue
    entry = {
        "coordinate": f"{dep['group']}:{dep['name']}",
        "current": current,
        "available": newest,
    }
    if classify(current, newest) == "major":
        major_updates.append(entry)
    else:
        minor_updates.append(entry)

summary_path = os.environ.get("GITHUB_STEP_SUMMARY", "/dev/stdout")
with open(summary_path, "a") as out:
    out.write("# Dependency Update Report\n\n")

    if not major_updates and not minor_updates:
        out.write("All dependencies are up to date.\n")
    else:
        if major_updates:
            out.write("## Major Updates\n\n")
            out.write("| Dependency | Current | Available |\n")
            out.write("|---|---|---|\n")
            for u in major_updates:
                out.write(f"| `{u['coordinate']}` | {u['current']} | **{u['available']}** |\n")
            out.write("\n")

        if minor_updates:
            out.write("## Minor Updates\n\n")
            out.write("| Dependency | Current | Available |\n")
            out.write("|---|---|---|\n")
            for u in minor_updates:
                out.write(f"| `{u['coordinate']}` | {u['current']} | {u['available']} |\n")
            out.write("\n")

for u in minor_updates:
    print(f"::warning::Minor update available: {u['coordinate']} {u['current']} -> {u['available']}")

for u in major_updates:
    print(f"::error::Major update available: {u['coordinate']} {u['current']} -> {u['available']}")

if major_updates:
    sys.exit(1)
