#!/usr/bin/env python3
"""Checks the nightly notes, or merges them into the nightly release body.

Each file in .github/nightly-notes/ holds one PR's user-visible changes as `## <Section>` headings
followed by `- ` bullets. The in-app update screen (ReleaseNotes.kt) shows the intro paragraph, then
one group per `##` section, so the merged body keeps exactly that shape.

    nightly_notes.py --check
    nightly_notes.py --intro "Built from ..." > body.md
"""

import argparse
import pathlib
import re
import sys

SECTIONS = ("New", "Improved", "Fixed")
NOTES_DIR = pathlib.Path(__file__).resolve().parents[1] / "nightly-notes"
FILE_NAME = re.compile(r"\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9.-]*\.md")


def read_note(path):
    sections = {}
    errors = []
    current = None
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        where = f"{path.name}:{number}"
        if not line:
            continue
        if "—" in line:
            errors.append(f"{where}: no em-dashes in user-facing copy")
        if line.startswith("## "):
            current = line[3:].strip()
            if current not in SECTIONS:
                errors.append(f"{where}: section '{current}' must be one of {', '.join(SECTIONS)}")
            sections.setdefault(current, [])
        elif line.startswith("- "):
            if current is None:
                errors.append(f"{where}: bullet before any '## Section' heading")
            else:
                sections[current].append(line[2:].strip())
        else:
            errors.append(f"{where}: expected a '## Section' heading or a '- ' bullet")
    if not any(sections.values()):
        errors.append(f"{path.name}: has no bullets")
    return sections, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="only validate the notes")
    parser.add_argument("--intro", default="", help="paragraph shown above the sections")
    args = parser.parse_args()

    notes = sorted(path for path in NOTES_DIR.glob("*.md") if path.name != "README.md")
    merged = {section: [] for section in SECTIONS}
    errors = []
    # File names start with the date, so reversed order lists the newest changes first.
    for path in reversed(notes):
        if not FILE_NAME.fullmatch(path.name):
            errors.append(f"{path.name}: name must be YYYY-MM-DD-<branch>.md in lowercase")
        sections, problems = read_note(path)
        errors += problems
        for section, items in sections.items():
            merged.setdefault(section, []).extend(items)

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    if args.check:
        print(f"{len(notes)} nightly notes are valid.")
        return 0

    lines = [args.intro] if args.intro else []
    for section in SECTIONS:
        if merged[section]:
            lines += ["", f"## {section}", *(f"- {item}" for item in merged[section])]
    print("\n".join(lines).strip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
