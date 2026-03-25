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
        "tap", "tip", "web"
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
        val chWords = tokenize(video.channelName)

        // Channel keywords
        chWords.forEach {
            topics[it] = calculateIdfWeight(
                it, CHANNEL_KEYWORD_WEIGHT, idfSnapshot
            )
        }

        // ── V9.3 Fix 1: Extract bigrams FIRST, track which unigram indices are "claimed" ──
        val claimedByBigram = mutableSetOf<Int>()

        if (titleWords.size >= 2) {
            for (i in 0 until titleWords.size - 1) {
                val bigram = "${titleWords[i]} ${titleWords[i + 1]}"

                val isMeaningful = COMPOUND_TERMS.contains(bigram) ||
                    POLYSEMOUS_WORDS.contains(titleWords[i]) ||
                    POLYSEMOUS_WORDS.contains(titleWords[i + 1])

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
                topics[word] = (topics.getOrDefault(word, 0.0) +
                    calculateIdfWeight(word, TITLE_KEYWORD_WEIGHT, idfSnapshot))
            }
        }

        // ── V9.3 Fix 5: Smart description extraction ──
        val descriptionTopics = extractDescriptionKeywords(
            video.description, idfSnapshot
        )
        descriptionTopics.forEach { (word, weight) ->
            topics[word] = (topics.getOrDefault(word, 0.0) + weight)
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
}
