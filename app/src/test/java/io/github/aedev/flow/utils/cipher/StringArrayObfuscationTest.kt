package io.github.aedev.flow.utils.cipher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The string-array obfuscation YouTube's player uses, and the part of it that defeats extraction.
 *
 * Both shapes here were taken from real players on 2026-09-22: neither the array's name nor its
 * delimiter is stable, and the previous pattern recognised only `var Q=...split("}")`.
 */
class StringArrayObfuscationTest {
    private fun blob(vararg parts: String) = parts.joinToString(";").padEnd(120, 'x')

    @Test
    fun `a two character array name is recognised`() {
        val js = """var LJ="${blob("L", "G", "url", "split", "join")}".split(";");var x=LJ[3];"""

        assertThat(FunctionNameExtractor.hasQArrayObfuscation(js)).isTrue()
    }

    @Test
    fun `the legacy single character brace separated array is still recognised`() {
        val js = """var Q="${"a}b}c".padEnd(120, 'x')}".split("}");var x=Q[1];"""

        assertThat(FunctionNameExtractor.hasQArrayObfuscation(js)).isTrue()
    }

    @Test
    fun `a player with no string array is not flagged`() {
        val js = """var a=function(b){b=b.split("");b.reverse();return b.join("")};"""

        assertThat(FunctionNameExtractor.hasQArrayObfuscation(js)).isFalse()
        assertThat(FunctionNameExtractor.hasComputedArrayIndices(js)).isFalse()
    }

    @Test
    fun `literal indices are readable, computed indices are not`() {
        val literal = """var jd="${blob("a", "b", "c")}".split(";");var x=jd[2];"""
        val computed = """var LJ="${blob("a", "b", "c")}".split(";");var x=f[LJ[c^7001]](LJ[6]);"""

        assertThat(FunctionNameExtractor.hasComputedArrayIndices(literal)).isFalse()
        assertThat(FunctionNameExtractor.hasComputedArrayIndices(computed)).isTrue()
    }
}
