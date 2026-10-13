/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSelector
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import io.github.aedev.flow.data.recommendation.IdfSnapshot
import io.github.aedev.flow.data.recommendation.NeuroClusters
import io.github.aedev.flow.data.recommendation.NeuroContentStore
import io.github.aedev.flow.data.recommendation.NeuroMaintenance
import io.github.aedev.flow.data.recommendation.NeuroPhrases
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.data.recommendation.NeuroVectorMath
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark.Family
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

/**
 * The #907 reports as scenarios on the real engine. Each returns numbers, so the change that fixes a
 * report flips its assertion and the report file shows before and after. Ranking runs without jitter
 * ([NeuroLearningBenchmark.NO_JITTER]) unless a result says otherwise, so every number is repeatable.
 */
internal object Issue907Scenarios {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val JITTER_RUNS = 6
    private const val TECH_WATCHES = 150
    private const val TOP_KEYS = 30

    private val tokenizer = NeuroTokenizer()

    /** Words that name no subject; a learned key built from one is a bridge to unrelated videos. */
    val GENERIC_WORDS = setOf("after", "month", "later", "long", "term", "worth", "life", "week", "pro", "test", "speed", "ultra")

    data class OffFamily(
        val top10: Double,
        val positions5to20: Double,
    )

    fun offFamily(
        ranked: List<Video>,
        isOff: (Video) -> Boolean,
    ): OffFamily {
        val window = ranked.drop(4).take(16)
        return OffFamily(
            top10 = ranked.take(10).count(isOff) / 10.0,
            positions5to20 = if (window.isEmpty()) 0.0 else window.count(isOff).toDouble() / window.size,
        )
    }

    private fun video(
        id: String,
        title: String,
        channel: String,
        uploadDate: String = "3 days ago",
        tags: List<String> = emptyList(),
        duration: Int = 720,
        category: String? = null,
    ) = Video(
        id = id,
        title = title,
        channelName = channel,
        channelId = "ch-${channel.lowercase().replace(' ', '-')}",
        thumbnailUrl = "",
        duration = duration,
        viewCount = 400_000,
        uploadDate = uploadDate,
        tags = tags,
        category = category,
    )

    private fun family(family: Family) = NeuroLearningBenchmark.pool().filter { NeuroLearningBenchmark.familyOf(it.id) == family }

    private suspend fun FlowNeuroEngine.watchAll(videos: List<Video>) = with(NeuroLearningBenchmark) { videos.forEach { watch(it) } }

