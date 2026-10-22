/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.model.Video
import kotlin.math.*

/**
 * Text processing: tokenization, lemmatization, IDF,
 * stop words, polysemy protection, description extraction.
 *
 * Stateless utility — can be shared across all other components.
 */
internal class NeuroTokenizer(
    /** Dictionary breaker for unspaced scripts. */
    private val wordBreaker: WordBreaker = PlatformWordBreaker,
) {
    companion object {
        val WHITESPACE_REGEX = Regex("\\s+")

        // ── Feature Extraction Constants ──
        const val IDF_COLD_START_DOCS = 30
        const val IDF_MIN_WEIGHT = 0.15
        const val IDF_MAX_WEIGHT = 1.0
        const val CHANNEL_KEYWORD_WEIGHT = 0.6
        const val TITLE_KEYWORD_WEIGHT = 0.5

        const val BIGRAM_PRIORITY_WEIGHT = 1.2
        const val DESCRIPTION_MIN_LENGTH = 20
        const val DESCRIPTION_TAKE_WORDS = 15
        const val DESCRIPTION_TAKE_LINES = 5
        const val DESCRIPTION_LINE_MIN_LENGTH = 15
        const val DESCRIPTION_WORD_WEIGHT = 0.2
        const val COMPLEXITY_TITLE_LEN_MAX = 80.0
        const val COMPLEXITY_TITLE_LEN_WEIGHT = 0.4
        const val COMPLEXITY_WORD_LEN_DIVISOR = 8.0
        const val COMPLEXITY_WORD_LEN_WEIGHT = 0.4
        const val COMPLEXITY_CHAPTER_BONUS = 0.2
        val TIMESTAMP_PATTERN = Regex("""\d{1,2}:\d{2}""")
        const val CHAPTER_TIMESTAMP_MIN = 3

        // ── Tag Processing Constants ──
        const val TAG_MAX_INGEST = 15
        const val TAG_PHRASE_MAX_WORDS = 3
        const val TAG_PHRASE_WORD_SHARE = 0.5
        const val HASHTAG_MAX = 5
        private val HASHTAG_REGEX = Regex("""#([\p{L}\p{N}_]{3,40})""")
        const val TAG_VERIFIED_WEIGHT = 0.65
        const val TAG_UNVERIFIED_WEIGHT = 0.10
        const val TAG_MIN_LENGTH = 3
        const val TAG_MAX_LENGTH = 40

        /** Matches any year 2020-2099, used to filter year-spam tags and tokens */
        private val YEAR_TAG_REGEX = Regex("^20[2-9]\\d$")
    }

    // ── Lemma Map ──

    private val lemmaMap =
        mapOf(
            // Gaming
            "gaming" to "game",
            "games" to "game",
            "gamer" to "game",
            "gamers" to "game",
            "gameplay" to "game",
            "gamed" to "game",
            // Coding / Programming
            "coding" to "code",
            "coder" to "code",
            "coders" to "code",
            "codes" to "code",
            "coded" to "code",
            "programming" to "program",
            "programmer" to "program",
            "programmers" to "program",
            "programs" to "program",
            "programmed" to "program",
            // Cooking
            "cooking" to "cook",
            "cooked" to "cook",
            "cooks" to "cook",
            "cooker" to "cook",
            // Music
            "songs" to "song",
            "singing" to "sing",
            "singer" to "sing",
            "singers" to "sing",
            "musics" to "music",
            "musical" to "music",
            "musician" to "music",
            "musicians" to "music",
            // Technology
            "technologies" to "technology",
            "technological" to "technology",
            "computers" to "computer",
            "computing" to "computer",
            "computed" to "computer",
            // Art
            "drawing" to "draw",
            "drawings" to "draw",
            "drawn" to "draw",
            "painting" to "paint",
            "paintings" to "paint",
            "painted" to "paint",
            "painter" to "paint",
            "animating" to "animation",
            "animated" to "animation",
            "animations" to "animation",
            "animator" to "animation",
            // Fitness
            "workouts" to "workout",
            "exercising" to "exercise",
            "exercises" to "exercise",
            "exercised" to "exercise",
            "learning" to "learn",
            "learned" to "learn",
            "learner" to "learn",
            "learners" to "learn",
            "teaching" to "teach",
            "teacher" to "teach",
            "teachers" to "teach",
            "taught" to "teach",
            "studying" to "study",
            "studies" to "study",
            "studied" to "study",
            "tutorials" to "tutorial",
            "making" to "make",
            "maker" to "make",
            "makers" to "make",
            "makes" to "make",
            "made" to "make",
            "reviewing" to "review",
            "reviewed" to "review",
            "reviews" to "review",
            "reviewer" to "review",
            "testing" to "test",
            "tested" to "test",
            "tests" to "test",
            "tester" to "test",
            "editing" to "edit",
            "edited" to "edit",
            "edits" to "edit",
            "editor" to "edit",
            "traveling" to "travel",
            "travelled" to "travel",
            "travels" to "travel",
            "traveler" to "travel",
            "vlogging" to "vlog",
            "vlogs" to "vlog",
            "vlogger" to "vlog",
            "vloggers" to "vlog",
            "reactions" to "reaction",
            "compilations" to "compilation",
            // Science
            "experiments" to "experiment",
            "experimenting" to "experiment",
            "experimental" to "experiment",
            "sciences" to "science",
            "scientific" to "science",
            "scientist" to "science",
            "engineering" to "engineer",
            "engineered" to "engineer",
            "engineers" to "engineer",
            "inventions" to "invention",
            "inventing" to "invention",
            "invented" to "invention",
            // Nature
            "animals" to "animal",
            // Lifestyle
            "recipes" to "recipe",
            "baking" to "bake",
            "baked" to "bake",
            "baker" to "bake",
            "gardening" to "garden",
            "gardens" to "garden",
            "photographing" to "photography",
            "photographs" to "photography",
            "photographer" to "photography",
            // Common verbs
            "explained" to "explain",
            "explains" to "explain",
            "explaining" to "explain",
            "created" to "create",
            "creates" to "create",
            "creating" to "create",
            "creator" to "create",
            "creators" to "create",
            "discovered" to "discover",
            "discovers" to "discover",
            "discovering" to "discover",
            "exploring" to "explore",
            "explored" to "explore",
            "explores" to "explore",
            "comparing" to "compare",
            "compared" to "compare",
            "compares" to "compare",
            "comparison" to "compare",
            "comparisons" to "compare",
            // Plurals
            "videos" to "video",
            "channels" to "channel",
            "episodes" to "episode",
            "movies" to "movie",
            "documentaries" to "documentary",
            "podcasts" to "podcast",
            "interviews" to "interview",
            "challenges" to "challenge",
            "montages" to "montage",
        )

    // ── Stop Words ──

    private val stopWords =
        hashSetOf(
            // Grammatical
            "the",
            "and",
            "for",
            "that",
            "this",
            "with",
            "you",
            "how",
            "what",
            "when",
            "mom",
            "types",
            "your",
            "which",
            "can",
            "make",
            "seen",
            "most",
            "into",
            "best",
            "from",
            "just",
            "about",
            "more",
            "some",
            "will",
            "one",
            "all",
            "would",
            "there",
            "their",
            "out",
            "not",
            "but",
            "have",
            "has",
            "been",
            "being",
            "was",
            "were",
            "are",
            // YouTube meta
            "video",
            "official",
            "channel",
            "review",
            "reaction",
            "full",
            "episode",
            "part",
            "new",
            "latest",
            "update",
            "hdr",
            "uhd",
            "fps",
            "live",
            "stream",
            "watch",
            "subscribe",
            "like",
            "comment",
            "share",
            "click",
            "link",
            "description",
            "below",
            "check",
            "dont",
            "miss",
            "must",
            "now",
            // Resolution tags
            "1080p",
            "720p",
            "480p",
            "360p",
            "240p",
            "144p",
            // Lemma-consistent
            "compilation",
            "montage",
            "reupload",
            "reup",
            "reuploaded",
            // Format / meta descriptors
            "guide",
            "tutorial",
            "tips",
            "tricks",
            "hack",
            "hacks",
            "lesson",
            "course",
            "class",
            "session",
            "step",
            "steps",
            "ways",
            "things",
            "stuff",
            "beginner",
            "beginners",
            "advanced",
            "intermediate",
            "basic",
            "basics",
            "introduction",
            "intro",
            "everything",
            "anything",
            "nothing",
            "something",
            "complete",
            "ultimate",
            "definitive",
            "easy",
            "simple",
            "hard",
            "difficult",
            "free",
            "paid",
            "cheap",
            "expensive",
            "first",
            "last",
            "next",
            "previous",
            // AI / prompt meta-words
            "prompt",
            "prompts",
            "prompting",
            // YouTube engagement bait
            "amazing",
            "insane",
            "crazy",
            "incredible",
            "unbelievable",
            "shocking",
            "exposed",
            "hilarious",
            // The platform itself, in nearly every title about anything (#907)
            "youtube",
            "revealed",
            "secret",
            "secrets",
            "honest",
            "truth",
            "proof",
            "finally",
            // High-frequency verbs with no topic meaning
            "use",
            "used",
            "using",
            "need",
            "want",
            "know",
            "help",
            "find",
            "look",
            "looking",
            "get",
            "got",
            "getting",
            "give",
            "gave",
            "keep",
            "kept",
            "tell",
            "told",
            "say",
            "said",
            "start",
            "stop",
            "try",
            "take",
            "took",
            // Filler adverbs / discourse fillers
            "really",
            "actually",
            "literally",
            "basically",
            "ever",
            "never",
            "always",
            "every",
            "still",
            "also",
            "too",
            "very",
            "only",
            "then",
            "than",
            "well",
            "even",
            "else",
            "did",
            "does",
            "done",
            "doing",
            "why",
            "these",
            "those",
            "here",
            "they",
            "them",
            "their",
            "made",
            "tried",
            // Filler adverbs that leaked into topic vectors as junk unigrams and
            // bigram halves ("laptops right" from "laptops right now").
            "right",
            "gonna",
            "wanna",
            "gotta",
            "honestly",
            "maybe",
            "probably",
            // English function words: they end a chunk, so "battery life after a month" never pairs
            // "life after" (#907). Other languages get their lists with the multilingual stop lists.
            "after",
            "before",
            "over",
            "under",
            "again",
            "once",
            "during",
            "while",
            "through",
            "between",
            "against",
            "without",
            "within",
            "until",
            "since",
            "upon",
            "onto",
            "off",
            "above",
            "near",
            "behind",
            "across",
            "along",
            "around",
            "among",
            "beside",
            "besides",
            "beyond",
            "inside",
            "outside",
            "toward",
            "towards",
            "despite",
            "unless",
            "whether",
            "although",
            "though",
            "because",
            "yet",
            "nor",
            "per",
            "via",
            "any",
            "both",
            "each",
            "few",
            "many",
            "much",
            "other",
            "others",
            "such",
            "same",
            "own",
            "our",
            "ours",
            "ourselves",
            "his",
            "her",
            "hers",
            "herself",
            "him",
            "himself",
            "its",
            "itself",
            "she",
            "who",
            "whom",
            "whose",
            "yours",
            "yourself",
            "yourselves",
            "theirs",
            "themselves",
            "myself",
            "had",
            "having",
            "should",
            "could",
            "may",
            "might",
            "shall",
            "let",
            "where",
            "later",
            "ago",
            "soon",
            "yes",
        )

    // ── Tag Spam Filter ──

    private val tagSpamWords =
        hashSetOf(
            // Creator/platform names used as SEO bait
            "mrbeast",
            "pewdiepie",
            "markiplier",
            "jacksepticeye",
            "dream",
            "tommyinnit",
            "pokimane",
            "ninja",
            // Broad SEO spam
            "viral",
            "trending",
            "fyp",
            "foryou",
            "foryoupage",
            "like",
            "subscribe",
            "comment",
            "share",
            "free",
            "giveaway",
            "win",
            // Platform names
            "youtube",
            "tiktok",
            "instagram",
            "twitter",
            "twitch",
            "shorts",
            "reels",
            "stories",
            // Generic engagement bait
            "funny",
            "amazing",
            "insane",
            "crazy",
            "incredible",
            "satisfying",
            "oddly satisfying",
            "gone wrong",
            "not clickbait",
            "must watch",
            "you wont believe",
            "emotional",
        )

    // ── Polysemy Data ──

    val polysemousWords =
        hashSetOf(
            "train",
            "model",
            "build",
            "plant",
            "stream",
            "react",
            "design",
            "film",
            "run",
            "play",
            "cook",
            "fire",
            "spring",
            "match",
            "cell",
            "power",
            "drive",
            "board",
            "frame",
            "scale",
            "lead",
            "light",
            "block",
            "drop",
            "track",
            "craft",
            "host",
            "mine",
            "pitch",
            "wave",
            "bass",
            "bow",
            "clip",
            "dart",
            "fan",
            "gear",
            "jam",
            "kit",
            "lab",
            "log",
            "net",
            "pad",
            "port",
            "rig",
            "set",
            "tap",
            "tip",
            "web",
            // Music genres / material / nature terms with multiple meanings
            "metal",
            "rock",
            "bar",
        )

    val compoundTerms =
        hashSetOf(
            // AI/ML
            "train model",
            "train ai",
            "machine learn",
            "deep learn",
            "neural network",
            "train data",
            "fine tune",
            "train network",
            "train system",
            "build model",
            "build ai",
            "build network",
            "model train",
            "model ai",
            "model build",
            // Programming
            "react native",
            "react component",
            "react hook",
            "react app",
            "react tutorial",
            "react project",
            "build system",
            "build tool",
            "build project",
            "run test",
            "run code",
            "run server",
            "run script",
            "design pattern",
            "design system",
            "web design",
            "game design",
            "sound design",
            // Specific disambiguations
            "power plant",
            "plant base",
            "plant based",
            "fire base",
            "fire wall",
            "fire fight",
            "spring boot",
            "spring framework",
            "spring board",
            "stream deck",
            "stream lab",
            "stream setup",
            "film make",
            "film edit",
            "film score",
            "scale model",
            "block chain",
            "block list",
            "host server",
            "host name",
            "web development",
            "web app",
            "web site",
            "net work",
            "net worth",
            // Music disambiguations
            "bass guitar",
            "bass boost",
            "bass drop",
            "drum track",
            "sound track",
            "race track",
            // Music
            "lofi hip hop",
            // Tech
            "game laptop",
            // Gaming
            "speed run",
            "play through",
            "build guide",
            "build order",
            "craft recipe",
            "mine craft",
        )

    val sponsorLinePatterns =
        listOf(
            "use code ",
            "% off",
            "free trial",
            "link in",
            "sponsored by",
            "brought to you",
            "check out",
            "sign up",
            "discount",
            "promo code",
            "coupon",
            "affiliate",
            "partner",
            "merch",
            "merchandise",
            "patreon",
            "ko-fi",
            "buymeacoffee",
            "buy me a coffee",
            "subscribe",
            "follow me",
            "social media",
            "instagram",
            "twitter",
            "tiktok",
            "discord",
            "join the",
            "become a member",
            "membership",
            "business inquiries",
            "business email",
            "contact:",
            "►",
            "→",
            "⬇",
            "⇩",
            "👇",
            "timestamps:",
            "chapters:",
        )

    // ── Pacing Keywords ──

    val highPacingWords =
        setOf(
            "compilation",
            "tiktok",
            "tiktoks",
            "highlights",
            "speedrun",
            "trailer",
            "shorts",
            "montage",
            "moments",
            "best of",
            "try not to",
            "memes",
            "funny",
            "fails",
            "rapid",
            "fast",
            "quick",
            "minute",
            "seconds",
            "top 10",
            "top 5",
            "ranked",
            "tier list",
            "versus",
        )

    val lowPacingWords =
        setOf(
            "podcast",
            "essay",
            "ambient",
            "explained",
            "study",
            "meditation",
            "sleep",
            "asmr",
            "relaxing",
            "calm",
            "deep dive",
            "analysis",
            "lecture",
            "course",
            "documentary",
            "interview",
            "conversation",
            "discussion",
            "breakdown",
            "walkthrough",
        )

    // ── Functions ──

    fun normalizeLemma(word: String): String = lemmaMap[word.lowercase()] ?: word.lowercase()

    /** The viewer's language ([UserBrain.languageProfile]); its stop words apply to every title. */
    @Volatile
    var languageProfile: String = "en"

    /**
     * The language keys are stemmed for ([UserBrain.keyLanguage]). Unlike [languageProfile] it never
     * follows what the viewer watches, so a word keeps one key for the life of a brain.
     */
    @Volatile
    var keyLanguage: String = "en"

    @Volatile
    private var specificityCache: Pair<String, NeuroSpecificity>? = null

    /** How specific [key] is in general language ([NeuroSpecificity]), for English and this viewer's languages. */
    fun specificity(key: String): Double {
        val languages = "$keySpec|$keyLanguage|$languageProfile"
        val cached = specificityCache?.takeIf { it.first == languages }?.second
        val current =
            cached ?: NeuroSpecificity(::keyOf, listOf("en", keyLanguage, languageProfile)).also { specificityCache = languages to it }
        return current.of(key)
    }

    /** The [NeuroKeySpec] keys are made by. Spec 1 keeps lemmas; migrations up to schema 18 read keys by it. */
    var keySpec: Int = NeuroKeySpec.VERSION
        private set

    /** A tokenizer that reads keys by [spec] for the same viewer. */
    fun atKeySpec(spec: Int): NeuroTokenizer =
        NeuroTokenizer(wordBreaker).also {
            it.keySpec = spec
            it.languageProfile = languageProfile
            it.keyLanguage = keyLanguage
        }

    /** Words no language's stop list may remove: they are topics here, whatever they mean elsewhere. */
    internal val protectedWords: Set<String> by lazy {
        buildSet {
            addAll(NeuroText.SHORT_TOPICS)
            addAll(lemmaMap.values)
            addAll(polysemousWords)
            addAll(alwaysTopical)
            NeuroKeySpec.EXPRESSION_KEYS.forEach { addAll(it.split(' ')) }
            compoundTerms.forEach { addAll(it.split(' ')) }
            NeuroTopicCatalog.TOPIC_CATEGORIES.forEach { category ->
                category.topics.forEach { topic ->
                    addAll(NeuroText.words(fold(topic), wordBreaker).map { it.trim { c -> !NeuroText.isWordChar(c) } })
                }
            }
        }
    }

    /** The lemma map and the stop words every title gets, for the tables Flow Desktop reads ([NeuroKeySpec]). */
    internal val lemmas: Map<String, String> get() = lemmaMap
    internal val baseStopWords: Set<String> get() = stopWords

    /** Words the tokenizer's own lists compare keys with; key spec 2 never stems them ([NeuroStemmer]). */
    internal val curatedWords: Set<String> by lazy {
        buildSet {
            addAll(protectedWords)
            addAll(NeuroKeySpec.NEVER_ALONE)
            addAll(channelBranding)
            addAll(highPacingWords)
            addAll(lowPacingWords)
            domainDisambiguation.forEach { (word, contexts) ->
                add(word)
                contexts.forEach { context ->
                    add(context.domain)
                    addAll(context.contextWords)
                }
            }
        }
    }

    private val stopLists = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    /** [language]'s stop words, less the [protectedWords]. */
    fun stopList(language: String): Set<String> = stopLists.getOrPut(language) { NeuroLanguages.stopList(language) - protectedWords }

    private fun stopWordsFor(pieces: List<String>): (String) -> Boolean {
        val profile = stopList(languageProfile)
        val title = NeuroLanguages.detect(pieces)?.takeIf { it != languageProfile }?.let(::stopList)
        return if (title.isNullOrEmpty()) {
            { word -> word in stopWords || word in profile }
        } else {
            { word -> word in stopWords || word in profile || word in title }
        }
    }

    /**
     * [word]'s key ([NeuroKeySpec] stage 8): its lemma stemmed for [keyLanguage] by
     * [NeuroStemmer], or the lemma when the stem is itself a lemma-map word. A key is its own key.
     */
    fun keyOf(word: String): String {
        val lemma = normalizeLemma(word)
        if (keySpec < 2) return lemma
        val stem = NeuroStemmer.key(lemma, keyLanguage)
        return if (normalizeLemma(stem) == stem) stem else lemma
    }

    private val chunker by lazy {
        NeuroChunker(::stopWordsFor, ::normalizeLemma, ::keyOf, wordBreaker)
    }

    /** [NeuroKeySpec.HAN_SPEC] and a non-`ja` viewer: keys fold Hant and merge Han runs. */
    val hanFolds: Boolean get() = keySpec >= NeuroKeySpec.HAN_SPEC && languageProfile != "ja"

    private val defaultHan: HanFold get() = if (hanFolds) HanFold.UNLESS_KANA else HanFold.NONE

    /** One fold for every field of [video]; kana in the title, channel name or tags turns it off. */
    fun hanFoldOf(video: Video): HanFold = if (hanFolds && !hasKana(video)) HanFold.ALWAYS else HanFold.NONE

    private fun hasKana(video: Video): Boolean =
        NeuroText.hasKana(video.title) || NeuroText.hasKana(video.channelName) ||
            video.tags.take(TAG_MAX_INGEST).any(NeuroText::hasKana)

    fun fold(
        text: String,
        han: HanFold = defaultHan,
    ): String = NeuroText.fold(text, han)

    /** Multi-word catalog topics, keyed the way a title spells them. */
    private val catalogPhrases: Set<String> by lazy {
        NeuroTopicCatalog.TOPIC_CATEGORIES
            .flatMap { it.topics }
            .mapNotNull { topic ->
                chunker
                    .chunk(topic, defaultHan)
                    .chunks
                    .singleOrNull()
                    ?.takeIf { it.size >= 2 }
                    ?.joinToString(" ")
            }.toSet()
    }

    /** Every catalog topic as titles key it, single words and phrases alike ("Hip Hop" reads "hip hop"). */
    val catalogKeys: Set<String> by lazy {
        NeuroTopicCatalog.TOPIC_CATEGORIES
            .flatMap { it.topics }
            .flatMapTo(HashSet()) { keysOf(it) }
    }

    /** [text] read by [NeuroKeySpec]: its chunks of words that belong together. */
    fun chunk(
        text: String,
        han: HanFold = defaultHan,
    ): NeuroChunker.Chunks = chunker.chunk(text, han)

    fun tokenize(
        text: String,
        han: HanFold = defaultHan,
    ): List<String> = chunker.chunk(text, han).words

    /**
     * The keys a short label stands for, such as an onboarding pick: one per chunk, so "Lo-Fi" reads
     * "lofi" and "Health & Fitness" reads "health" and "fitness", the keys titles produce.
     */
    fun keysOf(label: String): List<String> =
        chunker
            .chunk(label, defaultHan)
            .chunks
            .map { it.joinToString(" ") }
            .ifEmpty { listOfNotNull(keyOf(fold(label).trim()).takeIf { it.isNotBlank() }) }

    fun tokenizeForSimilarity(text: String): Set<String> = tokenize(text).toSet()

    /**
     * True when a learned topic key is vocabulary noise rather than an interest:
     * too short, numeric, a stop word, or a uni/bigram whose parts are stop words.
     * Used to scrub historical junk from vectors and to gate rehydration seeds.
     */
    fun isNoiseTopic(topic: String): Boolean {
        val base = topic.substringBefore(':').lowercase().trim()
        if (base in NeuroKeySpec.EXPRESSION_KEYS) return false
        if (base in NeuroKeySpec.NEVER_ALONE || base.length > NeuroKeySpec.MAX_KEY_LENGTH) return true
        if (NeuroFormats.formatOf(base) != null) return true
        if (!NeuroText.isTopicSized(base)) return true
        if (base.all { it.isDigit() } || YEAR_TAG_REGEX.matches(base)) return true
        val parts = base.split(' ').filter { it.isNotBlank() }
        if (parts.isEmpty()) return true
        // Degenerate bigrams like "code code" are tokenizer echoes, not topics.
        if (parts.size > 1 && parts.distinct().size < parts.size) return true
        return parts.any { part ->
            part in stopWords || normalizeLemma(part) in stopWords || part in stopList(languageProfile) || !NeuroText.isTopicSized(part)
        }
    }

    fun calculateIdfWeight(
        word: String,
        baseWeight: Double,
        idfSnapshot: IdfSnapshot,
    ): Double {
        if (idfSnapshot.totalDocs < IDF_COLD_START_DOCS) return baseWeight

        val df = idfSnapshot.wordFrequency[word] ?: 0
        val idf = ln(1.0 + idfSnapshot.totalDocs.toDouble() / (df + 1.0))
        val maxIdf = ln(1.0 + idfSnapshot.totalDocs.toDouble())
        val normalizedIdf = (idf / maxIdf).coerceIn(IDF_MIN_WEIGHT, IDF_MAX_WEIGHT)

        return baseWeight * normalizedIdf
    }

    fun extractFeatures(
        video: Video,
        idfSnapshot: IdfSnapshot,
        channelTopicProfile: Map<String, Double>? = null,
    ): ContentVector {
        val topics = mutableMapOf<String, Double>()
        val han = hanFoldOf(video)

        val title = chunker.chunk(video.title, han)
        val titleWords = title.words

        // Use smart channel tokenization to filter names and branding
        val chWords = tokenizeChannelName(video.channelName, han)

        // Channel keywords — filtered for actual topic words only
        chWords.forEach {
            topics[it] = calculateIdfWeight(it, CHANNEL_KEYWORD_WEIGHT, idfSnapshot)
        }

        // Collect all words for domain disambiguation context BEFORE processing tokens
        val fullContext = buildContextWordList(video, han)
        val tags = topicTags(video)
        val tagChunks = tags.take(TAG_MAX_INGEST).map { chunker.chunk(it, han) }
        val tagPhrases = tagChunks.flatMapTo(HashSet()) { tagPhrases(it) }
        val formats = title.formats + tagChunks.flatMap { it.formats }

        // A run of the title is a key only once it is known to be a phrase; until then it only counts
        // toward document frequency, which is how it can become one (#907). A phrase takes its words.
        val claimed = HashSet<Long>()
        title.runs().forEach { run ->
            if (isPromoted(run.key, idfSnapshot, tagPhrases)) {
                topics[run.key] = calculateIdfWeight(run.key, BIGRAM_PRIORITY_WEIGHT, idfSnapshot)
                for (i in run.from until run.to) claimed += position(run.chunk, i)
            }
        }
        title.merged.forEach { run ->
            if (NeuroPhrases.isMergedRun(run, idfSnapshot) || run in tagPhrases) {
                topics[run] = calculateIdfWeight(run, BIGRAM_PRIORITY_WEIGHT, idfSnapshot)
            }
        }
        title.chunks.forEachIndexed { chunkIndex, chunk ->
            chunk.forEachIndexed { index, word ->
                if (position(chunkIndex, index) in claimed || word in NeuroKeySpec.NEVER_ALONE) return@forEachIndexed
                // Domain-disambiguate polysemous words using full title+description context
                val resolvedWord =
                    if (word in domainDisambiguation) {
                        disambiguateWord(word, fullContext)
                    } else {
                        word
                    }
                topics[resolvedWord] = (
                    topics.getOrDefault(resolvedWord, 0.0) +
                        calculateIdfWeight(resolvedWord, TITLE_KEYWORD_WEIGHT, idfSnapshot)
                )
            }
        }

        // ── V9.3 Fix 5: Smart description extraction ──
        val descriptionTopics =
            extractDescriptionKeywords(
                video.description,
                idfSnapshot,
                han,
            )
        descriptionTopics.forEach { (word, weight) ->
            // Also disambiguate description words using the same full context
            val resolved =
                if (word in domainDisambiguation) {
                    disambiguateWord(word, fullContext)
                } else {
                    word
                }
            topics[resolved] = (topics.getOrDefault(resolved, 0.0) + weight)
        }

        // ── Tag Processing ──
        if (tags.isNotEmpty()) {
            val descWords =
                if (!video.description.isNullOrBlank()) {
                    tokenize(
                        video.description
                            .lines()
                            .take(DESCRIPTION_TAKE_LINES)
                            .joinToString(" "),
                    )
                } else {
                    emptyList()
                }

            val tagTopics =
                processTags(
                    tags,
                    titleWords,
                    descWords,
                    idfSnapshot,
                    han,
                )
            tagTopics.forEach { (word, weight) ->
                topics[word] = (topics[word] ?: 0.0) + weight
            }
        }

        topics.keys.removeAll(::isNoiseTopic)

        // ── Channel Topic Prior ──
        if (channelTopicProfile != null &&
            channelTopicProfile.size >= NeuroScoring.CHANNEL_PROFILE_MIN_VIDEOS
        ) {
            channelTopicProfile.forEach { (topic, channelWeight) ->
                val existing = topics[topic] ?: 0.0
                if (existing == 0.0) {
                    topics[topic] = channelWeight * NeuroScoring.CHANNEL_PROFILE_BLEND_WEIGHT
                }
            }
        }

        // Normalize topic vector
        val normalized =
            if (topics.isNotEmpty()) {
                var magnitude = 0.0
                topics.values.forEach { magnitude += it * it }
                magnitude = sqrt(magnitude)
                if (magnitude > 0) {
                    topics.mapValues { (_, v) -> v / magnitude }
                } else {
                    topics
                }
            } else {
                topics
            }

        val durationSec =
            when {
                video.duration > 0 -> video.duration
                video.isLive -> 3600
                else -> 300
            }
        val durationScore =
            (
                ln(1.0 + durationSec) /
                    ln(1.0 + 7200.0)
            ).coerceIn(0.0, 1.0)

        val pacingScore =
            run {
                val titleLower = video.title.lowercase()

                val highCount =
                    highPacingWords.count {
                        titleLower.contains(it)
                    }
                val lowCount =
                    lowPacingWords.count {
                        titleLower.contains(it)
                    }

                when {
                    highCount > lowCount -> {
                        (0.6 + highCount * 0.1)
                            .coerceAtMost(0.95)
                    }

                    lowCount > highCount -> {
                        (0.4 - lowCount * 0.1)
                            .coerceAtLeast(0.05)
                    }

                    video.isShort -> {
                        0.85
                    }

                    else -> {
                        0.5
                    }
                }
            }

        val description = video.description
        val hasChapters =
            if (!description.isNullOrBlank()) {
                TIMESTAMP_PATTERN.findAll(description).count() >= CHAPTER_TIMESTAMP_MIN
            } else {
                false
            }

        val rawTitleWords =
            video.title
                .split(WHITESPACE_REGEX)
                .filter { it.length > 1 }

        val complexityScore =
            run {
                val titleLenFactor =
                    (
                        video.title.length /
                            COMPLEXITY_TITLE_LEN_MAX
                    ).coerceIn(0.0, COMPLEXITY_TITLE_LEN_WEIGHT)
                val avgWordLen =
                    if (rawTitleWords.isNotEmpty()) {
                        rawTitleWords.map { it.length }.average()
                    } else {
                        4.0
                    }
                val wordLenFactor =
                    (
                        avgWordLen /
                            COMPLEXITY_WORD_LEN_DIVISOR
                    ).coerceIn(0.0, COMPLEXITY_WORD_LEN_WEIGHT)
                val chapterBonus = if (hasChapters) COMPLEXITY_CHAPTER_BONUS else 0.0
                (titleLenFactor + wordLenFactor + chapterBonus)
                    .coerceIn(0.0, 1.0)
            }

        return ContentVector(
            topics = normalized,
            duration = durationScore,
            pacing = pacingScore,
            complexity = complexityScore,
            isLive = if (video.isLive) 1.0 else 0.0,
            formats = formats,
        )
    }

    /** The creator's tags, then the description's hashtags, which the description reader skips. */
    fun topicTags(video: Video): List<String> {
        val description = video.description
        if (description.isNullOrBlank() || '#' !in description) return video.tags
        val hashtags =
            HASHTAG_REGEX
                .findAll(description)
                .map { it.groupValues[1].replace('_', ' ') }
                .take(HASHTAG_MAX)
                .toList()
        return (video.tags + hashtags).distinct()
    }

    fun extractDescriptionKeywords(
        description: String?,
        idfSnapshot: IdfSnapshot,
        han: HanFold = defaultHan,
    ): Map<String, Double> {
        if (description.isNullOrBlank() || description.length < DESCRIPTION_MIN_LENGTH) {
            return emptyMap()
        }

        val contentLines =
            description
                .lines()
                .filter { line ->
                    val lower = line.lowercase().trim()
                    line.trim().length > DESCRIPTION_LINE_MIN_LENGTH &&
                        !sponsorLinePatterns.any { pattern -> lower.contains(pattern) } &&
                        !lower.contains("http") &&
                        !lower.trimStart().startsWith("#") &&
                        !(line.trim().length > 5 && isShouted(line))
                }.take(DESCRIPTION_TAKE_LINES)
                .joinToString(" ")

        if (contentLines.isBlank()) return emptyMap()

        val words = tokenize(contentLines, han).take(DESCRIPTION_TAKE_WORDS)
        val result = mutableMapOf<String, Double>()
        words.forEach { word ->
            result[word] = (
                result.getOrDefault(word, 0.0) +
                    calculateIdfWeight(word, DESCRIPTION_WORD_WEIGHT, idfSnapshot)
            )
        }
        return result
    }

    /**
     * An all-caps line ("SUBSCRIBE NOW") or one with no letters at all. Chinese, Japanese and Thai
     * have no case, so their letters count against the capitals: "【MV】周杰倫 新歌" is not shouting (#1395).
     */
    private fun isShouted(line: String): Boolean {
        var capitals = 0
        var uncased = 0
        line.forEach { c ->
            when {
                c.isLowerCase() -> return false
                c.isUpperCase() -> capitals++
                c.isLetter() -> uncased++
            }
        }
        return capitals >= uncased
    }

    // ══════════════════════════════════════════════
    // TAG PROCESSING
    // Tags are the strongest contextual signal for learning —
    // the creator explicitly declared what the video is about.
    // Only available on videos the user opens (StreamInfo),
    // NOT on search result candidates (StreamInfoItem).
    // ══════════════════════════════════════════════

    fun processTags(
        tags: List<String>,
        titleWords: List<String>,
        descriptionWords: List<String>,
        idfSnapshot: IdfSnapshot,
        han: HanFold = defaultHan,
    ): Map<String, Double> {
        if (tags.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Double>()

        val verificationSet = (titleWords + descriptionWords).toHashSet()

        val tagContextWords =
            tags.take(TAG_MAX_INGEST).flatMap { t ->
                NeuroText
                    .fold(t, han)
                    .split(WHITESPACE_REGEX)
                    .map { it.trim { c -> !c.isLetterOrDigit() } }
                    .filter { it.length > 1 }
            }
        val fullDisambiguationContext = tagContextWords + titleWords + descriptionWords

        tags.take(TAG_MAX_INGEST).forEach { rawTag ->
            val cleaned = fold(rawTag, han).trim()

            if ((cleaned.length < TAG_MIN_LENGTH && cleaned !in NeuroText.SHORT_TOPICS) ||
                cleaned.length > TAG_MAX_LENGTH
            ) {
                return@forEach
            }

            if (cleaned in tagSpamWords) return@forEach
            if (cleaned in stopWords) return@forEach
            if (YEAR_TAG_REGEX.matches(cleaned)) return@forEach

            val chunked = chunker.chunk(cleaned, han)
            val tagTokens = chunked.words
            if (tagTokens.isEmpty()) return@forEach

            val isVerified = tagTokens.any { token -> token in verificationSet }

            val baseWeight =
                if (isVerified) {
                    TAG_VERIFIED_WEIGHT
                } else {
                    TAG_UNVERIFIED_WEIGHT
                }

            // A creator's multi-word tag is a phrase they chose; each unbroken part stays whole
            // ("guitar playalong", and "stand up comedy" as "standup comedy", never "stand comedy").
            val phrases = tagPhrases(chunked)
            phrases.forEach { phrase ->
                result[phrase] = (result[phrase] ?: 0.0) + calculateIdfWeight(phrase, baseWeight, idfSnapshot)
            }
            val wordWeight = if (phrases.isNotEmpty()) baseWeight * TAG_PHRASE_WORD_SHARE else baseWeight
            tagTokens.forEach { token ->
                if (token in NeuroKeySpec.NEVER_ALONE) return@forEach
                val resolved =
                    if (token in domainDisambiguation) {
                        disambiguateWord(token, fullDisambiguationContext)
                    } else {
                        token
                    }

                val idfWeighted = calculateIdfWeight(resolved, wordWeight, idfSnapshot)

                result[resolved] = (result[resolved] ?: 0.0) + idfWeighted
            }
        }

        return result
    }

    /** The unbroken parts of a tag that hold two or three words. */
    private fun tagPhrases(tag: NeuroChunker.Chunks): List<String> =
        tag.chunks
            .filter { chunk -> chunk.size >= 2 && chunk.sumOf { it.count { c -> c == ' ' } + 1 } <= TAG_PHRASE_MAX_WORDS }
            .map { it.joinToString(" ") }

    /** A run that is an interest on its own: a compound, a catalog topic, or a pair [NeuroPhrases] has learned. */
    fun isKnownPhrase(
        run: String,
        idfSnapshot: IdfSnapshot,
    ): Boolean =
        run in compoundTerms ||
            run in catalogPhrases ||
            run in NeuroKeySpec.EXPRESSION_KEYS ||
            (run.count { it == ' ' } == 1 && NeuroPhrases.isPhrase(run, idfSnapshot))

    private fun isPromoted(
        run: String,
        idfSnapshot: IdfSnapshot,
        tagPhrases: Set<String>,
    ): Boolean = run in tagPhrases || isKnownPhrase(run, idfSnapshot)

    private fun position(
        chunk: Int,
        index: Int,
    ): Long = (chunk.toLong() shl 32) or index.toLong()

    /** What one video counts toward document frequency: its keys, and every word and run of its title (#907). */
    fun documentTerms(
        video: Video,
        vector: ContentVector,
    ): Set<String> {
        val title = chunker.chunk(video.title, hanFoldOf(video))
        return HashSet<String>(vector.topics.keys).apply {
            addAll(title.words)
            title.runs().forEach { add(it.key) }
            addAll(title.modelPairs)
            addAll(title.merged)
            addAll(title.singles)
        }
    }

    /**
     * Which keys of [video] the acquisition floor may plant: known phrases, tag phrases its title or
     * description confirms, words two of its fields repeat, and words another video already used.
     * [ownDocuments] is how many of [idfSnapshot]'s counts are [video] itself. A bare title the viewer
     * meets for the first time plants nothing (#907).
     */
    fun plantFilter(
        video: Video,
        idfSnapshot: IdfSnapshot,
        ownDocuments: Int,
    ): (String) -> Boolean {
        val confirmed = plantableKeys(video, idfSnapshot)
        return { key ->
            val base = NeuroScoring.stripDomainTag(key)
            base in confirmed || (' ' !in base && (idfSnapshot.wordFrequency[key] ?: 0) > ownDocuments)
        }
    }

    private fun plantableKeys(
        video: Video,
        idfSnapshot: IdfSnapshot,
    ): Set<String> {
        val han = hanFoldOf(video)
        val title = chunker.chunk(video.title, han)
        val titleWords = title.words.toSet()
        val tags = topicTags(video).take(TAG_MAX_INGEST).map { chunker.chunk(it, han) }
        val tagWords = tags.flatMapTo(HashSet()) { it.words }
        val descriptionWords =
            video.description
                .takeIf { it.isNotBlank() }
                ?.let { tokenize(it.lines().take(DESCRIPTION_TAKE_LINES).joinToString(" "), han).toSet() }
                .orEmpty()
        val confirmedTags = tags.filter { tag -> tag.words.any { it in titleWords || it in descriptionWords } }
        val tagPhrases = tags.flatMapTo(HashSet(), ::tagPhrases)
        return buildSet {
            addAll(titleWords intersect tagWords)
            addAll(titleWords intersect descriptionWords)
            addAll(tagWords intersect descriptionWords)
            confirmedTags.forEach { addAll(tagPhrases(it)) }
            title.runs().forEach { run -> if (isPromoted(run.key, idfSnapshot, tagPhrases)) add(run.key) }
        }
    }

    // ══════════════════════════════════════════════
    // DOMAIN CONTEXT SYSTEM
    // Words can mean different things in different domains.
    // We use surrounding words to determine which domain
    // a polysemous word belongs to, then tag it accordingly.
    //
    // "metal" + music context → "metal:music" (won't match metalworking)
    // "metal" + craft context → "metal:craft" (won't match music)
    // "rock"  + music context → "rock:music"
    // "rock"  + nature context → "rock:nature"
    // ══════════════════════════════════════════════

    private data class DomainContext(
        val domain: String,
        val contextWords: Set<String>,
    )

    private val domainDisambiguation: Map<String, List<DomainContext>> =
        mapOf(
            "metal" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "song",
                                "band",
                                "album",
                                "music",
                                "concert",
                                "guitar",
                                "drum",
                                "vocal",
                                "sing",
                                "lyric",
                                "playlist",
                                "tour",
                                "riff",
                                "solo",
                                "anthem",
                                "heavy",
                                "death",
                                "black",
                                "thrash",
                                "power",
                                "symphonic",
                                "progressive",
                                "doom",
                                "genre",
                                "metalcore",
                                "deathcore",
                                "djent",
                                "cover",
                                "react",
                                "reaction",
                                "review",
                                "rank",
                                "tier",
                            ),
                    ),
                    DomainContext(
                        domain = "craft",
                        contextWords =
                            setOf(
                                "weld",
                                "forge",
                                "fabricat",
                                "sheet",
                                "steel",
                                "iron",
                                "aluminum",
                                "copper",
                                "brass",
                                "tin",
                                "alloy",
                                "workshop",
                                "tool",
                                "cut",
                                "bend",
                                "grind",
                                "polish",
                                "cast",
                                "mold",
                                "anvil",
                                "hammer",
                                "lathe",
                                "cnc",
                                "machin",
                                "work",
                                "shop",
                                "build",
                                "construct",
                            ),
                    ),
                ),
            "rock" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "song",
                                "band",
                                "album",
                                "music",
                                "concert",
                                "guitar",
                                "drum",
                                "vocal",
                                "sing",
                                "lyric",
                                "playlist",
                                "tour",
                                "classic",
                                "punk",
                                "indie",
                                "alternative",
                                "grunge",
                                "blues",
                                "roll",
                                "genre",
                                "cover",
                                "anthem",
                                "react",
                                "reaction",
                                "review",
                                "rank",
                                "tier",
                            ),
                    ),
                    DomainContext(
                        domain = "nature",
                        contextWords =
                            setOf(
                                "geology",
                                "mineral",
                                "stone",
                                "fossil",
                                "gem",
                                "crystal",
                                "boulder",
                                "cliff",
                                "mountain",
                                "cave",
                                "collect",
                                "specimen",
                                "earth",
                                "volcanic",
                                "sediment",
                                "igneous",
                                "metamorphic",
                                "quarry",
                                "dig",
                                "formation",
                            ),
                    ),
                    DomainContext(
                        domain = "climbing",
                        contextWords =
                            setOf(
                                "climb",
                                "boulder",
                                "wall",
                                "route",
                                "belay",
                                "rope",
                                "harness",
                                "crag",
                                "send",
                                "flash",
                                "dyno",
                                "crimp",
                                "sloper",
                            ),
                    ),
                ),
            "bass" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "guitar",
                                "song",
                                "music",
                                "band",
                                "play",
                                "riff",
                                "slap",
                                "fretless",
                                "amp",
                                "pedal",
                                "tone",
                                "groove",
                                "funk",
                                "jazz",
                                "solo",
                                "lesson",
                                "boost",
                                "drop",
                                "sub",
                                "speaker",
                                "audio",
                                "headphone",
                                "equalizer",
                                "frequency",
                                "woofer",
                            ),
                    ),
                    DomainContext(
                        domain = "fishing",
                        contextWords =
                            setOf(
                                "fish",
                                "fishing",
                                "lure",
                                "bait",
                                "catch",
                                "lake",
                                "river",
                                "pond",
                                "boat",
                                "rod",
                                "reel",
                                "tackle",
                                "tournament",
                                "largemouth",
                                "smallmouth",
                                "striped",
                            ),
                    ),
                ),
            "spring" to
                listOf(
                    DomainContext(
                        domain = "tech",
                        contextWords =
                            setOf(
                                "boot",
                                "java",
                                "framework",
                                "api",
                                "microservice",
                                "backend",
                                "server",
                                "code",
                                "program",
                                "develop",
                                "application",
                                "deploy",
                                "config",
                                "bean",
                                "inject",
                            ),
                    ),
                    DomainContext(
                        domain = "season",
                        contextWords =
                            setOf(
                                "summer",
                                "winter",
                                "fall",
                                "autumn",
                                "season",
                                "flower",
                                "garden",
                                "bloom",
                                "clean",
                                "outfit",
                                "fashion",
                                "weather",
                                "april",
                                "march",
                                "may",
                            ),
                    ),
                ),
            "cell" to
                listOf(
                    DomainContext(
                        domain = "biology",
                        contextWords =
                            setOf(
                                "biology",
                                "science",
                                "membrane",
                                "nucleus",
                                "mitosis",
                                "dna",
                                "rna",
                                "protein",
                                "organism",
                                "tissue",
                                "bacteria",
                                "virus",
                                "microscope",
                            ),
                    ),
                    DomainContext(
                        domain = "tech",
                        contextWords =
                            setOf(
                                "phone",
                                "mobile",
                                "battery",
                                "charge",
                                "signal",
                                "carrier",
                                "data",
                                "plan",
                                "network",
                                "sim",
                            ),
                    ),
                    DomainContext(
                        domain = "energy",
                        contextWords =
                            setOf(
                                "solar",
                                "fuel",
                                "battery",
                                "power",
                                "energy",
                                "electric",
                                "hydrogen",
                                "lithium",
                                "volt",
                            ),
                    ),
                ),
            "plant" to
                listOf(
                    DomainContext(
                        domain = "botany",
                        contextWords =
                            setOf(
                                "grow",
                                "garden",
                                "flower",
                                "seed",
                                "soil",
                                "water",
                                "pot",
                                "leaf",
                                "root",
                                "tree",
                                "herb",
                                "indoor",
                                "outdoor",
                                "succulent",
                                "cactus",
                                "propagat",
                                "prune",
                                "fertiliz",
                                "bloom",
                            ),
                    ),
                    DomainContext(
                        domain = "industrial",
                        contextWords =
                            setOf(
                                "power",
                                "factory",
                                "nuclear",
                                "chemical",
                                "manufacturing",
                                "industrial",
                                "facility",
                                "process",
                                "production",
                                "refinery",
                            ),
                    ),
                ),
            "pitch" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "sing",
                                "vocal",
                                "note",
                                "tune",
                                "perfect",
                                "music",
                                "tone",
                                "frequency",
                                "ear",
                                "train",
                            ),
                    ),
                    DomainContext(
                        domain = "business",
                        contextWords =
                            setOf(
                                "startup",
                                "investor",
                                "shark",
                                "tank",
                                "business",
                                "company",
                                "funding",
                                "venture",
                                "present",
                                "elevator",
                                "idea",
                                "million",
                            ),
                    ),
                    DomainContext(
                        domain = "sport",
                        contextWords =
                            setOf(
                                "baseball",
                                "cricket",
                                "throw",
                                "ball",
                                "strike",
                                "mound",
                                "pitcher",
                                "fastball",
                                "curve",
                                "slider",
                            ),
                    ),
                ),
            "jam" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "guitar",
                                "band",
                                "music",
                                "session",
                                "play",
                                "improvise",
                                "solo",
                                "groove",
                                "funk",
                                "blues",
                                "live",
                                "concert",
                            ),
                    ),
                    DomainContext(
                        domain = "food",
                        contextWords =
                            setOf(
                                "recipe",
                                "fruit",
                                "strawberry",
                                "preserve",
                                "spread",
                                "toast",
                                "jar",
                                "cook",
                                "homemade",
                                "sugar",
                                "berry",
                                "grape",
                            ),
                    ),
                ),
            "bar" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "beat",
                                "measure",
                                "rhythm",
                                "rap",
                                "flow",
                                "lyric",
                                "verse",
                                "hook",
                                "produce",
                                "music",
                            ),
                    ),
                    DomainContext(
                        domain = "fitness",
                        contextWords =
                            setOf(
                                "pull",
                                "deadlift",
                                "squat",
                                "bench",
                                "weight",
                                "gym",
                                "workout",
                                "exercise",
                                "lift",
                                "olympic",
                            ),
                    ),
                ),
            "wave" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "synth",
                                "new",
                                "retro",
                                "synthwave",
                                "vaporwave",
                                "music",
                                "genre",
                                "aesthetic",
                                "80s",
                                "electronic",
                            ),
                    ),
                    DomainContext(
                        domain = "science",
                        contextWords =
                            setOf(
                                "ocean",
                                "sound",
                                "light",
                                "electromagnetic",
                                "frequency",
                                "physics",
                                "energy",
                                "radio",
                                "seismic",
                                "tsunami",
                                "surf",
                            ),
                    ),
                    DomainContext(
                        domain = "hair",
                        contextWords =
                            setOf(
                                "hair",
                                "style",
                                "curl",
                                "360",
                                "brush",
                                "barber",
                                "durag",
                                "pattern",
                                "pomade",
                            ),
                    ),
                ),
            "track" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "song",
                                "album",
                                "music",
                                "beat",
                                "produce",
                                "mix",
                                "master",
                                "record",
                                "artist",
                                "release",
                                "single",
                                "feature",
                                "lyric",
                                "audio",
                            ),
                    ),
                    DomainContext(
                        domain = "sport",
                        contextWords =
                            setOf(
                                "race",
                                "run",
                                "lap",
                                "sprint",
                                "field",
                                "athlete",
                                "relay",
                                "hurdle",
                                "meter",
                                "olympic",
                                "nascar",
                                "formula",
                                "circuit",
                                "drift",
                            ),
                    ),
                ),
            "model" to
                listOf(
                    DomainContext(
                        domain = "ai",
                        contextWords =
                            setOf(
                                "train",
                                "machine",
                                "learn",
                                "neural",
                                "network",
                                "data",
                                "predict",
                                "gpt",
                                "llm",
                                "parameter",
                                "fine",
                                "tune",
                                "inference",
                                "deploy",
                                "weight",
                            ),
                    ),
                    DomainContext(
                        domain = "fashion",
                        contextWords =
                            setOf(
                                "fashion",
                                "runway",
                                "pose",
                                "photo",
                                "shoot",
                                "agency",
                                "walk",
                                "beauty",
                                "vogue",
                                "style",
                            ),
                    ),
                    DomainContext(
                        domain = "hobby",
                        contextWords =
                            setOf(
                                "scale",
                                "kit",
                                "miniature",
                                "paint",
                                "hobby",
                                "plastic",
                                "glue",
                                "detail",
                                "build",
                                "aircraft",
                                "tank",
                                "ship",
                                "car",
                                "diorama",
                                "warhammer",
                            ),
                    ),
                ),
            "stream" to
                listOf(
                    DomainContext(
                        domain = "live",
                        contextWords =
                            setOf(
                                "live",
                                "twitch",
                                "chat",
                                "viewer",
                                "sub",
                                "donation",
                                "game",
                                "play",
                                "broadcast",
                                "obs",
                                "overlay",
                                "alert",
                                "raid",
                                "clip",
                            ),
                    ),
                    DomainContext(
                        domain = "nature",
                        contextWords =
                            setOf(
                                "river",
                                "water",
                                "creek",
                                "brook",
                                "fish",
                                "nature",
                                "mountain",
                                "hike",
                                "forest",
                                "flow",
                                "waterfall",
                                "trout",
                            ),
                    ),
                    DomainContext(
                        domain = "tech",
                        contextWords =
                            setOf(
                                "data",
                                "kafka",
                                "process",
                                "real-time",
                                "event",
                                "pipeline",
                                "api",
                                "java",
                                "reactive",
                                "buffer",
                            ),
                    ),
                ),
            "build" to
                listOf(
                    DomainContext(
                        domain = "gaming",
                        contextWords =
                            setOf(
                                "game",
                                "character",
                                "class",
                                "loadout",
                                "weapon",
                                "armor",
                                "skill",
                                "spec",
                                "talent",
                                "meta",
                                "tier",
                                "rank",
                                "guide",
                                "fortnite",
                                "minecraft",
                            ),
                    ),
                    DomainContext(
                        domain = "pc",
                        contextWords =
                            setOf(
                                "pc",
                                "computer",
                                "gpu",
                                "cpu",
                                "ram",
                                "ssd",
                                "motherboard",
                                "case",
                                "cooling",
                                "rgb",
                                "setup",
                                "benchmark",
                                "gaming",
                                "workstation",
                                "budget",
                            ),
                    ),
                    DomainContext(
                        domain = "construction",
                        contextWords =
                            setOf(
                                "house",
                                "cabin",
                                "shed",
                                "deck",
                                "fence",
                                "wood",
                                "concrete",
                                "brick",
                                "foundation",
                                "roof",
                                "wall",
                                "frame",
                                "diy",
                                "workshop",
                            ),
                    ),
                    DomainContext(
                        domain = "car",
                        contextWords =
                            setOf(
                                "car",
                                "truck",
                                "engine",
                                "swap",
                                "turbo",
                                "exhaust",
                                "suspension",
                                "wheel",
                                "tire",
                                "drift",
                                "race",
                                "jdm",
                                "sema",
                                "custom",
                                "restore",
                                "project",
                                "garage",
                                "wrench",
                            ),
                    ),
                ),
            "scale" to
                listOf(
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "major",
                                "minor",
                                "note",
                                "practice",
                                "piano",
                                "guitar",
                                "music",
                                "theory",
                                "mode",
                                "pentatonic",
                                "chord",
                                "interval",
                                "key",
                            ),
                    ),
                    DomainContext(
                        domain = "hobby",
                        contextWords =
                            setOf(
                                "model",
                                "miniature",
                                "kit",
                                "rc",
                                "diorama",
                                "detail",
                                "paint",
                                "replica",
                            ),
                    ),
                    DomainContext(
                        domain = "business",
                        contextWords =
                            setOf(
                                "startup",
                                "growth",
                                "business",
                                "company",
                                "revenue",
                                "customer",
                                "market",
                                "enterprise",
                            ),
                    ),
                ),
            "code" to
                listOf(
                    DomainContext(
                        domain = "programming",
                        contextWords =
                            setOf(
                                "python",
                                "javascript",
                                "java",
                                "program",
                                "debug",
                                "react",
                                "angular",
                                "vue",
                                "node",
                                "django",
                                "software",
                                "develop",
                                "engineer",
                                "function",
                                "variable",
                                "kotlin",
                                "swift",
                                "c++",
                                "ruby",
                                "php",
                                "arduino",
                                "raspberry",
                                "linux",
                                "terminal",
                                "command",
                                "line",
                                "script",
                                "compile",
                                "execute",
                                "algorithm",
                                "data structure",
                                "oop",
                                "functional",
                                "programming",
                                "coding",
                                "flutter",
                                "dart",
                                "spring",
                                "boot",
                                "microservice",
                                "api",
                                "rest",
                                "graphql",
                                "machine learning",
                                "artificial intelligence",
                                "deep learning",
                                "neural network",
                                "tutorial",
                                "learn",
                                "beginner",
                                "project",
                                "build",
                            ),
                    ),
                    DomainContext(
                        domain = "promo",
                        contextWords =
                            setOf(
                                "promo",
                                "discount",
                                "coupon",
                                "sale",
                                "deal",
                                "off",
                                "percent",
                                "shop",
                                "store",
                                "buy",
                                "free",
                                "trial",
                                "subscription",
                                "membership",
                            ),
                    ),
                    DomainContext(
                        domain = "entertainment",
                        contextWords =
                            setOf(
                                "movie",
                                "film",
                                "series",
                                "show",
                                "thriller",
                                "stream",
                                "watch",
                                "netflix",
                                "amazon",
                                "episode",
                                "season",
                                "cast",
                                "actor",
                                "scene",
                            ),
                    ),
                ),
            "design" to
                listOf(
                    DomainContext(
                        domain = "graphic",
                        contextWords =
                            setOf(
                                "graphic",
                                "logo",
                                "brand",
                                "illustrator",
                                "photoshop",
                                "vector",
                                "font",
                                "typography",
                                "poster",
                                "ui",
                                "creative",
                                "adobe",
                                "canva",
                                "layout",
                                "mockup",
                            ),
                    ),
                    DomainContext(
                        domain = "interior",
                        contextWords =
                            setOf(
                                "interior",
                                "room",
                                "home",
                                "house",
                                "furniture",
                                "decor",
                                "living",
                                "bedroom",
                                "kitchen",
                                "space",
                                "minimalist",
                                "cozy",
                                "tour",
                                "makeover",
                            ),
                    ),
                    DomainContext(
                        domain = "fashion",
                        contextWords =
                            setOf(
                                "fashion",
                                "clothing",
                                "dress",
                                "outfit",
                                "style",
                                "runway",
                                "model",
                                "brand",
                                "collection",
                                "wear",
                                "accessory",
                                "trend",
                                "couture",
                            ),
                    ),
                    DomainContext(
                        domain = "game",
                        contextWords =
                            setOf(
                                "game",
                                "level",
                                "character",
                                "map",
                                "world",
                                "ux",
                                "ui",
                                "indie",
                                "unity",
                                "unreal",
                                "minecraft",
                                "city",
                                "park",
                                "architect",
                            ),
                    ),
                ),
            "craft" to
                listOf(
                    DomainContext(
                        domain = "gaming",
                        contextWords =
                            setOf(
                                "minecraft",
                                "game",
                                "recipe",
                                "enchant",
                                "potion",
                                "sword",
                                "armor",
                                "tool",
                                "block",
                                "inventory",
                                "stack",
                                "mod",
                                "server",
                            ),
                    ),
                    DomainContext(
                        domain = "diy",
                        contextWords =
                            setOf(
                                "diy",
                                "handmade",
                                "homemade",
                                "glue",
                                "scissors",
                                "fabric",
                                "needle",
                                "tutorial",
                                "project",
                                "paint",
                                "resin",
                                "clay",
                                "gift",
                                "decor",
                            ),
                    ),
                ),
            "run" to
                listOf(
                    DomainContext(
                        domain = "fitness",
                        contextWords =
                            setOf(
                                "mile",
                                "marathon",
                                "jog",
                                "sprint",
                                "pace",
                                "5k",
                                "10k",
                                "shoe",
                                "outdoor",
                                "trail",
                                "training",
                                "heart",
                                "rate",
                                "endurance",
                            ),
                    ),
                    DomainContext(
                        domain = "gaming",
                        contextWords =
                            setOf(
                                "speedrun",
                                "speed",
                                "any",
                                "glitch",
                                "record",
                                "category",
                                "wr",
                                "attempt",
                                "split",
                                "timer",
                                "pb",
                            ),
                    ),
                    DomainContext(
                        domain = "tech",
                        contextWords =
                            setOf(
                                "command",
                                "script",
                                "execute",
                                "terminal",
                                "process",
                                "npm",
                                "yarn",
                                "pipeline",
                                "ci",
                                "test",
                                "local",
                            ),
                    ),
                ),
            "play" to
                listOf(
                    DomainContext(
                        domain = "gaming",
                        contextWords =
                            setOf(
                                "game",
                                "controller",
                                "console",
                                "multiplayer",
                                "online",
                                "steam",
                                "xbox",
                                "playstation",
                                "pvp",
                                "ranked",
                                "match",
                            ),
                    ),
                    DomainContext(
                        domain = "music",
                        contextWords =
                            setOf(
                                "instrument",
                                "guitar",
                                "piano",
                                "drum",
                                "violin",
                                "chord",
                                "note",
                                "lesson",
                                "song",
                                "music",
                                "cover",
                                "acoustic",
                                "solo",
                            ),
                    ),
                    DomainContext(
                        domain = "sport",
                        contextWords =
                            setOf(
                                "football",
                                "basketball",
                                "soccer",
                                "baseball",
                                "cricket",
                                "field",
                                "team",
                                "sport",
                                "athlete",
                            ),
                    ),
                ),
        )

    /**
     * Resolves the domain of a polysemous word based on surrounding context.
     *
     * Returns the domain-tagged version (e.g. "metal:music") if context is
     * sufficient, or the original word if ambiguous.
     *
     * Uses prefix matching on context words to catch morphological variants
     * (e.g. "fabricat" matches "fabrication", "fabricated", "fabricating").
     */
    private fun disambiguateWord(
        word: String,
        allWords: List<String>,
    ): String {
        val domains = domainDisambiguation[word] ?: return word

        var bestDomain: String? = null
        var bestScore = 0
        var secondBestScore = 0

        for (domain in domains) {
            var score = 0
            for (contextWord in domain.contextWords) {
                if (allWords.any { it.startsWith(contextWord) || contextWord.startsWith(it) }) {
                    score++
                }
            }

            if (score > bestScore) {
                secondBestScore = bestScore
                bestScore = score
                bestDomain = domain.domain
            } else if (score > secondBestScore) {
                secondBestScore = score
            }
        }

        // Only tag if there's clear signal: best > 0 and best clearly wins over second
        return if (bestDomain != null &&
            bestScore >= 1 &&
            (secondBestScore == 0 || bestScore >= secondBestScore * 1.5 + 1)
        ) {
            "$word:$bestDomain"
        } else {
            word
        }
    }

    // ══════════════════════════════════════════════
    // CHANNEL NAME INTELLIGENCE
    // Channel names contain a mix of:
    // - Personal names ("John", "Sarah", "Mr Beast"), dropped by allow-listing topic words
    // - Topic keywords ("Tech", "Gaming", "Cooking")
    // - Branding suffixes ("TV", "HQ", "Official")
    //
    // Only the topic keywords should enter the topic vector.
    // Personal names and branding are noise.
    // ══════════════════════════════════════════════

    private val channelBranding =
        hashSetOf(
            "tv",
            "hq",
            "official",
            "studios",
            "studio",
            "media",
            "network",
            "show",
            "channel",
            "daily",
            "weekly",
            "clips",
            "highlights",
            "productions",
            "entertainment",
            "records",
            "label",
            "publishing",
            "digital",
            "online",
            "plus",
            "extra",
            "prime",
            "live",
            "vlog",
            "vlogs",
            "sensei",
            "guru",
            "master",
            "pro",
            "expert",
            "academy",
            "zone",
            "hub",
            "spot",
            "world",
            "nation",
            "central",
            "insider",
            "minute",
            "bytes",
            "bits",
        )

    private val alwaysTopical =
        hashSetOf(
            // Technology
            "tech",
            "technology",
            "science",
            "code",
            "coding",
            "program",
            "programming",
            "software",
            "hardware",
            "cybersecurity",
            "linux",
            "android",
            "python",
            "javascript",
            "rust",
            "java",
            "swift",
            "flutter",
            "react",
            "devops",
            "database",
            "frontend",
            "backend",
            "hacking",
            "ai",
            // Gaming
            "game",
            "gaming",
            "minecraft",
            "fortnite",
            "roblox",
            "valorant",
            "pokemon",
            "nintendo",
            "playstation",
            "xbox",
            "esports",
            "speedrun",
            "apex",
            "overwatch",
            "destiny",
            "warzone",
            "gta",
            "zelda",
            // Music
            "music",
            "guitar",
            "piano",
            "drum",
            "violin",
            "bass",
            "singing",
            "jazz",
            "classical",
            "electronic",
            "edm",
            "hiphop",
            "rap",
            "metal",
            "rock",
            "punk",
            "indie",
            "kpop",
            "lofi",
            // Creative
            "art",
            "drawing",
            "painting",
            "illustration",
            "sculpture",
            "animation",
            "calligraphy",
            "design",
            "photo",
            "photography",
            "film",
            "cinema",
            "filmmaking",
            // Education & Academic
            "education",
            "math",
            "mathematics",
            "physics",
            "chemistry",
            "biology",
            "engineering",
            "geography",
            "geology",
            "economics",
            "politics",
            "psychology",
            "philosophy",
            "history",
            "literature",
            "language",
            "spanish",
            "japanese",
            "korean",
            "french",
            "german",
            // Entertainment
            "anime",
            "manga",
            "movie",
            "comedy",
            "horror",
            "podcast",
            "documentary",
            "asmr",
            "chess",
            // Sports & Fitness
            "sports",
            "fitness",
            "workout",
            "gym",
            "yoga",
            "running",
            "bodybuilding",
            "calisthenics",
            "nutrition",
            "meditation",
            "football",
            "basketball",
            "soccer",
            "baseball",
            "hockey",
            "golf",
            "tennis",
            "boxing",
            "mma",
            "wrestling",
            "cycling",
            "swimming",
            "climbing",
            "martial",
            // Food & Cooking
            "food",
            "cook",
            "cooking",
            "recipe",
            "baking",
            "vegan",
            "vegetarian",
            "barbecue",
            "cuisine",
            // Lifestyle
            "travel",
            "fashion",
            "beauty",
            "skincare",
            "health",
            "diy",
            "craft",
            "garden",
            "gardening",
            "automotive",
            "car",
            "motorcycle",
            "aviation",
            // Nature & Science
            "nature",
            "wildlife",
            "ocean",
            "marine",
            "aquarium",
            "space",
            "astronomy",
            "robotics",
            "electronics",
            // Pets
            "dog",
            "cat",
            "pet",
            // Craft & Making
            "woodworking",
            "metalworking",
            "3d",
            "printing",
            "sewing",
            "knitting",
            "pottery",
            // Finance
            "crypto",
            "finance",
            "investing",
            "business",
            "marketing",
            "trading",
            "stocks",
            "entrepreneur",
        )

    /**
     * Tokenizes a channel name, extracting ONLY confirmed topic keywords.
     *
     * Channel names are inherently noisy
     * puns ("Fireship"), adjectives ("Epic"), and creative words that are
     * NOT content topic indicators. Instead of trying to filter out all
     * possible noise words (impossible), we only keep words that match
     * known content topics.
     *
     * The channel's identity and viewing patterns are tracked separately
     * via channelScores and channelTopicProfiles. This function only
     * extracts topic signals for the content vector.
     *
     * "Coding Sloth"          → ["code"]        (sloth = mascot, not a topic)
     * "Linus Tech Tips"       → ["tech"]        (linus = not topical, tips = not topical)
     * "Fireship"              → []              (creative brand, not a topic)
     * "Python Engineer"       → ["python"]      (engineer not in list, python is)
     * "Binging with Babish"   → []              (no topic words)
     * "History Matters"       → ["history"]
     * "央視新聞"               → ["新聞"]        (央視 = a brand, 新聞 = news)
     */
    fun tokenizeChannelName(
        channelName: String,
        han: HanFold = defaultHan,
    ): List<String> {
        val rawOriginal = nameWords(channelName, han)

        if (rawOriginal.isEmpty()) return emptyList()

        if (rawOriginal.size == 1) {
            val word = rawOriginal[0].lowercase()
            val lemma = keyOf(word)
            return if (isChannelTopic(lemma) || isChannelTopic(word)) {
                listOf(lemma)
            } else {
                emptyList()
            }
        }

        val topical = mutableListOf<String>()
        rawOriginal.forEach { originalWord ->
            val lower = originalWord.lowercase()
            val lemma = keyOf(lower)

            when {
                isChannelTopic(lemma) -> topical.add(lemma)
                isChannelTopic(lower) -> topical.add(lower)
            }
        }
        return topical.distinct()
    }

    private fun isChannelTopic(word: String): Boolean = word in alwaysTopical || word in NeuroLanguages.channelTopics

    /** Words of a channel name; scripts written without spaces are split by the word breaker. */
    private fun nameWords(
        name: String,
        han: HanFold,
    ): List<String> =
        NeuroText
            .words(fold(name, han), wordBreaker)
            .map { word -> word.trim { !NeuroText.isWordChar(it) } }
            .filter { it.length > 1 }

    /**
     * Builds a flat list of all meaningful words from the video's title,
     * channel name, and description. Used as context for domain disambiguation.
     *
     * Does NOT lemmatize — we want raw word stems for prefix matching
     * against domain context words.
     */
    private fun buildContextWordList(
        video: Video,
        han: HanFold,
    ): List<String> {
        val words = mutableListOf<String>()

        NeuroText
            .fold(video.title, han)
            .split(WHITESPACE_REGEX)
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 1 }
            .forEach { words.add(it) }

        words.addAll(nameWords(video.channelName, han))

        if (!video.description.isNullOrBlank()) {
            video.description
                .lines()
                .take(3)
                .joinToString(" ")
                .let { NeuroText.fold(it, han) }
                .split(WHITESPACE_REGEX)
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .forEach { words.add(it) }
        }

        // Tags provide critical disambiguation context
        val tags = topicTags(video)
        if (tags.isNotEmpty()) {
            tags.take(TAG_MAX_INGEST).forEach { tag ->
                NeuroText
                    .fold(tag, han)
                    .split(WHITESPACE_REGEX)
                    .map { it.trim { c -> !c.isLetterOrDigit() } }
                    .filter { it.length > 1 }
                    .forEach { words.add(it) }
            }
        }

        return words
    }
}
