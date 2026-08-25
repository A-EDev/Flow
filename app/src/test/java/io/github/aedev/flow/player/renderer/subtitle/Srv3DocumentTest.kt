package io.github.aedev.flow.player.renderer.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Srv3DocumentTest {
    @Test
    fun `plain paragraph with no head elements falls back to defaults`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8" ?>
            <timedtext format="3">
              <body>
                <p t="500" d="3000">Hello world</p>
              </body>
            </timedtext>
            """.trimIndent()

        val paragraphs = parseSrv3Document(xml)

        assertEquals(1, paragraphs.size)
        val paragraph = paragraphs.single()
        assertEquals(500L, paragraph.startMs)
        assertEquals(3000L, paragraph.durationMs)
        assertEquals("Hello world", paragraph.runs.joinToString("") { it.text })
        assertEquals(Srv3Window.DEFAULT_ANCHOR_POINT, paragraph.window.anchorPoint)
        assertFalse(paragraph.runs.single().pen.bold)
    }

    @Test
    fun `a pen referenced by a paragraph applies bold, italic and color`() {
        val xml =
            """
            <timedtext format="3">
              <head>
                <pen id="1" b="1" i="1" fc="#FF0000" fo="254"/>
              </head>
              <body>
                <p t="0" d="1000" p="1">Styled text</p>
              </body>
            </timedtext>
            """.trimIndent()

        val pen = parseSrv3Document(xml).single().runs.single().pen

        assertTrue(pen.bold)
        assertTrue(pen.italic)
        assertEquals("#FF0000", pen.foregroundColor)
        assertEquals(254, pen.foregroundOpacity)
    }

    @Test
    fun `nested s runs carry their own pen and preserve order`() {
        val xml =
            """
            <timedtext format="3">
              <head>
                <pen id="0"/>
                <pen id="2" fc="#00FF00"/>
              </head>
              <body>
                <p t="0" d="1000" p="0">Hello <s p="2">green</s> world</p>
              </body>
            </timedtext>
            """.trimIndent()

        val runs = parseSrv3Document(xml).single().runs

        assertEquals(listOf("Hello ", "green", " world"), runs.map { it.text })
        assertEquals(null, runs[0].pen.foregroundColor)
        assertEquals("#00FF00", runs[1].pen.foregroundColor)
        assertEquals(null, runs[2].pen.foregroundColor)
    }

    @Test
    fun `window position and justification are resolved from wp and ws ids`() {
        val xml =
            """
            <timedtext format="3">
              <head>
                <ws id="0" ju="0"/>
                <wp id="0" ap="0" ah="10" av="15"/>
              </head>
              <body>
                <p t="0" d="1000" wp="0" ws="0">Top left, left-justified</p>
              </body>
            </timedtext>
            """.trimIndent()

        val window = parseSrv3Document(xml).single().window

        assertEquals(0, window.anchorPoint)
        assertEquals(10f, window.horizontalPercent)
        assertEquals(15f, window.verticalPercent)
        assertEquals(Srv3Window.JUSTIFY_LEFT, window.justify)
        assertTrue(window.positioned)
    }

    @Test
    fun `a paragraph with no wp is not marked positioned so the player places it`() {
        val xml =
            """
            <timedtext format="3">
              <head>
                <ws id="0" ju="0"/>
              </head>
              <body>
                <p t="0" d="1000" ws="0">Left-justified, but unplaced</p>
              </body>
            </timedtext>
            """.trimIndent()

        val window = parseSrv3Document(xml).single().window

        assertFalse("an unplaced cue must not carry an explicit line", window.positioned)
        assertEquals(Srv3Window.JUSTIFY_LEFT, window.justify)
    }

    @Test
    fun `literal backslash-n escapes become real newlines and multiline text is preserved`() {
        val xml =
            """
            <timedtext format="3">
              <body>
                <p t="0" d="1000">Line one\nLine two\nLine three</p>
              </body>
            </timedtext>
            """.trimIndent()

        val text = parseSrv3Document(xml).single().runs.joinToString("") { it.text }

        assertEquals("Line one\nLine two\nLine three", text)
    }

    @Test
    fun `multiple paragraphs are returned in document order`() {
        val xml =
            """
            <timedtext format="3">
              <body>
                <p t="0" d="500">First</p>
                <p t="600" d="500">Second</p>
              </body>
            </timedtext>
            """.trimIndent()

        val paragraphs = parseSrv3Document(xml)

        assertEquals(listOf(0L, 600L), paragraphs.map { it.startMs })
        assertEquals(listOf("First", "Second"), paragraphs.map { p -> p.runs.joinToString("") { it.text } })
    }

    @Test
    fun `a paragraph with no text content at all is dropped`() {
        val xml =
            """
            <timedtext format="3">
              <body>
                <p t="0" d="500"></p>
              </body>
            </timedtext>
            """.trimIndent()

        assertEquals(0, parseSrv3Document(xml).size)
    }
}
