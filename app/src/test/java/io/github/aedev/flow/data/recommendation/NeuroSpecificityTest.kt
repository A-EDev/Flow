package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

class NeuroSpecificityTest {
    private val tokenizer = NeuroTokenizer()

    private fun specificity(title: String) = tokenizer.specificity(tokenizer.keysOf(title).single())

    @Test
    fun `words of one subject are specific`() {
        listOf("pixel", "snapdragon", "linux", "android", "earbuds").forEach {
            assertWithWord(it).that(specificity(it)).isAtLeast(0.9)
        }
    }

    @Test
    fun `words every subject uses are not`() {
        listOf("better", "long", "year", "top", "buy", "normal", "test", "phone").forEach {
            assertWithWord(it).that(specificity(it)).isLessThan(0.5)
        }
    }

    @Test
    fun `a phrase is as specific as its words together`() {
        assertThat(tokenizer.specificity("long term")).isLessThan(0.4)
        assertThat(tokenizer.specificity("galaxi s26")).isEqualTo(1.0)
    }

    @Test
    fun `a domain tag does not change the word`() {
        assertThat(tokenizer.specificity("pixel:tech")).isEqualTo(tokenizer.specificity("pixel"))
    }

    @Test
    fun `sharing only a common word with the viewer counts for little`() {
        val viewer = NeuroVectorMath.PreparedVector(ContentVector(topics = mapOf("pixel" to 0.6, "normal" to 0.4)))
        val shared = ContentVector(topics = mapOf("normal" to 1.0))

        val plain = NeuroVectorMath.topicSimilarity(viewer, shared)
        val weighted = NeuroVectorMath.topicSimilarity(viewer, shared, tokenizer::specificity)

        assertThat(weighted).isLessThan(plain * 0.3)
    }

    @Test
    fun `the Chinese table holds only words the word breaker keeps whole`() {
        val file = File("src/main/resources/neuro/freq/zh.txt")
        val lines = file.readLines().filter { it.isNotBlank() }
        val whole =
            lines.filter { line ->
                val word = NeuroText.fold(line.substringBeforeLast(' '))
                word.length >= 2 && NeuroText.words(word, IcuWordBreaker) == listOf(word)
            }
        if (System.getenv("NEURO_WRITE_FREQ_ZH") == "1") file.writeText(whole.joinToString("\n") + "\n")
        assertWithMessage("run the test with NEURO_WRITE_FREQ_ZH=1").that(whole).isEqualTo(lines)
    }

    @Test
    fun `Chinese words every subject uses are not specific`() {
        val chinese = NeuroTokenizer(IcuWordBreaker).also { it.languageProfile = "zh" }

        listOf("新闻", "电影", "音乐", "游戏", "时候").forEach { assertWithWord(it).that(chinese.specificity(it)).isLessThan(0.5) }
        assertThat(chinese.specificity("台积电")).isEqualTo(1.0)
    }

    private fun assertWithWord(word: String) =
        com.google.common.truth.Truth
            .assertWithMessage(word)
}
