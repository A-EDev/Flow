package io.github.aedev.flow.innertube.pages.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The suggest host answers with a bare array, historically wrapped in a JSONP callback. The fourth
 * element of a row is the knowledge panel YouTube attaches to a few suggestions.
 */
class SearchSuggestionsPageTest {
    private val live =
        """
        ["linus",[
          ["linus tech tips",0,[512,433]],
          ["linus tech tips laptop",0,[512,433,131]],
          ["linus torvalds",0,[512,433,47],{
            "zae":"/m/04gnf",
            "zaf":"Finnish-American software engineer",
            "zai":"https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcR",
            "zao":"Linus Torvalds",
            "zaq":"https://www.britannica.com/biography/Linus-Torvalds"
          }]
        ]]
        """.trimIndent()

    @Test
    fun `reads every suggestion in order`() {
        val suggestions = parseSearchSuggestions(live)

        assertThat(suggestions.map { it.text })
            .containsExactly("linus tech tips", "linus tech tips laptop", "linus torvalds")
            .inOrder()
    }

    @Test
    fun `reads the knowledge panel where youtube attached one`() {
        val entity = parseSearchSuggestions(live).single { it.text == "linus torvalds" }.entity

        assertThat(entity?.title).isEqualTo("Linus Torvalds")
        assertThat(entity?.description).isEqualTo("Finnish-American software engineer")
        assertThat(entity?.thumbnailUrl).startsWith("https://")
    }

    @Test
    fun `an ordinary suggestion carries no entity`() {
        assertThat(parseSearchSuggestions(live).first().entity).isNull()
    }

    @Test
    fun `unwraps a jsonp callback`() {
        val wrapped = """window.google.ac.h(["linus",[["linus tech tips",0,[512]]]])"""

        assertThat(parseSearchSuggestions(wrapped).map { it.text }).containsExactly("linus tech tips")
    }

    @Test
    fun `drops repeats that differ only in case`() {
        val duplicated = """["a",[["Sam Sulek",0,[]],["sam sulek",0,[]]]]"""

        assertThat(parseSearchSuggestions(duplicated)).hasSize(1)
    }

    @Test
    fun `a malformed body yields nothing rather than throwing`() {
        assertThat(parseSearchSuggestions("")).isEmpty()
        assertThat(parseSearchSuggestions("not json at all")).isEmpty()
        assertThat(parseSearchSuggestions("""["only the query"]""")).isEmpty()
    }
}
