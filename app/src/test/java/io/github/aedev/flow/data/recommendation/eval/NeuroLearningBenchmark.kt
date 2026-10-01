/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.IdfSnapshot
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.recommendation.NeuroContentStore
import io.github.aedev.flow.data.recommendation.NeuroMaintenance
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.NeuroVectorMath
import io.github.aedev.flow.data.recommendation.UserBrain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.math.ln

/**
 * Offline model of how the engine LEARNS, driving the real [FlowNeuroEngine] (learning, rejection,
 * ranking and discovery) with storage mocked out. It answers the #907 reports in numbers:
 *  - retention: months of comedy, tech and cooking, then a Shorts binge and two guitar playalongs.
 *    Does comedy survive, and does guitar take over?
 *  - rejection: a few accidental phonk watches, then thumbs-down or "Not interested". How much
 *    phonk does the feed still serve, and how random does the feed become?
 *  - phrases: guitar playalongs tagged "guitar playalong". Does the phrase become the interest?
 *  - thin time bucket: the current time of day has seen only the two guitar videos.
 *
 * Watched videos carry tags (they were opened in the player); feed candidates are bare titles, as
 * search and related results are.
 */
internal object NeuroLearningBenchmark {
    private const val RANK_RUNS = 6
    private const val TOP_N = 20

    enum class Family { COMEDY, TECH, COOKING, GUITAR, PHONK, OTHER }

    private val comedyNames = listOf("Mulaney", "Gaffigan", "Ali Wong", "Bill Burr", "Birbiglia", "Taylor Tomlinson")
    private val comedyThings = listOf("airports", "dentists", "weddings", "kids", "parents", "hotels", "road trips", "gyms")
    private val phones = listOf("Pixel 10 Pro", "Galaxy S26", "iPhone 17", "OnePlus 14", "Nothing Phone 3")
    private val phoneAngles = listOf("camera test", "battery life after a month", "the honest truth", "worth upgrading", "long term")
    private val dishes = listOf("pasta carbonara", "crispy chicken thighs", "homemade ramen", "sourdough bread", "beef stew")
    private val dishStyles = listOf("weeknight dinner", "one pot", "beginner friendly", "restaurant style")
    private val songs =
        listOf(
            "Sweet Child O Mine",
            "Wonderwall",
            "Hotel California",
            "Smells Like Teen Spirit",
            "Back in Black",
            "Enter Sandman",
            "Come As You Are",
            "Seven Nation Army",
            "Nothing Else Matters",
            "Highway to Hell",
        )
    private val phonkTitles =
        listOf(
            "𝙋𝙃𝙊𝙉𝙆 𝙈𝙄𝙓 aggressive night drive",
            "ＤＲＩＦＴ ＰＨＯＮＫ one hour",
            "ᴘʜᴏɴᴋ gym motivation playlist",
            "ⓟⓗⓞⓝⓚ for late drives",
            "BRAZILIAN PHONK MIX aggressive",
            "phonk for driving at night",
            "aggressive drift phonk playlist",
            "PHONK workout mix that hits hard",
        )
    private val otherTitles =
        listOf(
            "cat knocks cup off the table",
            "satisfying soap cutting",
            "football skills compilation",
            "prank on my brother",
            "street food in Bangkok",
            "dog reacts to magic trick",
            "lego speed build",
            "skateboard trick fails",
            "tiny house tour",
            "celebrity red carpet moments",
            "lottery winner story",
            "crazy parkour run",
        )

    data class World(
        val familyOf: Map<String, Family>,
        val pool: List<Video>,
    )

    private val families = HashMap<String, Family>()

    private fun video(
        id: String,
        family: Family,
        title: String,
        channel: String,
        tags: List<String> = emptyList(),
        isShort: Boolean = false,
        duration: Int = 720,
    ): Video {
        families[id] = family
        return Video(
            id = id,
            title = title,
            channelName = channel,
            channelId = "ch-${channel.lowercase().replace(' ', '-')}",
            thumbnailUrl = "",
            duration = duration,
            viewCount = 400_000,
            uploadDate = "3 days ago",
            tags = tags,
            isShort = isShort,
        )
    }

    fun comedy(
        i: Int,
        tagged: Boolean = true,
    ) = video(
        "comedy-$i${if (tagged) "" else "-c"}",
        Family.COMEDY,
        "Stand-up comedy special: ${comedyNames[i % comedyNames.size]} on ${comedyThings[(i / comedyNames.size + i) % comedyThings.size]}",
        listOf("Netflix Is A Joke", "Comedy Central Stand-Up", "Dry Bar Comedy")[i % 3],
        if (tagged) listOf("stand up comedy", "comedy special", "funny", "comedian") else emptyList(),
    )

