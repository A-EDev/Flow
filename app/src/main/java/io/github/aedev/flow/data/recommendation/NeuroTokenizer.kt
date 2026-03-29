/*
 * Copyright (C) 2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
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
internal class NeuroTokenizer {

    companion object {
        val WHITESPACE_REGEX = Regex("\\s+")

        // ── Feature Extraction Constants ──
        const val IDF_COLD_START_DOCS = 30
        const val IDF_MIN_WEIGHT = 0.15
        const val IDF_MAX_WEIGHT = 1.0
        const val CHANNEL_KEYWORD_WEIGHT = 1.0
        const val TITLE_KEYWORD_WEIGHT = 0.5
        const val BIGRAM_WEIGHT = 0.75
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
    }

    // ── Lemma Map ──

    private val LEMMA_MAP = mapOf(
        // Gaming
        "gaming" to "game", "games" to "game", "gamer" to "game",
        "gamers" to "game", "gameplay" to "game", "gamed" to "game",
        // Coding / Programming
        "coding" to "code", "coder" to "code", "coders" to "code",
        "codes" to "code", "coded" to "code",
        "programming" to "program", "programmer" to "program",
        "programmers" to "program", "programs" to "program",
        "programmed" to "program",
        // Cooking
        "cooking" to "cook", "cooked" to "cook", "cooks" to "cook",
        "cooker" to "cook",
        // Music
        "songs" to "song", "singing" to "sing", "singer" to "sing",
        "singers" to "sing", "musics" to "music", "musical" to "music",
        "musician" to "music", "musicians" to "music",
        // Technology
        "technologies" to "technology", "technological" to "technology",
        "computers" to "computer", "computing" to "computer",
        "computed" to "computer",
        // Art
        "drawing" to "draw", "drawings" to "draw", "drawn" to "draw",
        "painting" to "paint", "paintings" to "paint",
        "painted" to "paint", "painter" to "paint",
        "animating" to "animation", "animated" to "animation",
        "animations" to "animation", "animator" to "animation",
        // Fitness
        "workouts" to "workout", "exercising" to "exercise",
        "exercises" to "exercise", "exercised" to "exercise",
        "learning" to "learn", "learned" to "learn",
        "learner" to "learn", "learners" to "learn",
        "teaching" to "teach", "teacher" to "teach",
        "teachers" to "teach", "taught" to "teach",
        "studying" to "study", "studies" to "study",
        "studied" to "study", "tutorials" to "tutorial",
        "making" to "make", "maker" to "make", "makers" to "make",
        "makes" to "make", "made" to "make",
        "reviewing" to "review", "reviewed" to "review",
        "reviews" to "review", "reviewer" to "review",
        "testing" to "test", "tested" to "test", "tests" to "test",
        "tester" to "test",
        "editing" to "edit", "edited" to "edit", "edits" to "edit",
        "editor" to "edit",
        "traveling" to "travel", "travelled" to "travel",
        "travels" to "travel", "traveler" to "travel",
        "vlogging" to "vlog", "vlogs" to "vlog", "vlogger" to "vlog",
        "vloggers" to "vlog",
        "reactions" to "reaction",
        "compilations" to "compilation",
        // Science
        "experiments" to "experiment", "experimenting" to "experiment",
        "experimental" to "experiment",
        "sciences" to "science", "scientific" to "science",
        "scientist" to "science",
        "engineering" to "engineer", "engineered" to "engineer",
        "engineers" to "engineer",
        "inventions" to "invention", "inventing" to "invention",
        "invented" to "invention",
        // Nature
        "animals" to "animal",
        // Lifestyle
        "recipes" to "recipe", "baking" to "bake", "baked" to "bake",
        "baker" to "bake",
        "gardening" to "garden", "gardens" to "garden",
        "photographing" to "photography",
        "photographs" to "photography",
        "photographer" to "photography",
        // Common verbs
        "explained" to "explain", "explains" to "explain",
        "explaining" to "explain",
        "created" to "create", "creates" to "create",
        "creating" to "create", "creator" to "create",
        "creators" to "create",
        "discovered" to "discover", "discovers" to "discover",
        "discovering" to "discover",
        "exploring" to "explore", "explored" to "explore",
        "explores" to "explore",
        "comparing" to "compare", "compared" to "compare",
        "compares" to "compare", "comparison" to "compare",
        "comparisons" to "compare",
        // Plurals
        "videos" to "video", "channels" to "channel",
        "episodes" to "episode",
        "movies" to "movie", "documentaries" to "documentary",
        "podcasts" to "podcast", "interviews" to "interview",
        "challenges" to "challenge",
        "montages" to "montage"
    )

    // ── Stop Words ──

    private val STOP_WORDS = hashSetOf(
        // Grammatical
        "the", "and", "for", "that", "this", "with", "you", "how",
        "what", "when", "mom", "types", "your", "which", "can",
        "make", "seen", "most", "into", "best", "from", "just",
        "about", "more", "some", "will", "one", "all", "would",
        "there", "their", "out", "not", "but", "have", "has",
        "been", "being", "was", "were", "are",
        // YouTube meta
        "video", "official", "channel", "review", "reaction",
        "full", "episode", "part", "new", "latest", "update",
        "hdr", "uhd", "fps", "live", "stream",
        "watch", "subscribe", "like", "comment",
        "share", "click", "link", "description", "below", "check",
        "dont", "miss", "must", "now",
        // Resolution tags
        "1080p", "720p", "480p", "360p", "240p", "144p",
        // Lemma-consistent
        "compilation", "montage",
        "reupload", "reup", "reuploaded",
        // Format / meta descriptors
        "guide", "tutorial", "tips", "tricks", "hack", "hacks",
        "lesson", "course", "class", "session", "step", "steps",
        "ways", "things", "stuff", "beginner", "beginners",
        "advanced", "intermediate", "basic", "basics",
        "introduction", "intro", "everything", "anything",
        "nothing", "something", "complete", "ultimate",
        "definitive", "easy", "simple", "hard", "difficult",
        "free", "paid", "cheap", "expensive",
        "first", "last", "next", "previous",
        // AI / prompt meta-words
        "prompt", "prompts", "prompting",
        // Year tokens — always noise in topic vectors
        "2020", "2021", "2022", "2023", "2024",
        "2025", "2026", "2027", "2028", "2029", "2030",
        // YouTube engagement bait
        "amazing", "insane", "crazy", "incredible",
        "unbelievable", "shocking", "exposed", "revealed",
        "secret", "secrets", "honest", "truth", "proof", "finally",
        // High-frequency verbs with no topic meaning
        "use", "used", "using", "need", "want", "know",
        "help", "find", "look", "looking", "get", "got", "getting",
        "give", "gave", "keep", "kept", "tell", "told", "say", "said",
        "start", "stop", "try", "take", "took",
        // Filler adverbs / discourse fillers
        "really", "actually", "literally", "basically",
        "ever", "never", "always", "every",
        "still", "also", "too", "very", "only",
        "then", "than", "well", "even"
    )

    // ── Polysemy Data ──

    val POLYSEMOUS_WORDS = hashSetOf(
        "train", "model", "build", "plant", "stream",
        "react", "design", "film", "run", "play",
        "cook", "fire", "spring", "match", "cell",
        "power", "drive", "board", "frame", "scale",
        "lead", "light", "block", "drop", "track",
        "craft", "host", "mine", "pitch", "wave",
        "bass", "bow", "clip", "dart", "fan",
        "gear", "jam", "kit", "lab", "log",
        "net", "pad", "port", "rig", "set",
        "tap", "tip", "web",
        // Music genres / material / nature terms with multiple meanings
        "metal", "rock", "bar"
    )

    val COMPOUND_TERMS = hashSetOf(
        // AI/ML
        "train model", "train ai", "machine learn",
        "deep learn", "neural network", "train data",
        "fine tune", "train network", "train system",
        "build model", "build ai", "build network",
        "model train", "model ai", "model build",
        // Programming
        "react native", "react component", "react hook",
        "react app", "react tutorial", "react project",
        "build system", "build tool", "build project",
        "run test", "run code", "run server", "run script",
        "design pattern", "design system", "web design",
        "game design", "sound design",
        // Specific disambiguations
        "power plant", "plant base", "plant based",
        "fire base", "fire wall", "fire fight",
        "spring boot", "spring framework", "spring board",
        "stream deck", "stream lab", "stream setup",
        "film make", "film edit", "film score",
        "scale model",
        "block chain", "block list",
        "host server", "host name",
        "web development", "web app", "web site",
        "net work", "net worth",
        // Music disambiguations
        "bass guitar", "bass boost", "bass drop",
        "drum track", "sound track", "race track",
        // Gaming
        "speed run", "play through",
        "build guide", "build order",
        "craft recipe", "mine craft"
    )

    val SPONSOR_LINE_PATTERNS = listOf(
        "use code ", "% off", "free trial", "link in",
        "sponsored by", "brought to you", "check out",
        "sign up", "discount", "promo code", "coupon",
        "affiliate", "partner", "merch", "merchandise",
        "patreon", "ko-fi", "buymeacoffee", "buy me a coffee",
        "subscribe", "follow me", "social media",
        "instagram", "twitter", "tiktok", "discord",
        "join the", "become a member", "membership",
        "business inquiries", "business email", "contact:",
        "►", "→", "⬇", "⇩", "👇",
        "timestamps:", "chapters:"
    )

    // ── Pacing Keywords ──

    val HIGH_PACING_WORDS = setOf(
        "compilation", "tiktok", "tiktoks", "highlights",
        "speedrun", "trailer", "shorts", "montage", "moments",
        "best of", "try not to", "memes", "funny", "fails",
        "rapid", "fast", "quick", "minute", "seconds",
        "top 10", "top 5", "ranked", "tier list", "versus"
    )

    val LOW_PACING_WORDS = setOf(
        "podcast", "essay", "ambient", "explained", "study",
        "meditation", "sleep", "asmr", "relaxing", "calm",
        "deep dive", "analysis", "lecture", "course",
        "documentary", "interview", "conversation",
        "discussion", "breakdown", "walkthrough"
    )

    // ── Music Keywords ──

    val MUSIC_KEYWORDS = setOf(
        "music", "song", "lyrics", "remix", "lofi", "lo-fi",
        "playlist", "official audio", "official video",
        "music video", "feat", "ft.", "acoustic", "cover",
        "karaoke", "instrumental", "beat", "rap", "hip hop",
        "pop", "rock", "jazz", "classical", "edm", "mix"
    )

    // ── Functions ──

    fun normalizeLemma(word: String): String =
        LEMMA_MAP[word.lowercase()] ?: word.lowercase()

    fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(WHITESPACE_REGEX)
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { it.length > 2 }
            .map { normalizeLemma(it) }
            .filter { !STOP_WORDS.contains(it) }
    }

    fun tokenizeForSimilarity(text: String): Set<String> {
        return tokenize(text).toSet()
    }

    fun calculateIdfWeight(
        word: String,
        baseWeight: Double,
        idfSnapshot: IdfSnapshot
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
        channelTopicProfile: Map<String, Double>? = null
    ): ContentVector {
        val topics = mutableMapOf<String, Double>()

        val titleWords = tokenize(video.title)

        // Use smart channel tokenization to filter names and branding
        val chWords = tokenizeChannelName(video.channelName)

        // Channel keywords — filtered for actual topic words only
        chWords.forEach {
            topics[it] = calculateIdfWeight(it, CHANNEL_KEYWORD_WEIGHT, idfSnapshot)
        }

        // Collect all words for domain disambiguation context BEFORE processing tokens
        val fullContext = buildContextWordList(video)

        // ── V9.3 Fix 1: Extract bigrams FIRST, track which unigram indices are "claimed" ──
        val claimedByBigram = mutableSetOf<Int>()

        if (titleWords.size >= 2) {
            for (i in 0 until titleWords.size - 1) {
                val bigram = "${titleWords[i]} ${titleWords[i + 1]}"

                val isMeaningful = COMPOUND_TERMS.contains(bigram) ||
                    POLYSEMOUS_WORDS.contains(titleWords[i]) ||
                    POLYSEMOUS_WORDS.contains(titleWords[i + 1]) ||
                    // Also treat domain-disambiguable words as meaningful for bigram protection
                    titleWords[i] in DOMAIN_DISAMBIGUATION ||
                    titleWords[i + 1] in DOMAIN_DISAMBIGUATION

                if (isMeaningful) {
                    topics[bigram] = calculateIdfWeight(
                        bigram, BIGRAM_PRIORITY_WEIGHT, idfSnapshot
                    )
                    claimedByBigram.add(i)
                    claimedByBigram.add(i + 1)
                } else {
                    topics[bigram] = calculateIdfWeight(
                        bigram, BIGRAM_WEIGHT, idfSnapshot
                    )
                }
            }
        }

        // ── Title unigrams: SKIP words claimed by meaningful bigrams ──
        titleWords.forEachIndexed { index, word ->
            if (index !in claimedByBigram) {
                // Domain-disambiguate polysemous words using full title+description context
                val resolvedWord = if (word in DOMAIN_DISAMBIGUATION) {
                    disambiguateWord(word, fullContext)
                } else {
                    word
                }
                topics[resolvedWord] = (topics.getOrDefault(resolvedWord, 0.0) +
                    calculateIdfWeight(resolvedWord, TITLE_KEYWORD_WEIGHT, idfSnapshot))
            }
        }

        // ── V9.3 Fix 5: Smart description extraction ──
        val descriptionTopics = extractDescriptionKeywords(
            video.description, idfSnapshot
        )
        descriptionTopics.forEach { (word, weight) ->
            // Also disambiguate description words using the same full context
            val resolved = if (word in DOMAIN_DISAMBIGUATION) {
                disambiguateWord(word, fullContext)
            } else {
                word
            }
            topics[resolved] = (topics.getOrDefault(resolved, 0.0) + weight)
        }

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
        val normalized = if (topics.isNotEmpty()) {
            var magnitude = 0.0
            topics.values.forEach { magnitude += it * it }
            magnitude = sqrt(magnitude)
            if (magnitude > 0) topics.mapValues { (_, v) -> v / magnitude }
            else topics
        } else topics

        val durationSec = when {
            video.duration > 0 -> video.duration
            video.isLive -> 3600
            else -> 300
        }
        val durationScore = (ln(1.0 + durationSec) /
            ln(1.0 + 7200.0)).coerceIn(0.0, 1.0)

        val pacingScore = run {
            val titleLower = video.title.lowercase()

            val highCount = HIGH_PACING_WORDS.count {
                titleLower.contains(it)
            }
            val lowCount = LOW_PACING_WORDS.count {
                titleLower.contains(it)
            }

            when {
                highCount > lowCount -> (0.6 + highCount * 0.1)
                    .coerceAtMost(0.95)
                lowCount > highCount -> (0.4 - lowCount * 0.1)
                    .coerceAtLeast(0.05)
                video.isShort -> 0.85
                else -> 0.5
            }
        }

        val description = video.description
        val hasChapters = if (!description.isNullOrBlank()) {
            TIMESTAMP_PATTERN.findAll(description).count() >= CHAPTER_TIMESTAMP_MIN
        } else false

        val rawTitleWords = video.title.split(WHITESPACE_REGEX)
            .filter { it.length > 1 }

        val complexityScore = run {
            val titleLenFactor = (video.title.length /
                COMPLEXITY_TITLE_LEN_MAX).coerceIn(0.0, COMPLEXITY_TITLE_LEN_WEIGHT)
            val avgWordLen = if (rawTitleWords.isNotEmpty()) {
                rawTitleWords.map { it.length }.average()
            } else 4.0
            val wordLenFactor = (avgWordLen /
                COMPLEXITY_WORD_LEN_DIVISOR).coerceIn(0.0, COMPLEXITY_WORD_LEN_WEIGHT)
            val chapterBonus = if (hasChapters) COMPLEXITY_CHAPTER_BONUS else 0.0
            (titleLenFactor + wordLenFactor + chapterBonus)
                .coerceIn(0.0, 1.0)
        }

        return ContentVector(
            topics = normalized,
            duration = durationScore,
            pacing = pacingScore,
            complexity = complexityScore,
            isLive = if (video.isLive) 1.0 else 0.0
        )
    }

    fun extractDescriptionKeywords(
        description: String?,
        idfSnapshot: IdfSnapshot
    ): Map<String, Double> {
        if (description.isNullOrBlank() || description.length < DESCRIPTION_MIN_LENGTH) {
            return emptyMap()
        }

        val contentLines = description.lines()
            .filter { line ->
                val lower = line.lowercase().trim()
                line.trim().length > DESCRIPTION_LINE_MIN_LENGTH &&
                !SPONSOR_LINE_PATTERNS.any { pattern -> lower.contains(pattern) } &&
                !lower.contains("http") &&
                !lower.trimStart().startsWith("#") &&
                !(line.trim().length > 5 && line.trim() == line.trim().uppercase())
            }
            .take(DESCRIPTION_TAKE_LINES)
            .joinToString(" ")

        if (contentLines.isBlank()) return emptyMap()

        val words = tokenize(contentLines).take(DESCRIPTION_TAKE_WORDS)
        val result = mutableMapOf<String, Double>()
        words.forEach { word ->
            result[word] = (result.getOrDefault(word, 0.0) +
                calculateIdfWeight(word, DESCRIPTION_WORD_WEIGHT, idfSnapshot))
        }
        return result
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
        val contextWords: Set<String>
    )

    private val DOMAIN_DISAMBIGUATION: Map<String, List<DomainContext>> = mapOf(
        "metal" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "song", "band", "album", "music", "concert", "guitar",
                    "drum", "vocal", "sing", "lyric", "playlist", "tour",
                    "riff", "solo", "anthem", "heavy", "death", "black",
                    "thrash", "power", "symphonic", "progressive", "doom",
                    "genre", "metalcore", "deathcore", "djent", "cover",
                    "react", "reaction", "review", "rank", "tier"
                )
            ),
            DomainContext(
                domain = "craft",
                contextWords = setOf(
                    "weld", "forge", "fabricat", "sheet", "steel", "iron",
                    "aluminum", "copper", "brass", "tin", "alloy",
                    "workshop", "tool", "cut", "bend", "grind", "polish",
                    "cast", "mold", "anvil", "hammer", "lathe", "cnc",
                    "machin", "work", "shop", "build", "construct"
                )
            )
        ),
        "rock" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "song", "band", "album", "music", "concert", "guitar",
                    "drum", "vocal", "sing", "lyric", "playlist", "tour",
                    "classic", "punk", "indie", "alternative", "grunge",
                    "blues", "roll", "genre", "cover", "anthem",
                    "react", "reaction", "review", "rank", "tier"
                )
            ),
            DomainContext(
                domain = "nature",
                contextWords = setOf(
                    "geology", "mineral", "stone", "fossil", "gem",
                    "crystal", "boulder", "cliff", "mountain", "cave",
                    "collect", "specimen", "earth", "volcanic", "sediment",
                    "igneous", "metamorphic", "quarry", "dig", "formation"
                )
            ),
            DomainContext(
                domain = "climbing",
                contextWords = setOf(
                    "climb", "boulder", "wall", "route", "belay",
                    "rope", "harness", "crag", "send", "flash",
                    "dyno", "crimp", "sloper"
                )
            )
        ),
        "bass" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "guitar", "song", "music", "band", "play", "riff",
                    "slap", "fretless", "amp", "pedal", "tone",
                    "groove", "funk", "jazz", "solo", "lesson",
                    "boost", "drop", "sub", "speaker", "audio",
                    "headphone", "equalizer", "frequency", "woofer"
                )
            ),
            DomainContext(
                domain = "fishing",
                contextWords = setOf(
                    "fish", "fishing", "lure", "bait", "catch",
                    "lake", "river", "pond", "boat", "rod",
                    "reel", "tackle", "tournament", "largemouth",
                    "smallmouth", "striped"
                )
            )
        ),
        "spring" to listOf(
            DomainContext(
                domain = "tech",
                contextWords = setOf(
                    "boot", "java", "framework", "api", "microservice",
                    "backend", "server", "code", "program", "develop",
                    "application", "deploy", "config", "bean", "inject"
                )
            ),
            DomainContext(
                domain = "season",
                contextWords = setOf(
                    "summer", "winter", "fall", "autumn", "season",
                    "flower", "garden", "bloom", "clean", "outfit",
                    "fashion", "weather", "april", "march", "may"
                )
            )
        ),
        "cell" to listOf(
            DomainContext(
                domain = "biology",
                contextWords = setOf(
                    "biology", "science", "membrane", "nucleus",
                    "mitosis", "dna", "rna", "protein", "organism",
                    "tissue", "bacteria", "virus", "microscope"
                )
            ),
            DomainContext(
                domain = "tech",
                contextWords = setOf(
                    "phone", "mobile", "battery", "charge", "signal",
                    "carrier", "data", "plan", "network", "sim"
                )
            ),
            DomainContext(
                domain = "energy",
                contextWords = setOf(
                    "solar", "fuel", "battery", "power", "energy",
                    "electric", "hydrogen", "lithium", "volt"
                )
            )
        ),
        "plant" to listOf(
            DomainContext(
                domain = "botany",
                contextWords = setOf(
                    "grow", "garden", "flower", "seed", "soil",
                    "water", "pot", "leaf", "root", "tree",
                    "herb", "indoor", "outdoor", "succulent", "cactus",
                    "propagat", "prune", "fertiliz", "bloom"
                )
            ),
            DomainContext(
                domain = "industrial",
                contextWords = setOf(
                    "power", "factory", "nuclear", "chemical",
                    "manufacturing", "industrial", "facility",
                    "process", "production", "refinery"
                )
            )
        ),
        "pitch" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "sing", "vocal", "note", "tune", "perfect",
                    "music", "tone", "frequency", "ear", "train"
                )
            ),
            DomainContext(
                domain = "business",
                contextWords = setOf(
                    "startup", "investor", "shark", "tank", "business",
                    "company", "funding", "venture", "present",
                    "elevator", "idea", "million"
                )
            ),
            DomainContext(
                domain = "sport",
                contextWords = setOf(
                    "baseball", "cricket", "throw", "ball", "strike",
                    "mound", "pitcher", "fastball", "curve", "slider"
                )
            )
        ),
        "jam" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "guitar", "band", "music", "session", "play",
                    "improvise", "solo", "groove", "funk", "blues",
                    "live", "concert"
                )
            ),
            DomainContext(
                domain = "food",
                contextWords = setOf(
                    "recipe", "fruit", "strawberry", "preserve",
                    "spread", "toast", "jar", "cook", "homemade",
                    "sugar", "berry", "grape"
                )
            )
        ),
        "bar" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "beat", "measure", "rhythm", "rap", "flow",
                    "lyric", "verse", "hook", "produce", "music"
                )
            ),
            DomainContext(
                domain = "fitness",
                contextWords = setOf(
                    "pull", "deadlift", "squat", "bench", "weight",
                    "gym", "workout", "exercise", "lift", "olympic"
                )
            )
        ),
        "wave" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "synth", "new", "retro", "synthwave", "vaporwave",
                    "music", "genre", "aesthetic", "80s", "electronic"
                )
            ),
            DomainContext(
                domain = "science",
                contextWords = setOf(
                    "ocean", "sound", "light", "electromagnetic",
                    "frequency", "physics", "energy", "radio",
                    "seismic", "tsunami", "surf"
                )
            ),
            DomainContext(
                domain = "hair",
                contextWords = setOf(
                    "hair", "style", "curl", "360", "brush",
                    "barber", "durag", "pattern", "pomade"
                )
            )
        ),
        "track" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "song", "album", "music", "beat", "produce",
                    "mix", "master", "record", "artist", "release",
                    "single", "feature", "lyric", "audio"
                )
            ),
            DomainContext(
                domain = "sport",
                contextWords = setOf(
                    "race", "run", "lap", "sprint", "field",
                    "athlete", "relay", "hurdle", "meter", "olympic",
                    "nascar", "formula", "circuit", "drift"
                )
            )
        ),
        "model" to listOf(
            DomainContext(
                domain = "ai",
                contextWords = setOf(
                    "train", "machine", "learn", "neural", "network",
                    "data", "predict", "gpt", "llm", "parameter",
                    "fine", "tune", "inference", "deploy", "weight"
                )
            ),
            DomainContext(
                domain = "fashion",
                contextWords = setOf(
                    "fashion", "runway", "pose", "photo", "shoot",
                    "agency", "walk", "beauty", "vogue", "style"
                )
            ),
            DomainContext(
                domain = "hobby",
                contextWords = setOf(
                    "scale", "kit", "miniature", "paint", "hobby",
                    "plastic", "glue", "detail", "build", "aircraft",
                    "tank", "ship", "car", "diorama", "warhammer"
                )
            )
        ),
        "stream" to listOf(
            DomainContext(
                domain = "live",
                contextWords = setOf(
                    "live", "twitch", "chat", "viewer", "sub",
                    "donation", "game", "play", "broadcast", "obs",
                    "overlay", "alert", "raid", "clip"
                )
            ),
            DomainContext(
                domain = "nature",
                contextWords = setOf(
                    "river", "water", "creek", "brook", "fish",
                    "nature", "mountain", "hike", "forest", "flow",
                    "waterfall", "trout"
                )
            ),
            DomainContext(
                domain = "tech",
                contextWords = setOf(
                    "data", "kafka", "process", "real-time", "event",
                    "pipeline", "api", "java", "reactive", "buffer"
                )
            )
        ),
        "build" to listOf(
            DomainContext(
                domain = "gaming",
                contextWords = setOf(
                    "game", "character", "class", "loadout", "weapon",
                    "armor", "skill", "spec", "talent", "meta",
                    "tier", "rank", "guide", "fortnite", "minecraft"
                )
            ),
            DomainContext(
                domain = "pc",
                contextWords = setOf(
                    "pc", "computer", "gpu", "cpu", "ram", "ssd",
                    "motherboard", "case", "cooling", "rgb", "setup",
                    "benchmark", "gaming", "workstation", "budget"
                )
            ),
            DomainContext(
                domain = "construction",
                contextWords = setOf(
                    "house", "cabin", "shed", "deck", "fence",
                    "wood", "concrete", "brick", "foundation",
                    "roof", "wall", "frame", "diy", "workshop"
                )
            ),
            DomainContext(
                domain = "car",
                contextWords = setOf(
                    "car", "truck", "engine", "swap", "turbo",
                    "exhaust", "suspension", "wheel", "tire",
                    "drift", "race", "jdm", "sema", "custom",
                    "restore", "project", "garage", "wrench"
                )
            )
        ),
        "scale" to listOf(
            DomainContext(
                domain = "music",
                contextWords = setOf(
                    "major", "minor", "note", "practice", "piano",
                    "guitar", "music", "theory", "mode", "pentatonic",
                    "chord", "interval", "key"
                )
            ),
            DomainContext(
                domain = "hobby",
                contextWords = setOf(
                    "model", "miniature", "kit", "rc", "diorama",
                    "detail", "paint", "replica"
                )
            ),
            DomainContext(
                domain = "business",
                contextWords = setOf(
                    "startup", "growth", "business", "company",
                    "revenue", "customer", "market", "enterprise"
                )
            )
        )
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
    private fun disambiguateWord(word: String, allWords: List<String>): String {
        val domains = DOMAIN_DISAMBIGUATION[word] ?: return word

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

        // Only tag if we have clear signal: best > 0 and best clearly wins over second
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
    // - Personal names ("John", "Sarah", "Mr Beast")
    // - Topic keywords ("Tech", "Gaming", "Cooking")
    // - Branding suffixes ("TV", "HQ", "Official")
    //
    // Only the topic keywords should enter the topic vector.
    // Personal names and branding are noise.
    // ══════════════════════════════════════════════

    private val COMMON_FIRST_NAMES = hashSetOf(
        // English male
        "adam", "alan", "alex", "alexander", "andrew", "anthony",
        "austin", "ben", "benjamin", "brandon", "brian", "bruce",
        "caleb", "cameron", "carl", "charles", "charlie", "chris",
        "christian", "colin", "connor", "corey", "craig", "dan",
        "daniel", "dave", "david", "dean", "derek", "devon",
        "dominic", "don", "donald", "drew", "dustin", "dylan",
        "ed", "edward", "eli", "elijah", "eric", "erik", "ethan",
        "evan", "felix", "frank", "fred", "gary", "george",
        "grant", "greg", "gregory", "harry", "henry", "howard",
        "hunter", "ian", "isaac", "jack", "jackson", "jacob",
        "jake", "james", "jamie", "jared", "jason", "jeff",
        "jeffrey", "jeremy", "jerry", "jesse", "jim", "jimmy",
        "joe", "joel", "john", "johnny", "jon", "jonathan",
        "jordan", "jose", "joseph", "josh", "joshua", "juan",
        "julian", "justin", "keith", "ken", "kevin", "kyle",
        "lance", "larry", "leo", "liam", "logan", "louis",
        "lucas", "luke", "marcus", "mark", "martin", "mason",
        "matt", "matthew", "max", "michael", "mike", "miles",
        "nathan", "nicholas", "nick", "noah", "nolan", "oliver",
        "oscar", "owen", "patrick", "paul", "peter", "phil",
        "philip", "preston", "quentin", "ralph", "randy", "ray",
        "raymond", "richard", "rick", "robert", "robin", "roger",
        "ron", "ronald", "ross", "roy", "russell", "ryan",
        "sam", "samuel", "scott", "sean", "seth", "shane",
        "simon", "spencer", "stephen", "steve", "steven",
        "stuart", "ted", "terry", "thomas", "tim", "timothy",
        "todd", "tom", "tommy", "tony", "travis", "trevor",
        "troy", "tyler", "victor", "vincent", "wade", "walter",
        "warren", "wayne", "will", "william", "zach", "zachary",
        // English female
        "alice", "amanda", "amber", "amy", "andrea", "angela",
        "anna", "ashley", "barbara", "beth", "betty", "brenda",
        "brittany", "carmen", "carol", "caroline", "catherine",
        "charlotte", "chelsea", "christina", "christine", "claire",
        "courtney", "crystal", "cynthia", "daisy", "dana", "danielle",
        "deborah", "diana", "donna", "dorothy", "elizabeth", "ellen",
        "emily", "emma", "erica", "eva", "faith", "fiona",
        "florence", "grace", "hailey", "hannah", "heather", "helen",
        "holly", "irene", "isabel", "isabella", "jackie", "jane",
        "janet", "janice", "jasmine", "jennifer", "jenny", "jessica",
        "joan", "joanne", "julia", "julie", "karen", "kate",
        "katherine", "kathleen", "katie", "kayla", "kelly", "kim",
        "kimberly", "kristen", "kristin", "laura", "lauren", "leah",
        "lillian", "lily", "linda", "lisa", "liz", "lori",
        "lucy", "lynn", "madeline", "madison", "maria", "marie",
        "martha", "mary", "megan", "melissa", "michelle", "miranda",
        "molly", "monica", "nancy", "natalie", "natasha", "nicole",
        "olivia", "paige", "pamela", "patricia", "rachel", "rebecca",
        "renee", "rita", "robin", "rosa", "rose", "ruth",
        "sabrina", "samantha", "sandra", "sara", "sarah", "sharon",
        "sophia", "stephanie", "susan", "tamara", "tanya", "tara",
        "teresa", "tiffany", "tina", "tracy", "valerie", "vanessa",
        "veronica", "victoria", "virginia", "wendy", "whitney"
    )

    private val COMMON_LAST_NAMES = hashSetOf(
        "smith", "johnson", "williams", "brown", "jones", "garcia",
        "miller", "davis", "rodriguez", "martinez", "hernandez",
        "lopez", "gonzalez", "wilson", "anderson", "thomas",
        "taylor", "moore", "jackson", "martin", "lee", "perez",
        "thompson", "white", "harris", "sanchez", "clark", "ramirez",
        "lewis", "robinson", "walker", "young", "allen", "king",
        "wright", "scott", "torres", "nguyen", "hill", "flores",
        "green", "adams", "nelson", "baker", "hall", "rivera",
        "campbell", "mitchell", "carter", "roberts", "turner",
        "phillips", "parker", "evans", "edwards", "collins", "stewart",
        "morris", "murphy", "cook", "rogers", "morgan", "peterson",
        "cooper", "reed", "bailey", "bell", "gomez", "kelly",
        "howard", "ward", "cox", "diaz", "richardson", "wood",
        "watson", "brooks", "bennett", "gray", "james", "reyes",
        "cruz", "hughes", "price", "myers", "long", "foster",
        "sanders", "ross", "morales", "powell", "sullivan", "russell",
        "ortiz", "jenkins", "gutierrez", "perry", "butler", "barnes",
        "fisher", "henderson", "coleman", "simmons", "patterson",
        "jordan", "reynolds", "hamilton", "graham", "kim", "gonzales"
    )

    private val CHANNEL_BRANDING = hashSetOf(
        "tv", "hq", "official", "studios", "studio", "media",
        "network", "show", "channel", "daily", "weekly",
        "clips", "highlights", "productions", "entertainment",
        "records", "label", "publishing", "digital", "online",
        "plus", "extra", "prime", "live", "vlog", "vlogs",
        "sensei", "guru", "master", "pro", "expert", "academy",
        "zone", "hub", "spot", "world", "nation", "central",
        "insider", "minute", "bytes", "bits"
    )

    private val ALWAYS_TOPICAL = hashSetOf(
        "tech", "technology", "science", "music", "game", "gaming",
        "cook", "cooking", "art", "code", "coding", "program",
        "fitness", "health", "beauty", "fashion", "travel",
        "food", "nature", "history", "math", "physics", "chemistry",
        "biology", "engineering", "design", "photo", "photography",
        "film", "cinema", "anime", "manga", "sports", "football",
        "basketball", "soccer", "baseball", "hockey", "golf",
        "tennis", "boxing", "mma", "wrestling", "yoga", "meditation",
        "crypto", "finance", "investing", "business", "marketing",
        "psychology", "philosophy", "education", "garden", "automotive",
        "car", "motorcycle", "aviation", "space", "astronomy",
        "woodworking", "metalworking", "electronics", "robotics",
        "3d", "printing", "sewing", "knitting", "pottery"
    )

    /**
     * Tokenizes a channel name, extracting ONLY topical keywords.
     * Filters out personal names, branding suffixes, and other noise.
     *
     * "John Doe Gaming"          → ["game"]
     * "TechWithTim"              → ["tech"]  (tim filtered as name)
     * "Kurzgesagt – In a Nutshell" → ["kurzgesagt"]  (unique brand = kept)
     * "Linus Tech Tips"          → ["tech"]  (linus = name, tips = stop word)
     */
    fun tokenizeChannelName(channelName: String): List<String> {
        // Keep original casing for capitalization-based name detection
        val rawOriginal = channelName
            .split(WHITESPACE_REGEX)
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { it.length > 1 }

        // Single-word channel names are likely unique brands — keep if substantial
        if (rawOriginal.size == 1) {
            val word = rawOriginal[0].lowercase()
            if (word.length > 3 && word !in CHANNEL_BRANDING && word !in STOP_WORDS) {
                return listOf(normalizeLemma(word))
            }
            return emptyList()
        }

        val topical = mutableListOf<String>()
        rawOriginal.forEach { originalWord ->
            val lower = originalWord.lowercase()
            val lemma = normalizeLemma(lower)

            when {
                lower in ALWAYS_TOPICAL || lemma in ALWAYS_TOPICAL -> topical.add(lemma)
                lower in COMMON_FIRST_NAMES -> { /* skip — personal name */ }
                lower in COMMON_LAST_NAMES -> { /* skip — surname */ }
                lower in CHANNEL_BRANDING -> { /* skip — branding suffix */ }
                lower in STOP_WORDS -> { /* skip — stop word */ }
                lower.length <= 2 -> { /* skip — likely initials */ }
                originalWord[0].isUpperCase() && lower.length <= 5 &&
                    lower !in ALWAYS_TOPICAL -> { /* skip — likely a name */ }
                else -> topical.add(lemma)
            }
        }
        return topical
    }

    /**
     * Builds a flat list of all meaningful words from the video's title,
     * channel name, and description. Used as context for domain disambiguation.
     *
     * Does NOT lemmatize — we want raw word stems for prefix matching
     * against domain context words.
     */
    private fun buildContextWordList(video: Video): List<String> {
        val words = mutableListOf<String>()

        video.title.lowercase()
            .split(WHITESPACE_REGEX)
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 1 }
            .forEach { words.add(it) }

        video.channelName.lowercase()
            .split(WHITESPACE_REGEX)
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 1 }
            .forEach { words.add(it) }

        if (!video.description.isNullOrBlank()) {
            video.description.lines()
                .take(3)
                .joinToString(" ")
                .lowercase()
                .split(WHITESPACE_REGEX)
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .forEach { words.add(it) }
        }

        return words
    }
}
