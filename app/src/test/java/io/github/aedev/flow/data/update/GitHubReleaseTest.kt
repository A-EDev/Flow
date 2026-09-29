package io.github.aedev.flow.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class GitHubReleaseTest {
    private fun asset(
        name: String,
        size: Long = 1,
        digest: String? = null,
    ) = GitHubAsset(name, "https://example.test/$name", size, digest)

    private val splits = listOf(asset("flow-armeabi-v7a.apk"), asset("flow-foss-arm64-v8a.apk"), asset("flow-arm64-v8a.apk"))

    @Test
    fun `an arm64 device gets the arm64 github build`() {
        assertThat(selectApk(splits, listOf("arm64-v8a", "armeabi-v7a"))?.name).isEqualTo("flow-arm64-v8a.apk")
    }

    @Test
    fun `an arm32 device gets the armv7 build`() {
        assertThat(selectApk(splits, listOf("armeabi-v7a", "armeabi"))?.name).isEqualTo("flow-armeabi-v7a.apk")
    }

    @Test
    fun `an unpublished abi gets nothing when there is no universal build`() {
        assertThat(selectApk(splits, listOf("x86_64", "x86"))).isNull()
    }

    @Test
    fun `an unpublished abi falls back to the universal build`() {
        assertThat(selectApk(splits + asset("flow.apk"), listOf("x86_64"))?.name).isEqualTo("flow.apk")
    }

    @Test
    fun `a foss build is never picked`() {
        assertThat(selectApk(listOf(asset("flow-foss.apk"), asset("flow.apk")), listOf("arm64-v8a"))?.name).isEqualTo("flow.apk")
    }

    @Test
    fun `versions compare numerically and ignore the prefix and suffix`() {
        assertThat(AppVersions.isNewer("v2.10.0", "2.9.9")).isTrue()
        assertThat(AppVersions.isNewer("v2.2.5", "2.2.5-debug")).isFalse()
        assertThat(AppVersions.isNewer("2.2", "2.2.0")).isFalse()
        assertThat(AppVersions.isNewer("v2.2.1", "2.3.0")).isFalse()
    }

    @Test
    fun `a newer release carries its apk size, digest and date`() {
        val json =
            """
            {"tag_name":"v2.3.0","body":"## New features","html_url":"https://github.com/A-EDev/Flow/releases/tag/v2.3.0",
             "published_at":"2026-09-28T10:00:00Z","draft":false,
             "assets":[{"name":"flow-arm64-v8a.apk","browser_download_url":"https://example.test/a","size":17613193,
                        "digest":"sha256:F900AB"}]}
            """.trimIndent()

        val release = parseGitHubRelease(json).toAppReleaseIfNewer("2.2.5", listOf("arm64-v8a"))

        assertThat(release?.version).isEqualTo("2.3.0")
        assertThat(release?.publishedAt).isEqualTo(Instant.parse("2026-09-28T10:00:00Z"))
        assertThat(release?.apk).isEqualTo(ReleaseApk("flow-arm64-v8a.apk", "https://example.test/a", 17613193, "f900ab"))
    }

    @Test
    fun `the running version is not an update`() {
        val release = GitHubRelease(tagName = "v2.2.5").toAppReleaseIfNewer("2.2.5", listOf("arm64-v8a"))

        assertThat(release).isNull()
    }

    @Test
    fun `a newer nightly run is offered with its own apk`() {
        val nightly = GitHubRelease(tagName = "nightly", assets = listOf(asset("flow-nightly-1430.apk"), asset("checksums.txt")))

        val release = nightly.toNightlyReleaseIfNewer(currentRun = 1428)

        assertThat(release?.version).isEqualTo("nightly.1430")
        assertThat(release?.apk?.name).isEqualTo("flow-nightly-1430.apk")
    }

    @Test
    fun `the running nightly or an older one is not an update`() {
        val nightly = GitHubRelease(tagName = "nightly", assets = listOf(asset("flow-nightly-1428.apk")))

        assertThat(nightly.toNightlyReleaseIfNewer(currentRun = 1428)).isNull()
        assertThat(nightly.toNightlyReleaseIfNewer(currentRun = 1500)).isNull()
    }

    @Test
    fun `a local nightly build never offers an update`() {
        val nightly = GitHubRelease(tagName = "nightly", assets = listOf(asset("flow-nightly-1430.apk")))

        assertThat(nightly.toNightlyReleaseIfNewer(currentRun = 0)).isNull()
    }

    @Test
    fun `a nightly release without a nightly apk offers nothing`() {
        val nightly = GitHubRelease(tagName = "nightly", assets = listOf(asset("flow.apk"), asset("flow-arm64-v8a.apk")))

        assertThat(nightly.toNightlyReleaseIfNewer(currentRun = 1)).isNull()
    }

    @Test
    fun `the stable updater never picks a nightly apk`() {
        assertThat(selectApk(listOf(asset("flow-nightly-1430.apk")), listOf("arm64-v8a"))).isNull()
    }
}