    fun tech(
        i: Int,
        tagged: Boolean = true,
    ) = video(
        "tech-$i${if (tagged) "" else "-c"}",
        Family.TECH,
        "${phones[i % phones.size]} review: ${phoneAngles[(i / phones.size + i) % phoneAngles.size]}",
        listOf("MKBHD", "Mrwhosetheboss", "Dave2D")[i % 3],
        if (tagged) listOf("phone review", "smartphone", "tech review", "android") else emptyList(),
    )

    fun cooking(
        i: Int,
        tagged: Boolean = true,
    ) = video(
        "cooking-$i${if (tagged) "" else "-c"}",
        Family.COOKING,
        "Easy ${dishes[i % dishes.size]} recipe, ${dishStyles[(i / dishes.size + i) % dishStyles.size]}",
        listOf("Joshua Weissman", "Babish Culinary Universe", "Ethan Chlebowski")[i % 3],
        if (tagged) listOf("recipe", "home cooking", "easy recipe", "dinner ideas") else emptyList(),
    )

    fun guitar(
        i: Int,
        tagged: Boolean = true,
    ) = video(
        "guitar-$i${if (tagged) "" else "-c"}",
        Family.GUITAR,
        "${songs[i % songs.size]} guitar playalong with tabs",
        "Ultimate Playalongs",
        if (tagged) listOf("guitar playalong", "guitar tabs", "rock guitar", "guitar cover") else emptyList(),
    )

    fun phonk(
        i: Int,
        tagged: Boolean = true,
    ) = video(
        "phonk-$i${if (tagged) "" else "-c"}",
        Family.PHONK,
        phonkTitles[i % phonkTitles.size],
        listOf("Phonk Nation", "Night Drive Music", "Drift Kings")[i % 3],
        if (tagged) listOf("phonk", "drift phonk", "gym phonk", "phonk mix") else emptyList(),
    )

    fun other(
        i: Int,
        isShort: Boolean = false,
    ) = video(
        "other-$i${if (isShort) "-s" else ""}",
        Family.OTHER,
        otherTitles[i % otherTitles.size] + if (i >= otherTitles.size) " part ${i / otherTitles.size + 1}" else "",
        "Viral Clips ${i % 7}",
        isShort = isShort,
        duration = if (isShort) 35 else 600,
    )

