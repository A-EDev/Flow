/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

/**
 * Key spec 3: how text becomes the keys the engine learns, ranks and syncs by. Flow Desktop
 * implements the same stages, and both pass app/src/test/resources/neuro/key_spec_golden.json.
 *
 * 1. Fold ([NeuroText.fold]): NFKC, small capitals, lowercase. ’ ‘ ʼ ` ´ read as ', Turkish İ as i,
 *    and the emoji joiners U+FE0F and U+200D are dropped. Hant folds to Hans
 *    (`resources/neuro/fold/hant-hans.txt`, 1:1) unless the video holds kana. Keys are Hans;
 *    [NeuroDisplayForms] keeps the source spelling.
 * 2. Split at whitespace and punctuation; Chinese, Japanese, Thai, Lao, Khmer and Burmese by
 *    dictionary. [KEEP_WHOLE] words ("c++") stay whole, and a dot between letters splits a word
 *    unless the word is listed. A single quote is dropped rather than splitting anything.
 * 3. Expressions: [EXPRESSIONS] maps the ways one thing is written to one key ("lo-fi", "lo fi" and
 *    "lofi" all read "lofi"). Any other hyphenated word joins when a part has two letters or fewer
 *    ("k-pop" reads "kpop") and splits otherwise ("spider-man" reads "spider", "man").
 * 4. Possessives: 's, s' and a trailing ' come off, and so do French and Italian elisions ([ELISIONS],
 *    "l'amour" reads "amour"). [CONTRACTIONS] are stop words.
 * 5. Boundaries: stop words (the viewer's language and the title's own, [NeuroLanguages]), years,
 *    bare numbers, words under three letters and punctuation end a chunk. A number right after a
 *    word makes a model pair ("pixel 10").
 * 6. Candidates: every word, and every run of two or three words inside one chunk, never across a
 *    boundary. [NEVER_ALONE] words only count inside a run. Single Han characters from the breaker
 *    are merged into one candidate with adjacent single characters, a preceding word (大谷 + 翔 + 平) or
 *    a following word after a lone character (李 + 永乐). Runs end at `resources/neuro/runbreak`
 *    characters, are 2 to 5 characters, and contain no stop or format word. No break list, no merge.
 * 7. Promotion: words and runs count toward document frequency once per video. A run becomes a key
 *    only when [NeuroPhrases] finds it is a phrase, a creator tag holds it, or it is a compound or
 *    catalog topic. A merged Han run becomes one per [NeuroPhrases.isMergedRun]; single characters
 *    count toward document frequency.
 * 8. Stems ([NeuroStemmer]): each word's lemma is stemmed once, by its script and the viewer's
 *    language. The lemma stays when it is in `resources/neuro/keys/unstemmed.txt`, holds anything
 *    but letters, or would stem to under three letters, to a stem that stems again, or to a word
 *    the lemma map rewrites. Spec 1 kept the lemma. Chinese is not stemmed.
 *
 * Spec 3 changes stages 1, 6 and 7 for Chinese.
 *
 * A key is one to three words joined by a space and at most [MAX_KEY_LENGTH] characters. It never
 * holds "|" or ":", except one trailing domain tag ("metal:music").
 */
internal object NeuroKeySpec {
    const val VERSION = 3

    /** First spec that folds Hant to Hans and merges Han runs. */
    const val HAN_SPEC = 3
    const val MAX_RUN_WORDS = 3
    const val MAX_KEY_LENGTH = 40

    /** Model and tier words: "pro" alone says nothing about a video, "pixel pro" might. */
    val NEVER_ALONE = setOf("pro", "max", "ultra", "plus", "mini")

    val KEEP_WHOLE = setOf("c++", "c#", "f#", "g++", "node.js", "vue.js", "next.js", "three.js", "asp.net")

    /** Longest first, so "dell'" comes off before "l'" could. */
    val ELISIONS =
        listOf(
            "jusqu'",
            "lorsqu'",
            "puisqu'",
            "quell'",
            "quest'",
            "dell'",
            "dall'",
            "nell'",
            "sull'",
            "all'",
            "qu'",
            "un'",
            "l'",
            "d'",
            "j'",
            "m'",
            "n'",
            "s'",
            "t'",
            "c'",
        )

