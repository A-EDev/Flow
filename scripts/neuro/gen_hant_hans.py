#!/usr/bin/env python3
"""Writes app/src/main/resources/neuro/fold/hant-hans.txt from OpenCC TSCharacters.txt (pinned, Apache-2.0).

BMP 1:1 mappings only; first variant wins; chains resolved to a fixed point.

    python3 -I scripts/neuro/gen_hant_hans.py
"""
import os
import sys
import urllib.request

COMMIT = "ba09ffb8353f680381200c72452a36c72d540155"
URL = f"https://raw.githubusercontent.com/BYVoid/OpenCC/{COMMIT}/data/dictionary/TSCharacters.txt"
OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "app", "src", "main", "resources", "neuro", "fold", "hant-hans.txt",
)


def main():
    with urllib.request.urlopen(URL, timeout=60) as response:
        text = response.read().decode("utf-8")
    pairs = {}
    for line in text.splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, _, values = line.partition("\t")
        first = values.split()[0] if values.split() else ""
        if len(key) == 1 and len(first) == 1 and key != first and ord(key) < 0x10000 and ord(first) < 0x10000:
            pairs[key] = first
    # Resolve chains (薴 to 苧 to 苎) so the fold is idempotent.
    for key in list(pairs):
        value, seen = pairs[key], {key}
        while value in pairs and value not in seen:
            seen.add(value)
            value = pairs[value]
        pairs[key] = value
    pairs = {key: value for key, value in pairs.items() if key != value}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("# OpenCC %s TSCharacters.txt (Apache-2.0) | scripts/neuro/gen_hant_hans.py | traditional, simplified\n" % COMMIT[:12])
        for key in sorted(pairs):
            f.write("%s%s\n" % (key, pairs[key]))
    print(len(pairs))


if __name__ == "__main__":
    sys.exit(main())