    private val comedyPool =
        listOf(
            "Bill Burr rants about airlines",
            "Taylor Tomlinson on dating apps, full special",
            "Ali Wong roasts her husband",
            "John Mulaney on horses at the airport",
            "Jim Gaffigan on hot pockets",
            "Mike Birbiglia sleepwalking story",
            "Stand-up comedy: crowd work in Chicago",
            "Funniest comedians on parenting",
            "Dave Chappelle on fame",
            "Nate Bargatze on the space race",
            "Hasan Minhaj on homecoming",
            "Comedy roast of a tech CEO",
            "Stand-up special about moving to Texas",
            "Comedian heckler takedown compilation",
            "Iliza on bachelorette parties",
            "Sebastian Maniscalco on family dinners",
            "Best of late night stand-up sets",
            "Comedy club open mic night highlights",
            "Bert Kreischer the machine story",
            "Stand-up comedy about cats and dogs",
        )
    private val techPool =
        listOf(
            "Pixel 10 Pro vs iPhone 17 camera comparison",
            "Galaxy S26 Ultra one week later",
            "Best phones of 2026 so far",
            "Nothing Phone 3 unboxing and first look",
            "OnePlus 14 battery test",
            "Should you buy the Pixel 10a",
            "iPhone 17 review after a month",
            "Galaxy Z Fold 7 durability test",
            "Budget phone camera shootout",
            "Snapdragon 8 Elite benchmark results",
            "Pixel 10 Pro display review",
            "Smartphone charging speed test",
            "Android 17 features hands on",
            "Phone review: Xiaomi 16 Ultra",
            "Foldable phones compared",
            "Is the iPhone 17 Air worth it",
            "Galaxy S26 vs Pixel 10 Pro speed test",
            "Best cheap Android phone",
            "Smartwatch review: Pixel Watch 4",
            "Tech review: earbuds under 100 dollars",
        )
    private val cookingPool =
        listOf(
            "Crispy chicken thighs recipe",
            "How to make fresh pasta at home",
            "Easy beef stew for cold nights",
            "Homemade ramen from scratch",
            "One pot chili recipe",
            "The best chocolate chip cookies",
            "Sourdough starter for beginners",
            "Weeknight stir fry in 15 minutes",
            "Perfect roast potatoes recipe",
            "Chef reacts to viral pasta recipe",
            "Japanese curry at home",
            "Easy banana bread recipe",
            "How to cook the perfect steak",
            "Meal prep for the week, cheap",
            "Thai green curry recipe",
            "Homemade pizza dough guide",
            "Restaurant style fried rice",
            "Easy lasagna recipe",
            "Dinner ideas for busy families",
            "French omelette technique",
        )
    private val guitarPool =
        listOf(
            "Wonderwall guitar lesson for beginners",
            "Learn Enter Sandman on guitar with tabs",
            "Hotel California solo tutorial",
            "Back in Black riff lesson",
            "Seven Nation Army guitar cover",
            "Easy guitar songs for beginners",
            "Nothing Else Matters fingerstyle guitar",
            "Blues guitar improvisation lesson",
            "Smells Like Teen Spirit guitar tabs",
            "Guitar playalong: Highway to Hell",
            "Come As You Are guitar playalong",
            "Rock guitar backing track jam",
            "How to play barre chords on guitar",
            "Sweet Child O Mine intro lesson",
            "Acoustic guitar strumming patterns",
            "Metal guitar riffs tutorial",
            "Guitar scales every player should know",
            "Stairway to Heaven guitar lesson",
            "Guitar playalong with tabs: Paranoid",
            "Electric guitar tone tips",
        )
    private val phonkPool =
        listOf(
            "𝙋𝙃𝙊𝙉𝙆 2026 aggressive",
            "BRAZILIAN PHONK MIX",
            "phonk for night drives",
            "ᴅʀɪꜰᴛ ᴘʜᴏɴᴋ playlist",
            "ＧＹＭ ＰＨＯＮＫ",
            "aggressive phonk workout",
            "phonk mix 1 hour",
            "ⓟⓗⓞⓝⓚ drift music",
            "cowbell phonk mix",
            "Phonk house playlist",
            "best phonk songs 2026",
            "𝐏𝐇𝐎𝐍𝐊 gym motivation",
            "dark phonk for gaming",
            "funk phonk brazil mix",
            "phonk drift car edit music",
            "slowed phonk playlist",
            "memphis phonk classics",
            "phonk remix of a pop song",
            "ＰＨＯＮＫ ＭＩＸ night",
            "chill phonk for studying",
        )

    /** The candidate pool every ranking is drawn from: 20 bare-title videos per family, from channels never watched. */
    fun pool(): List<Video> =
        (0 until 20).flatMap { i ->
            listOf(
                video("pool-comedy-$i", Family.COMEDY, comedyPool[i], "Laugh Factory ${i % 7}"),
                video("pool-tech-$i", Family.TECH, techPool[i], "Tech Spurt ${i % 7}"),
                video("pool-cooking-$i", Family.COOKING, cookingPool[i], "Kitchen Table ${i % 7}"),
                video("pool-guitar-$i", Family.GUITAR, guitarPool[i], "Guitar Lessons ${i % 7}"),
                video("pool-phonk-$i", Family.PHONK, phonkPool[i], "Phonk Radio ${i % 7}"),
                other(100 + i),
            )
        }

    fun familyOf(id: String): Family = families.getValue(id)

    fun engine(brain: UserBrain = UserBrain(schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION)): FlowNeuroEngine {
        val storage: NeuroStorage =
            mockk(relaxed = true) {
                coEvery { load() } returns brain
            }
        val contentStore: NeuroContentStore =
            mockk(relaxed = true) {
                every { topicsFor(any()) } returns null
            }
        return FlowNeuroEngine(mockk(relaxed = true), storage, contentStore, learningPaused = { false })
    }

    suspend fun FlowNeuroEngine.watch(
        video: Video,
        percent: Float = 0.9f,
    ) {
        if (!video.isShort) onVideoInteraction(video, InteractionType.CLICK)
        onVideoInteraction(video, InteractionType.WATCHED, percent)
    }

    /** Months of mixed watching: comedy, tech and cooking interleaved, 40 of each. */
    suspend fun FlowNeuroEngine.longHistory() {
        for (i in 0 until 40) {
            watch(comedy(i))
            watch(tech(i))
            watch(cooking(i))
        }
    }

    private val profile = setOf(Family.COMEDY, Family.TECH, Family.COOKING)

    suspend fun FlowNeuroEngine.shareInTop(
        family: Family,
        n: Int = TOP_N,
    ): Double = shareInTop(setOf(family), n)

