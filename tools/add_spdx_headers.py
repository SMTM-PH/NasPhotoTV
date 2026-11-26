#!/usr/bin/env python3
"""Idempotently add SPDX headers to Kotlin / Gradle Kotlin DSL / Swift sources.

Usage:
    python3 tools/add_spdx_headers.py [path ...]

With no arguments, scans the whole repository (skipping .git and build output).
Files that already contain 'SPDX-License-Identifier' are left untouched.
Files that cannot be written (e.g. a read-only checkout) are reported and skipped.
"""

from __future__ import annotations

import os
import stat
import sys
import tempfile
from pathlib import Path

NOTICE = "Copyright 2026 SMTM-PH and NasPhotoTV contributors"
SPDX = "SPDX-License-Identifier: Apache-2.0"
MARKER = "SPDX-License-Identifier"

HEADERS = {
    ".kt": f"/*\n * {NOTICE}\n * {SPDX}\n */\n\n",
    ".kts": f"/*\n * {NOTICE}\n * {SPDX}\n */\n\n",
    ".swift": f"//\n// {NOTICE}\n// {SPDX}\n//\n\n",
}

SKIP_DIRS = {".git", "build", ".gradle", ".kotlin", ".build", "node_modules"}


def iter_sources(root: Path):
    if root.is_file():
        if root.suffix in HEADERS:
            yield root
        return
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in HEADERS:
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        yield path


def main(argv: list[str]) -> int:
    roots = [Path(p) for p in argv] if argv else [Path(".")]
    added = skipped = unchanged = 0
    failed: list[str] = []

    for root in roots:
        if not root.exists():
            failed.append(f"path not found: {root}")
            continue
        for path in iter_sources(root):
            try:
                text = path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError) as exc:
                failed.append(f"cannot read {path}: {exc}")
                continue
            if MARKER in text:
                unchanged += 1
                continue
            content = HEADERS[path.suffix] + text
            try:
                path.write_text(content, encoding="utf-8")
            except OSError:
                # Some files are owned by another user but live in a writable
                # directory. Replace them atomically while preserving the mode.
                try:
                    fd, tmp_name = tempfile.mkstemp(dir=path.parent, prefix=".spdx-")
                    with os.fdopen(fd, "w", encoding="utf-8") as tmp:
                        tmp.write(content)
                    os.chmod(tmp_name, stat.S_IMODE(path.stat().st_mode))
                    os.replace(tmp_name, path)
                except OSError as exc:
                    skipped += 1
                    failed.append(f"cannot write {path}: {exc}")
                    continue
            added += 1

    print(f"SPDX headers added: {added}")
    print(f"Already present:    {unchanged}")
    print(f"Skipped (readonly): {skipped}")
    for line in failed:
        print(f"  ! {line}")
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
