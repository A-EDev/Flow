package io.github.aedev.flow.ui.components.categories

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.io.IOException

class CategoryErrorsTest {
    private fun refusal(status: HttpStatusCode): Throwable {
        val response = mockk<HttpResponse>(relaxed = true) { every { this@mockk.status } returns status }
        return ResponseException(response, "{\"error\":{}}")
    }

    @Test
    fun `a rate limit or a server error asks the viewer to wait`() {
        assertThat(categoryErrorRes(refusal(HttpStatusCode.TooManyRequests))).isEqualTo(R.string.categories_rate_limited)
        assertThat(categoryErrorRes(refusal(HttpStatusCode.ServiceUnavailable))).isEqualTo(R.string.categories_rate_limited)
    }

    @Test
    fun `anything else is the plain failure, never the exception text`() {
        assertThat(categoryErrorRes(refusal(HttpStatusCode.BadRequest))).isEqualTo(R.string.error_failed_to_load_videos)
        assertThat(categoryErrorRes(IOException("Unable to resolve host"))).isEqualTo(R.string.error_failed_to_load_videos)
    }
}
