"""General word frequencies for the engine's specificity signal (#907).

Downloads the OpenSubtitles 2018 frequency lists of hermitdave/FrequencyWords at a pinned commit
and writes the most frequent words of each language to app/src/main/resources/neuro/freq/<lang>.txt
as "word zipf" lines, zipf times ten as an integer (Zipf = log10 of the frequency per billion
words). The engine keys these words at runtime, so the table follows the key spec on its own.

Content licence: CC BY-SA 4.0 (credited in assets/licenses/notices.txt).

    python3 -I scripts/neuro/gen_wordfreq.py
"""
import math
import os
import sys
import urllib.request

COMMIT = "525f9b560de45753a5ea01069454e72e9aa541c6"
BASE = f"https://raw.githubusercontent.com/hermitdave/FrequencyWords/{COMMIT}/content/2018"
# zh is Hans; NeuroSpecificityTest prunes it to single breaker words (NEURO_WRITE_FREQ_ZH=1).
LANGUAGES = {
    "en": "en", "es": "es", "pt": "pt_br", "fr": "fr", "de": "de", "it": "it", "ru": "ru", "id": "id",
    "tr": "tr", "pl": "pl", "cs": "cs", "uk": "uk", "ar": "ar", "fa": "fa", "vi": "vi", "et": "et", "zh": "zh_cn",
}
TOP = {"en": 10000}
# Past this rank a word sits near LOW_ZIPF and barely changes a key's specificity.
DEFAULT_TOP = 2500
# Below this a word is rare enough to count as fully specific (NeuroSpecificity.LOW_ZIPF); not worth shipping.
MIN_ZIPF = 4.0

out_dir = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/resources/neuro/freq"
os.makedirs(out_dir, exist_ok=True)
for lang, source in LANGUAGES.items():
    url = f"{BASE}/{source}/{source}_50k.txt"
    rows = [line.split() for line in urllib.request.urlopen(url, timeout=60).read().decode("utf-8").splitlines() if line.strip()]
    rows = [(word, int(count)) for word, count in (r for r in rows if len(r) == 2)]
    total = sum(count for _, count in rows)
    kept = []
    for word, count in rows[: TOP.get(lang, DEFAULT_TOP)]:
        zipf = math.log10(count / total * 1e9)
        if zipf < MIN_ZIPF:
            break
        if word.isalpha():
            kept.append(f"{word} {round(zipf * 10)}")
    with open(os.path.join(out_dir, f"{lang}.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(kept) + "\n")
    print(lang, len(kept))