    suspend fun FlowNeuroEngine.shareInTop(
        families: Set<Family>,
        n: Int = TOP_N,
    ): Double {
        val pool = pool()
        var hits = 0.0
        var total = 0.0
        repeat(RANK_RUNS) {
            rank(pool, emptySet()).take(n).forEachIndexed { position, video ->
                val gain = 1.0 / ln(position + 2.0)
                total += gain
                if (familyOf(video.id) in families) hits += gain
            }
        }
        return hits / total
    }

    /** Mean cosine between the learned global vector and the family's pool candidates. */
    fun knows(
        brain: UserBrain,
        family: Family,
    ): Double {
        val tokenizer = NeuroTokenizer()
        val idf = IdfSnapshot(emptyMap(), 0)
        val vectors = pool().filter { familyOf(it.id) == family }.map { tokenizer.extractFeatures(it, idf) }
        return vectors.sumOf { NeuroVectorMath.calculateCosineSimilarity(brain.globalVector, it) } / vectors.size
    }

    data class Retention(
        val profileBefore: Double,
        val profileAfter: Double,
        val comedyBefore: Double,
        val comedyAfter: Double,
        val guitarAfter: Double,
        val comedyKnownBefore: Double,
        val comedyKnownAfter: Double,
        val comedyTopicsBefore: Int,
        val comedyTopicsAfter: Int,
        val topicCountAfter: Int,
    )

    private fun comedyKeys(brain: UserBrain): Int =
        brain.globalVector.topics.keys
            .count { "comed" in it || "stand" in it || "funny" in it }

    fun retention(): Retention =
        runBlocking {
            val engine = engine()
            engine.longHistory()
            engine.resetSession()
            val before = engine.shareInTop(Family.COMEDY)
            val profileBefore = engine.shareInTop(profile)
            val keysBefore = comedyKeys(engine.getBrainSnapshot())
            val knownBefore = knows(engine.getBrainSnapshot(), Family.COMEDY)
            for (i in 0 until 100) engine.watch(other(i, isShort = true), percent = 0.8f)
            engine.watch(guitar(0), 1f)
            engine.watch(guitar(1), 1f)
            engine.resetSession()
            val brain = engine.getBrainSnapshot()
            Retention(
                profileBefore = profileBefore,
                profileAfter = engine.shareInTop(profile),
                comedyBefore = before,
                comedyAfter = engine.shareInTop(Family.COMEDY),
                guitarAfter = engine.shareInTop(Family.GUITAR),
                comedyKnownBefore = knownBefore,
                comedyKnownAfter = knows(brain, Family.COMEDY),
                comedyTopicsBefore = keysBefore,
                comedyTopicsAfter = comedyKeys(brain),
                topicCountAfter = brain.globalVector.topics.size,
            ).also { engine.shutdown() }
        }

    data class Rejection(
        val phonkAfterWatches: Double,
        val phonkAfterDislikes: Double,
        val profileAfterDislikes: Double,
        val noveltyAfterDislikes: Double,
        val phonkAfterNotInterested: Double,
        val profileAfterNotInterested: Double,
        val noveltyAfterNotInterested: Double,
    )

    private fun noveltyWeight(brain: UserBrain): Double = 0.2 + (brain.consecutiveSkips / 20.0).coerceIn(0.0, 0.5)

    private suspend fun phonkWatcher(): FlowNeuroEngine {
        val engine = engine()
        engine.longHistory()
        for (i in 0 until 3) engine.watch(phonk(i), percent = 0.6f)
        return engine
    }

    fun rejection(): Rejection =
        runBlocking {
            val disliking = phonkWatcher()
            val afterWatches = disliking.shareInTop(Family.PHONK, n = 40)
            for (i in 3 until 6) disliking.onVideoInteraction(phonk(i), InteractionType.DISLIKED)
            val afterDislikes = disliking.shareInTop(Family.PHONK, n = 40)
            val profileAfterDislikes = disliking.shareInTop(profile)
            val dislikeNovelty = noveltyWeight(disliking.getBrainSnapshot())
            disliking.shutdown()

            val hiding = phonkWatcher()
            for (i in 3 until 7) hiding.markNotInterested(phonk(i, tagged = false))
            Rejection(
                phonkAfterWatches = afterWatches,
                phonkAfterDislikes = afterDislikes,
                profileAfterDislikes = profileAfterDislikes,
                noveltyAfterDislikes = dislikeNovelty,
                phonkAfterNotInterested = hiding.shareInTop(Family.PHONK, n = 40),
                profileAfterNotInterested = hiding.shareInTop(profile),
                noveltyAfterNotInterested = noveltyWeight(hiding.getBrainSnapshot()),
            ).also { hiding.shutdown() }
        }

    data class Phrases(
        val phraseWeight: Double,
        val wordWeight: Double,
        val singleWordQueryShare: Double,
        val queries: List<String>,
    )

