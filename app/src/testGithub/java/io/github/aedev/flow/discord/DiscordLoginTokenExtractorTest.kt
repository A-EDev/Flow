package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordLoginTokenExtractorTest {
    @Test
    fun `authenticated Discord app route requests token extraction`() {
        assertThat(DiscordLoginTokenExtractor.isAuthenticatedAppUrl("https://discord.com/app"))
            .isTrue()
        assertThat(DiscordLoginTokenExtractor.isAuthenticatedAppUrl("https://discord.com/app/"))
            .isTrue()
    }

    @Test
    fun `login and non Discord routes do not request token extraction`() {
        assertThat(DiscordLoginTokenExtractor.isAuthenticatedAppUrl("https://discord.com/login"))
            .isFalse()
        assertThat(DiscordLoginTokenExtractor.isAuthenticatedAppUrl("https://example.com/app"))
            .isFalse()
    }

    @Test
    fun `JavaScript result decodes a token and rejects null`() {
        assertThat(DiscordLoginTokenExtractor.decodeJavascriptValue("\"discord-token\""))
            .isEqualTo("discord-token")
        assertThat(DiscordLoginTokenExtractor.decodeJavascriptValue("\"\\\"quoted-token\\\"\""))
            .isEqualTo("quoted-token")
        assertThat(DiscordLoginTokenExtractor.decodeJavascriptValue("null")).isNull()
    }
}
