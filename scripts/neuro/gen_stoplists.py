#!/usr/bin/env python3
"""Writes the engine's stop lists to app/src/main/resources/neuro/stop/<lang>.txt from pinned sources.

Snowball's own lists where Snowball publishes one, stopwords-iso elsewhere, plus add/<lang>.txt and
minus keep/<lang>.txt.
Run from the repository root: python3 scripts/neuro/gen_stoplists.py
"""

import json
import os
import sys
import unicodedata
import urllib.request

VERSION = 1
SNOWBALL = "a5c23fcf6cb8c34f450d5080d888e65bc4681c1b"
ISO = "6e8e9a2d1c019ad2a885bcbe157628dea624dfb6"
SNOWBALL_LANGS = {
    "en": "english",
    "es": "spanish",
    "pt": "portuguese",
    "fr": "french",
    "de": "german",
    "it": "italian",
    "ru": "russian",
    "id": "indonesian",
}
ISO_LANGS = ["ar", "cs", "et", "fa", "hi", "ja", "ko", "pl", "tr", "uk", "vi", "zh"]

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "resources", "neuro", "stop")
KEEP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "keep")
ADD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "add")


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read().decode("utf-8")


def fold(word):
    return unicodedata.normalize("NFKC", word.replace("’", "'")).lower().strip()


def snowball_words(text):
    words = []
    for line in text.splitlines():
        words += line.split("|", 1)[0].split()
    return words


def listed(folder, lang):
    path = os.path.join(folder, lang + ".txt")
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return {fold(line) for line in f if line.strip() and not line.startswith("#")}


def write(lang, source, words):
    kept = listed(KEEP, lang)
    added = listed(ADD, lang)
    entries = sorted(({w for w in map(fold, words) if w and " " not in w and any(c.isalnum() for c in w)} | added) - kept)
    with open(os.path.join(OUT, lang + ".txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# %s | scripts/neuro/gen_stoplists.py v%d | +%d add, -%d keep\n" % (source, VERSION, len(added), len(kept)))
        f.write("\n".join(entries) + "\n")
    return len(entries)


def main():
    os.makedirs(OUT, exist_ok=True)
    for lang, name in SNOWBALL_LANGS.items():
        path = "algorithms/%s/stop.txt" % name
        text = fetch("https://raw.githubusercontent.com/snowballstem/snowball-website/%s/%s" % (SNOWBALL, path))
        print(lang, write(lang, "snowball-website %s %s (BSD-3-Clause)" % (SNOWBALL[:12], path), snowball_words(text)))
    iso = json.loads(fetch("https://raw.githubusercontent.com/stopwords-iso/stopwords-iso/%s/stopwords-iso.json" % ISO))
    for lang in ISO_LANGS:
        print(lang, write(lang, "stopwords-iso %s stopwords-iso.json (MIT)" % ISO[:12], iso[lang]))


if __name__ == "__main__":
    sys.exit(main())
