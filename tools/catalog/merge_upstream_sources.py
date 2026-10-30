#!/usr/bin/env python3
"""Adds upstream's catalog sources to ours, so the fork's catalog keeps every addon upstream lists.

Our entries win when both list the same repo. Upstream entries are checked for shape and anything
odd is skipped; if upstream can't be fetched, our sources are written unchanged.

    python3 tools/catalog/merge_upstream_sources.py catalog/sources.json out/sources.json
"""
import json
import re
import sys
import urllib.request

UPSTREAM = "https://raw.githubusercontent.com/YoruDev-Ryland/WoWForeverywhere/main/catalog/sources.json"
REPO = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
CATEGORY = re.compile(r"^[A-Z_]+$")


def valid(entry):
    if not isinstance(entry, dict):
        return False
    if not isinstance(entry.get("repo"), str) or not REPO.match(entry["repo"]):
        return False
    if not isinstance(entry.get("name"), str) or not isinstance(entry.get("summary"), str):
        return False
    if "category" in entry and not (isinstance(entry["category"], str) and CATEGORY.match(entry["category"])):
        return False
    requires = entry.get("requires", [])
    return isinstance(requires, list) and all(isinstance(r, str) and REPO.match(r) for r in requires)


def main(ours_path, out_path):
    with open(ours_path) as f:
        ours = json.load(f)
    try:
        req = urllib.request.Request(UPSTREAM, headers={"User-Agent": "WoWEverywhere-catalog"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            upstream = json.load(resp).get("addons", [])
    except Exception as e:  # noqa: BLE001 - upstream is optional
        print(f"Upstream sources unavailable, using ours only: {e}", file=sys.stderr)
        upstream = []
    known = {a["repo"].lower() for a in ours["addons"]}
    added = []
    for entry in upstream:
        if valid(entry) and entry["repo"].lower() not in known:
            known.add(entry["repo"].lower())
            added.append({k: entry[k] for k in ("repo", "name", "category", "summary", "requires") if k in entry})
    ours["addons"].extend(added)
    print(f"Added {len(added)} upstream entries: {[a['repo'] for a in added]}")
    with open(out_path, "w") as f:
        json.dump(ours, f, indent=2)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