    private fun rankOnce(
        brain: UserBrain,
        pool: List<Video>,
    ): List<Video> =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER)
            engine.rank(pool, emptySet()).also { engine.shutdown() }
        }

    private fun rankJittered(
        brain: UserBrain,
        pool: List<Video>,
        isOff: (Video) -> Boolean,
    ): OffFamily =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine(brain, java.util.Random(907))
            val runs = List(JITTER_RUNS) { offFamily(engine.rank(pool, emptySet()), isOff) }
            engine.shutdown()
            OffFamily(runs.map { it.top10 }.average(), runs.map { it.positions5to20 }.average())
        }

    /** Share of the strongest learned keys that contain a [GENERIC_WORDS] word. */
    fun genericKeyShare(brain: UserBrain): Double {
        val top =
            brain.globalVector.topics.entries
                .sortedByDescending { it.value }
                .take(TOP_KEYS)
        if (top.isEmpty()) return 0.0
        return top.count { e -> NeuroScoring.stripDomainTag(e.key).split(' ').any { it in GENERIC_WORDS } }.toDouble() / top.size
    }

    // ── Problem 5: a tech-only viewer, and videos that share only a generic word ──

    private val bridgeTitles =
        listOf(
            "Pixel art painting timelapse",
            "Life after divorce: one month later",
            "Long term travel on a budget",
            "Is a pro chef knife worth it",
            "Camera shy cat finally comes out",
            "Battery powered RC car speed test",
            "Galaxy brain chess moments",
            "Ultra marathon week in the desert",
        )

    fun bridges(uploadDate: String = "3 days ago"): List<Video> =
        (bridgeTitles + bridgeTitles.map { "$it part 2" })
            .mapIndexed { i, title -> video("bridge-$i", title, "Creator Studio ${'A' + i}", uploadDate) }

    fun isBridge(video: Video) = video.id.startsWith("bridge-")

    data class TechBridges(
        val genericKeyShare: Double,
        val ranked: OffFamily,
        val jittered: OffFamily,
        val afterTenSkips: OffFamily,
        val freshBridgesOldTech: OffFamily,
        val boredBridgeOutranksOnTopic: Boolean,
    )

    val techBrain: UserBrain by lazy {
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            engine.watchAll((0 until TECH_WATCHES).map { NeuroLearningBenchmark.tech(it) })
            engine.resetSession()
            engine.getBrainSnapshot().also { engine.shutdown() }
        }
    }

    fun techBridges(): TechBridges {
        val brain = techBrain
        val tech = family(Family.TECH)
        val pool = tech + bridges()
        val bored = brain.copy(consecutiveSkips = 10)
        val onTopic = video("pair-galaxy", "Galaxy S26 Ultra review", "Tech Spurt 1")
        val bridge = video("pair-divorce", "Life after divorce: one month later", "Creator Studio R")
        return TechBridges(
            genericKeyShare = genericKeyShare(brain),
            ranked = offFamily(rankOnce(brain, pool), ::isBridge),
            jittered = rankJittered(brain, pool, ::isBridge),
            afterTenSkips = offFamily(rankOnce(bored, pool), ::isBridge),
            freshBridgesOldTech =
                offFamily(rankOnce(brain, tech.map { it.copy(uploadDate = "1 year ago") } + bridges("2 days ago")), ::isBridge),
            boredBridgeOutranksOnTopic = rankOnce(bored, listOf(onTopic, bridge)).first().id == bridge.id,
        )
    }

    // ── Problems 3 and 5 in other languages: function words are never interests ──

    private class Language(
        val code: String,
        val dishes: List<String>,
        val watched: List<String>,
        val unrelated: List<String>,
    )

    // "%s" is a dish. Watched and unrelated titles share only function words, never a content word.
    private val languages =
        listOf(
            Language(
                "es",
                listOf("tortilla", "paella", "gazpacho", "croquetas", "empanadas", "churros", "lentejas", "fabada"),
                listOf(
                    "Cómo hacer %s para la cena",
                    "La mejor receta de %s con los trucos de la abuela",
                    "%s casero en el horno",
                    "Por qué mis %s salen así",
                ),
                listOf(
                    "Cómo arreglar la bicicleta para el verano",
                    "La manera correcta de aprender a nadar con los niños",
                    "Por qué el cielo es azul",
                    "Los secretos de la bolsa para el que empieza",
                    "Cómo cambiar la rueda del coche en la carretera",
                    "La historia de los romanos en el norte",
                    "Por qué los gatos duermen tanto",
                    "El secreto de la batería para el móvil",
                    "Cómo pintar la casa con los amigos",
                    "La verdad sobre el fútbol de los noventa",
                ),
            ),
            Language(
                "fr",
                listOf("crêpes", "ratatouille", "quiche", "cassoulet", "tartiflette", "croissants", "madeleines", "bouillabaisse"),
                listOf(
                    "Comment faire des %s pour le dîner",
                    "La meilleure recette de %s avec les astuces de mamie",
                    "Les %s maison au four",
                    "Pourquoi mes %s ratent toujours",
                ),
                listOf(
                    "Comment réparer le vélo pour l'été",
                    "La bonne façon d'apprendre à nager avec les enfants",
                    "Pourquoi le ciel est bleu",
                    "Les secrets de la bourse pour les débutants",
                    "Comment changer la roue de la voiture sur la route",
                    "L'histoire des Romains dans le nord",
                    "Pourquoi les chats dorment autant",
                    "Le truc de la batterie pour le téléphone",
                    "Comment peindre la façade avec des amis",
                    "La vérité sur le football des années 90",
                ),
            ),
            Language(
                "ru",
                listOf("борщ", "пельмени", "блины", "оливье", "шашлык", "сырники", "солянка", "голубцы"),
                listOf(
                    "Как приготовить %s на ужин",
                    "Лучший рецепт: %s как у бабушки",
                    "%s в духовке это просто",
                    "Почему у меня не получается %s",
                ),
                listOf(
                    "Как починить велосипед на лето",
                    "Простой способ научить детей плавать как профи",
                    "Почему небо голубое и что это значит",
                    "Как поменять колесо на дороге",
                    "История римлян на севере это интересно",
                    "Почему кошки спят так много",
                    "Как покрасить дом на выходных",
                    "Правда о футболе в девяностые как это было",
                    "Как выбрать ноутбук для учёбы",
                    "Почему у меня не работает телефон",
                ),
            ),
            Language(
                "de",
                listOf("käsespätzle", "schnitzel", "sauerbraten", "rouladen", "kartoffelsalat", "apfelstrudel", "brezeln", "maultaschen"),
                listOf(
                    "Wie man %s für das Abendessen macht",
                    "Das beste Rezept für %s mit den Tricks von Oma",
                    "%s aus dem Ofen ist einfach",
                    "Warum mein %s nicht gelingt",
                ),
                listOf(
                    "Wie man das Fahrrad für den Sommer repariert",
                    "Die richtige Art mit den Kindern schwimmen zu lernen",
                    "Warum der Himmel blau ist",
                    "Die Geheimnisse der Börse für den Anfang",
                    "Wie man das Rad am Auto auf der Straße wechselt",
                    "Die Geschichte der Römer im Norden",
                    "Warum Katzen so viel schlafen",
                    "Der Trick mit dem Akku für das Handy",
                    "Wie man das Haus mit den Freunden streicht",
                    "Die Wahrheit über den Fußball der Neunziger",
                ),
            ),
        )

    data class LanguageViewer(
        val language: String,
        val functionWordKeys: List<String>,
        val ranked: OffFamily,
    )

    /** A viewer who watches cooking in [Language.code], ranked against unrelated titles in that language. */
    fun languageViewers(): List<LanguageViewer> =
        languages.map { language ->
            runBlocking {
                val engine =
                    NeuroLearningBenchmark.engine(
                        UserBrain(schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION, languageProfile = language.code),
                    )
                val watched =
                    language.dishes.flatMapIndexed { d, dish ->
                        language.watched.mapIndexed {
                            t,
                            template,
                            ->
                            video("${language.code}-w-$d-$t", template.format(dish), "Cocina ${d % 3}")
                        }
                    }
                engine.watchAll(watched + watched)
                val brain = engine.getBrainSnapshot()
                engine.shutdown()
                val stop = tokenizer.stopList(language.code)
                val keys =
                    brain.globalVector.topics.entries
                        .sortedByDescending { it.value }
                        .take(TOP_KEYS)
                        .map { NeuroScoring.stripDomainTag(it.key) }
                val onTopic =
                    language.dishes.flatMapIndexed { d, dish ->
                        listOf(1, 2).map { k ->
                            video(
                                "${language.code}-on-$d-$k",
                                language.watched[(d + k) % language.watched.size].format(dish),
                                "Recetas ${d % 5}",
                            )
                        }
                    }
                val unrelated = language.unrelated.mapIndexed { i, title -> video("${language.code}-off-$i", title, "Canal ${i % 5}") }
                LanguageViewer(
                    language = language.code,
                    functionWordKeys = keys.filter { key -> key.split(' ').any { it in stop } },
                    ranked = offFamily(rankOnce(brain, onTopic + unrelated)) { it.id.contains("-off-") },
                )
            }
        }

    // ── Key spec 2: the same subject in other forms of its words ──

    private class Forms(
        val code: String,
        val watched: List<String>,
        val sameSubject: List<String>,
        val unrelated: List<String>,
    )

    // Watched titles use one form of each subject word, candidates another; unrelated titles share none.
    private val forms =
        listOf(
            Forms(
                "en",
                listOf(
                    "Restoring a vintage guitar",
                    "Kayaking down a canyon river",
                    "Baking sourdough loaf at home",
                    "Building a treehouse cabin",
                    "Repairing an old motorcycle engine",
                ),
                listOf(
                    "Five vintage guitars restored",
                    "Kayak trips through canyons",
                    "Sourdough loaves for beginners",
                    "Treehouse cabins in the forest",
                    "Motorcycle engines repaired",
                    "Guitars from the seventies",
                    "Canyon kayaks compared",
                    "Loaves of sourdough bread",
                    "Cabins built in trees",
                    "Old motorcycles brought back",
                ),
                listOf(
                    "Chess openings explained",
                    "Stock market crash history",
                    "Volcano eruption footage",
                    "Classical piano recital",
                    "Tax filing deadline tips",
                    "Marathon training plan",
                    "Space telescope images",
                    "Desert lizard documentary",
                    "Football transfer rumours",
                    "Origami crane folding",
                ),
            ),
            Forms(
                "es",
                listOf(
                    "Restaurando una guitarra antigua",
                    "Receta de croqueta casera",
                    "Construyendo una cabaña en el árbol",
                    "Reparando un motor de moto",
                    "Viaje en kayak por el cañón",
                ),
                listOf(
                    "Cinco guitarras antiguas restauradas",
                    "Croquetas caseras de jamón",
                    "Cabañas en los árboles",
                    "Motores de motos reparados",
                    "Kayaks en cañones del norte",
                    "Guitarras de los setenta",
                    "Las croquetas de mi abuela",
                    "Cabañas de madera",
                    "Motos clásicas restauradas",
                    "Cañones para recorrer en kayak",
                ),
                listOf(
                    "Aperturas de ajedrez explicadas",
                    "Historia de la bolsa",
                    "Erupción de un volcán",
                    "Recital de piano clásico",
                    "Plazos para la declaración de impuestos",
                    "Plan de entrenamiento de maratón",
                    "Imágenes del telescopio espacial",
                    "Documental de lagartos del desierto",
                    "Rumores de fichajes de fútbol",
                    "Grullas de papel de origami",
                ),
            ),
            Forms(
                "ru",
                listOf(
                    "Реставрация старой гитары",
                    "Рецепт домашнего хлеба",
                    "Строим домик на дереве",
                    "Ремонт двигателя мотоцикла",
                    "Сплав на байдарке по каньону",
                ),
                listOf(
                    "Пять старых гитар после реставрации",
                    "Рецепты хлеба на закваске",
                    "Домики на деревьях в лесу",
                    "Двигатели мотоциклов после ремонта",
                    "Байдарки для сплава по каньонам",
                    "Гитары семидесятых годов",
                    "Хлеб в духовке рецепты",
                    "Деревянные домики своими руками",
                    "Мотоциклы после реставрации",
                    "Каньоны для сплава на байдарках",
                ),
                listOf(
                    "Шахматные дебюты",
                    "История биржевого краха",
                    "Извержение вулкана",
                    "Концерт классической музыки",
                    "Сроки подачи налоговой декларации",
                    "План подготовки к марафону",
                    "Снимки космического телескопа",
                    "Фильм о пустынных ящерицах",
                    "Слухи о трансферах футболистов",
                    "Журавлик оригами",
                ),
            ),
        )

    data class SameSubject(
        val language: String,
        val sameSubjectInTop10: Double,
    )

    /** Share of the top ten that is the watched subject written in other forms of its words. */
    fun sameSubjectInOtherForms(): List<SameSubject> =
        forms.map { language ->
            runBlocking {
                val engine =
                    NeuroLearningBenchmark.engine(
                        UserBrain(schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION, languageProfile = language.code),
                    )
                val watched = language.watched.mapIndexed { i, title -> video("${language.code}-fw-$i", title, "Workshop ${i % 2}") }
                engine.watchAll(watched + watched + watched)
                val brain = engine.getBrainSnapshot()
                engine.shutdown()
                val same = language.sameSubject.mapIndexed { i, title -> video("${language.code}-same-$i", title, "Maker ${i % 5}") }
                val unrelated = language.unrelated.mapIndexed { i, title -> video("${language.code}-other-$i", title, "Studio ${i % 5}") }
                val top = rankOnce(brain, same + unrelated).take(10)
                SameSubject(language.code, top.count { it.id.contains("-same-") } / 10.0)
            }
        }

    // ── Problem 6: months of comedy, then two guitar playalongs ──

    data class BurstSeeds(
        val guitarSeeds: Int,
        val guitarSeedWeight: Double,
        val comedySeedWeight: Double,
    )

    fun burstSeeds(now: Long = System.currentTimeMillis()): BurstSeeds {
        val comedy =
            (1..38).map { n ->
                GraphSeedInput(
                    id = "comedy-$n",
                    title = "Stand-up comedy special $n",
                    channelId = "ch-comedy-${n % 5}",
                    source = GraphSeedSource.WATCH_HISTORY,
                    engagementWeight = 0.45,
                    timestamp = now - (1 + n % 7) * DAY_MS,
                    durationSec = 3600,
                    percentWatched = 45.0,
                )
            }
        val guitar =
            listOf("Wonderwall guitar playalong with tabs", "Hotel California guitar playalong with tabs").mapIndexed { i, title ->
                GraphSeedInput(
                    id = "guitar-$i",
                    title = title,
                    channelId = "ch-guitar",
                    source = GraphSeedSource.WATCH_HISTORY,
                    engagementWeight = 1.0,
                    timestamp = now - DAY_MS,
                    durationSec = 240,
                    percentWatched = 100.0,
                )
            }
        val mass = mapOf("comedy" to 0.6, "guitar" to 0.05)
        val seeds =
            GraphSeedSelector.selectWithLongTerm(
                (guitar + comedy).sortedByDescending { it.timestamp },
                4,
                emptyList(),
                mass,
                { key -> if ("guitar" in key) "guitar" else "comedy" },
                now,
                topicScores = mass,
            )
        return BurstSeeds(
            guitarSeeds = seeds.count { it.startsWith("guitar") },
            guitarSeedWeight = GraphSeedSelector.scoreSeed(guitar[0], now),
            comedySeedWeight = GraphSeedSelector.scoreSeed(comedy[0], now),
        )
    }

    // ── Problem 2: someone who watches a review now and then ──

    data class OccasionalReview(
        val learned: List<String>,
        val ranked: OffFamily,
    )

    fun occasionalReview(): OccasionalReview =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            engine.watchAll((0 until 40).flatMap { listOf(NeuroLearningBenchmark.comedy(it), NeuroLearningBenchmark.cooking(it)) })
            val before = engine.getBrainSnapshot().globalVector.topics
            val review =
                video(
                    "review-pixel",
                    "Google Pixel 10 Pro Review",
                    "MKBHD",
                    tags = listOf("google pixel", "pixel 10 pro"),
                    duration = 900,
                    category = "Science & Technology",
                )
            engine.watchAll(listOf(review))
            engine.resetSession()
            val after = engine.getBrainSnapshot()
            engine.shutdown()
            val learned =
                after.globalVector.topics.entries
                    .filter { (key, value) -> value > (before[key] ?: 0.0) + 1e-9 }
                    .sortedByDescending { it.value }
                    .map { it.key }
            val probes =
                listOf("Pro chef knife skills", "Pixel art painting timelapse", "Google Maps hidden features", "Pixel 9 vs Pixel 10 camera")
                    .mapIndexed { i, title -> video("bridge-review-$i", title, "Creator Studio ${'W' + i}") }
            val pool = family(Family.COMEDY) + family(Family.COOKING) + probes
            OccasionalReview(learned, offFamily(rankOnce(after, pool), ::isBridge))
        }

    // ── Problem 7: why every interest looks the same ──

    data class Spread(
        val min: Double,
        val max: Double,
    )

    /** Twelve picks set to 0.5, then 400 full watches of unrelated videos at a mature brain's learning rate. */
    fun pickedTopicsAfterUnrelatedWatching(): Spread {
        val rate = 0.051
        val picks =
            listOf(
                "astronomy",
                "chess",
                "woodworking",
                "anime",
                "yoga",
                "history",
                "finance",
                "travel",
                "photo",
                "garden",
                "plane",
                "pottery",
            )
        val unrelated = (family(Family.COMEDY) + family(Family.COOKING)).map { tokenizer.extractFeatures(it, IdfSnapshot(emptyMap(), 0)) }
        var vector = ContentVector(topics = picks.associateWith { 0.5 })
        repeat(400) { i ->
            vector = NeuroVectorMath.learn(vector, unrelated[i % unrelated.size], 1.0, NeuroVectorMath.LONG_TERM, rate)
        }
        val weights = picks.map { vector.topics[it] ?: 0.0 }
        val strongest = weights.max()
        return Spread(weights.min() / strongest, weights.max() / strongest)
    }

    data class Frequency(
        val everyVideo: Double,
        val quarterOfVideos: Double,
    )

    /** Topic A is in every watched video at weight 0.25, topic B in one of four at 0.45; the engine also plants. */
    fun watchFrequency(): Frequency {
        val rate = 0.051
        var vector = ContentVector()
        repeat(400) { i ->
            val topics = linkedMapOf("topic-a" to 0.25)
            if (i % 4 == 0) topics["topic-b"] = 0.45
            val rest = sqrt(1.0 - topics.values.sumOf { it * it })
            repeat(4) { k -> topics["filler-$i-$k"] = rest / 2.0 }
            val target = ContentVector(topics = topics)
            vector = NeuroVectorMath.learn(vector, NeuroVectorMath.phraseFirst(target), 1.0, NeuroVectorMath.LONG_TERM, rate)
            vector = NeuroVectorMath.plantTopics(vector, target, NeuroScoring.TOPIC_ACQUISITION_FLOOR, NeuroScoring.TOPIC_ACQUISITION_TOP_K)
        }
        return Frequency(vector.topics["topic-a"] ?: 0.0, vector.topics["topic-b"] ?: 0.0)
    }

    // ── Problem 8: reset, then the app restarts ──

    data class Restart(
        val topics: Int,
        val interactions: Int,
        val saves: Int,
        val oldFilesRead: Boolean,
    )

    /**
     * Startup when the saved brain reads as empty and an older brain file is still on disk: after a
     * reset ([savedBrainExists]) or on the first start after an upgrade from an older format.
     */
    fun restartWithOldBrainFile(savedBrainExists: Boolean): Restart =
        runBlocking {
            val old =
                UserBrain(
                    totalInteractions = 500,
                    hasCompletedOnboarding = true,
                    globalVector = ContentVector(topics = (1..50).associate { "old topic $it" to 0.05 + it / 100.0 }),
                )
            var saves = 0
            var oldFilesRead = false
            val storage: NeuroStorage =
                mockk(relaxed = true) {
                    coEvery { load() } returns null
                    coEvery { hasSavedBrain() } returns savedBrainExists
                    coEvery { migrateLegacy() } answers {
                        oldFilesRead = true
                        old
                    }
                    coEvery { save(any()) } answers { saves++ }
                }
            val contentStore: NeuroContentStore = mockk(relaxed = true)
            val engine = FlowNeuroEngine(mockk(relaxed = true), storage, contentStore, learningPaused = { false })
            engine.initialize()
            val held = engine.getBrainSnapshot()
            engine.shutdown()
            Restart(held.globalVector.topics.size, held.totalInteractions, saves, oldFilesRead)
        }

    /** What Home searches with on a brand-new brain, and whether those searches count as learned. */
    fun starterSearches(): Pair<List<String>, Int> =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            val queries = engine.generateDiscoveryQueries(resetDepth = true)
            (queries to engine.getBrainSnapshot().recentQueryTokens.size).also { engine.shutdown() }
        }

    // ── Problem 9: onboarding picks as interest groups and chips ──

    data class Onboarding(
        val groups: Int,
        val groupsWithoutPickLinks: Int,
        val chipLabels: List<String>,
    )

    fun onboardingGroups(): Onboarding =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            engine.completeOnboarding(linkedSetOf("Cooking", "Gaming", "Fitness", "Anime", "Programming", "Travel", "Music"))
            val brain = engine.getBrainSnapshot()
            val chips = engine.interestChips(System.currentTimeMillis())
            engine.shutdown()

            fun groups(affinities: Map<String, Double>) =
                NeuroClusters
                    .buildClusters(
                        topicScores = brain.globalVector.topics,
                        affinities = affinities,
                        channelTopicProfiles = brain.channelTopicProfiles,
                        categories = NeuroTopicCatalog.TOPIC_CATEGORIES,
                        normalizeLemma = tokenizer::normalizeLemma,
                        tagAffinities = brain.tagAffinities,
                    ).size
            Onboarding(groups(brain.topicAffinities), groups(emptyMap()), chips.map { it.label })
        }

    // ── Problems 2 to 4: what one title or one search teaches ──

    data class Lesson(
        val learned: Map<String, Double>,
        val planted: List<String>,
    )

    fun learnFromTitle(
        title: String,
        tags: List<String> = emptyList(),
    ): Lesson =
        runBlocking {
            val video = video("lesson", title, "Some Channel", tags = tags)
            val engine = NeuroLearningBenchmark.engine()
            engine.watchAll(listOf(video))
            val learned = engine.getBrainSnapshot().globalVector.topics
            engine.shutdown()
            val idf = IdfSnapshot(emptyMap(), 0)
            val features = tokenizer.extractFeatures(video, idf)
            Lesson(
                learned,
                NeuroVectorMath.selectPlantKeys(features, NeuroScoring.TOPIC_ACQUISITION_TOP_K, tokenizer.plantFilter(video, idf, 0)),
            )
        }

    fun learnFromSearch(query: String): Map<String, Double> =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            engine.onSearchQuery(query)
            engine
                .getBrainSnapshot()
                .globalVector.topics
                .also { engine.shutdown() }
        }

    data class ChancePairs(
        val titles: Int,
        val pairs: Int,
        val unproven: Int,
        val planted: Int,
        val plantedUnproven: Int,
    ) {
        val plantedUnprovenShare: Double get() = if (planted == 0) 0.0 else plantedUnproven.toDouble() / planted
    }

    /**
     * Title word pairs nothing vouches for: not an NPMI phrase and not a known compound. Counted over
     * the keys extraction writes and the keys the acquisition floor would plant for each title.
     */
    fun chancePairs(
        videos: List<Video>,
        idf: IdfSnapshot = IdfSnapshot(emptyMap(), 0),
    ): ChancePairs {
        var pairs = 0
        var unproven = 0
        var planted = 0
        var plantedUnproven = 0

        fun isUnproven(key: String): Boolean {
            val base = NeuroScoring.stripDomainTag(key)
            return ' ' in base && !tokenizer.isKnownPhrase(base, idf)
        }
        videos.forEach { video ->
            val features = tokenizer.extractFeatures(video, idf)
            val keys = features.topics.keys.filter { ' ' in NeuroScoring.stripDomainTag(it) }
            pairs += keys.size
            unproven += keys.count(::isUnproven)
            val plant =
                NeuroVectorMath.selectPlantKeys(
                    features,
                    NeuroScoring.TOPIC_ACQUISITION_TOP_K,
                    tokenizer.plantFilter(video, idf, 0),
                )
            planted += plant.size
            plantedUnproven += plant.count(::isUnproven)
        }
        return ChancePairs(videos.size, pairs, unproven, planted, plantedUnproven)
    }

    fun titlesAsVideos(titles: List<String>): List<Video> = titles.mapIndexed { i, title -> video("corpus-$i", title, "Channel ${i % 11}") }
}
