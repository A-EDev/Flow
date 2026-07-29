package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateManagerTest {

    private val splitAssets = listOf(
        ReleaseAsset("flow-armeabi-v7a.apk", "https://example.test/armv7"),
        ReleaseAsset("flow-foss-arm64-v8a.apk", "https://example.test/foss-arm64"),
        ReleaseAsset("flow-arm64-v8a.apk", "https://example.test/arm64")
    )

    @Test
    fun selectApkDownloadUrl_arm64Device_selectsArm64GithubApk() {
        val url = UpdateManager.selectApkDownloadUrl(
            assets = splitAssets,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )

        assertThat(url).isEqualTo("https://example.test/arm64")
    }

    @Test
    fun selectApkDownloadUrl_arm32Device_selectsArmv7GithubApk() {
        val url = UpdateManager.selectApkDownloadUrl(
            assets = splitAssets,
            supportedAbis = listOf("armeabi-v7a", "armeabi")
        )

        assertThat(url).isEqualTo("https://example.test/armv7")
    }

    @Test
    fun selectApkDownloadUrl_unpublishedAbi_returnsNull() {
        val url = UpdateManager.selectApkDownloadUrl(
            assets = splitAssets,
            supportedAbis = listOf("x86_64", "x86")
        )

        assertThat(url).isNull()
    }

    @Test
    fun selectApkDownloadUrl_legacyRelease_prefersGithubApk() {
        val url = UpdateManager.selectApkDownloadUrl(
            assets = listOf(
                ReleaseAsset("flow-foss.apk", "https://example.test/foss"),
                ReleaseAsset("flow.apk", "https://example.test/github")
            ),
            supportedAbis = listOf("arm64-v8a")
        )

        assertThat(url).isEqualTo("https://example.test/github")
    }
}