    val CONTRACTIONS =
        setOf(
            "it's",
            "that's",
            "what's",
            "there's",
            "here's",
            "he's",
            "she's",
            "who's",
            "where's",
            "when's",
            "how's",
            "let's",
            "i'm",
            "you're",
            "we're",
            "they're",
            "i've",
            "you've",
            "we've",
            "they've",
            "i'd",
            "you'd",
            "he'd",
            "she'd",
            "we'd",
            "they'd",
            "i'll",
            "you'll",
            "he'll",
            "she'll",
            "we'll",
            "they'll",
            "it'll",
            "that'll",
            "don't",
            "doesn't",
            "didn't",
            "can't",
            "won't",
            "isn't",
            "aren't",
            "wasn't",
            "weren't",
            "haven't",
            "hasn't",
            "hadn't",
            "shouldn't",
            "wouldn't",
            "couldn't",
            "mustn't",
            "ain't",
            "y'all",
            "doesnt",
            "didnt",
            "cant",
            "isnt",
            "arent",
            "wasnt",
            "havent",
            "shouldnt",
            "wouldnt",
            "couldnt",
            "thats",
            "whats",
            "youre",
            "theyre",
        )

    /** Written variants, folded, to the one key they share. A key may hold a stop word ("drum and bass"). */
    val EXPRESSIONS: Map<String, String> =
        buildMap {
            fun spell(
                key: String,
                vararg variants: String,
            ) {
                put(key, key)
                variants.forEach { put(it, key) }
            }
            spell("lofi", "lo-fi", "lo fi")
            spell("hip hop", "hip-hop", "hiphop")
            spell("standup", "stand-up", "stand up")
            spell("kpop", "k-pop", "k pop")
            spell("jpop", "j-pop", "j pop")
            spell("jrock", "j-rock", "j rock")
            spell("esports", "e-sports", "e sports", "esport", "e-sport")
            spell("scifi", "sci-fi", "sci fi")
            spell("wifi", "wi-fi", "wi fi")
            spell("xray", "x-ray")
            spell("tshirt", "t-shirt", "t shirt")
            spell("email", "e-mail")
            spell("ebook", "e-book")
            spell("rnb", "r&b", "r'n'b", "r n b")
            spell("drum and bass", "drum & bass", "drum n bass", "drum'n'bass", "dnb", "d&b")
            spell("rock and roll", "rock & roll", "rock n roll", "rock'n'roll", "rock 'n' roll")
            spell("diy", "d.i.y", "do it yourself")
            spell("ai", "a.i")
            spell("ufc", "u.f.c")
            spell("nba", "n.b.a")
            spell("nfl", "n.f.l")
            spell("ps5", "ps 5", "playstation 5")
            spell("ps4", "ps 4", "playstation 4")
            spell("playalong", "play-along", "play along")
            spell("tier list", "tier-list", "tierlist")
            // Names that hold a stop word, which would otherwise split them.
            spell("call of duty")
            spell("league of legends")
            spell("legend of zelda", "the legend of zelda")
            spell("lord of the rings")
            spell("game of thrones")
            spell("god of war")
            spell("attack on titan")
            spell("sea of thieves")
            spell("house of the dragon")
        }

    /** The keys [EXPRESSIONS] produce: they stay whole even when they hold a stop word. */
    val EXPRESSION_KEYS: Set<String> = EXPRESSIONS.values.toSet()

    /** The longest variant in words, so a scan knows how far ahead to look. */
    val LONGEST_EXPRESSION_WORDS: Int = EXPRESSIONS.keys.maxOf { it.count { c -> c == ' ' } + 1 }

    /** First words of the variants written as several words, so a scan only looks ahead from them. */
    val EXPRESSION_FIRST_WORDS: Set<String> = EXPRESSIONS.keys.filter { ' ' in it }.mapTo(HashSet()) { it.substringBefore(' ') }
}