    fun phrases(): Phrases =
        runBlocking {
            val engine = engine()
            for (i in 0 until 10) {
                engine.watch(guitar(i), 1f)
                engine.watch(comedy(i))
            }
            val topics = engine.getBrainSnapshot().globalVector.topics
            val queries = engine.generateDiscoveryQueries(resetDepth = true)
            Phrases(
                phraseWeight = topics["guitar playalong"] ?: 0.0,
                wordWeight = topics["guitar"] ?: 0.0,
                singleWordQueryShare = if (queries.isEmpty()) 0.0 else queries.count { ' ' !in it.trim() }.toDouble() / queries.size,
                queries = queries,
            ).also { engine.shutdown() }
        }

    data class ThinBucket(
        val profileShare: Double,
        val guitarShare: Double,
        val comedyShare: Double,
    )

    /** Long history learned elsewhere in the day; the current time bucket only ever saw guitar. */
    fun thinBucket(): ThinBucket =
        runBlocking {
            val trainer = engine()
            trainer.longHistory()
            val global = trainer.getBrainSnapshot()
            trainer.shutdown()

            val bucketTrainer = engine()
            bucketTrainer.watch(guitar(0), 1f)
            bucketTrainer.watch(guitar(1), 1f)
            val bucketBrain = bucketTrainer.getBrainSnapshot()
            bucketTrainer.shutdown()

            val brain =
                global.copy(
                    timeVectors = bucketBrain.timeVectors,
                    timeBucketCounts = bucketBrain.timeBucketCounts,
                )
            val engine = engine(brain)
            ThinBucket(
                profileShare = engine.shareInTop(profile),
                guitarShare = engine.shareInTop(Family.GUITAR),
                comedyShare = engine.shareInTop(Family.COMEDY),
            ).also { engine.shutdown() }
        }

    fun report(
        retention: Retention,
        rejection: Rejection,
        phrases: Phrases,
        thinBucket: ThinBucket,
    ): String =
        buildString {
            appendLine(
                "NEURO LEARNING BENCHMARK (position-weighted top-$TOP_N shares over $RANK_RUNS ranks; known = cosine of global vector to the family)",
            )
            appendLine("RETENTION (120 long-form watches, then 100 Shorts and 2 guitar playalongs; measured next session)")
            appendLine("  profileShare before       = %.3f".format(retention.profileBefore))
            appendLine("  profileShare after        = %.3f".format(retention.profileAfter))
            appendLine("  comedyShare before        = %.3f".format(retention.comedyBefore))
            appendLine("  comedyShare after         = %.3f".format(retention.comedyAfter))
            appendLine("  guitarShare after         = %.3f".format(retention.guitarAfter))
            appendLine("  comedy known before/after = %.3f / %.3f".format(retention.comedyKnownBefore, retention.comedyKnownAfter))
            appendLine("  comedy topics before/after = ${retention.comedyTopicsBefore} / ${retention.comedyTopicsAfter}")
            appendLine("  global topics after       = ${retention.topicCountAfter}")
            appendLine("REJECTION (3 phonk watches; phonk share of top 40)")
            appendLine("  phonk after watches       = %.3f".format(rejection.phonkAfterWatches))
            appendLine("  phonk after 3 thumbs-down = %.3f".format(rejection.phonkAfterDislikes))
            appendLine("  profileShare (top 20)     = %.3f".format(rejection.profileAfterDislikes))
            appendLine("  novelty weight            = %.2f".format(rejection.noveltyAfterDislikes))
            appendLine("  phonk after 4 not-interested = %.3f".format(rejection.phonkAfterNotInterested))
            appendLine("  profileShare (top 20)     = %.3f".format(rejection.profileAfterNotInterested))
            appendLine("  novelty weight            = %.2f".format(rejection.noveltyAfterNotInterested))
            appendLine("PHRASES (10 guitar playalongs + 10 comedy)")
            appendLine("  'guitar playalong' weight = %.3f".format(phrases.phraseWeight))
            appendLine("  'guitar' weight           = %.3f".format(phrases.wordWeight))
            appendLine("  single-word query share   = %.2f".format(phrases.singleWordQueryShare))
            appendLine("  queries                   = ${phrases.queries}")
            appendLine("THIN TIME BUCKET (current bucket saw only 2 guitar videos)")
            appendLine("  profileShare              = %.3f".format(thinBucket.profileShare))
            appendLine("  guitarShare               = %.3f".format(thinBucket.guitarShare))
            appendLine("  comedyShare               = %.3f".format(thinBucket.comedyShare))
        }
}
