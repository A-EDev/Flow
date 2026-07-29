package io.github.aedev.flow.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val version: String,      // e.g., "v1.2.0"
    val changelog: String,    // The release notes
    val downloadUrl: String,  // Link to the .apk or the release page
    val isNewer: Boolean
)

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String
)

object UpdateManager {
    private val client: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder()).build()
    
    // 🔥 CHANGE THIS TO YOUR REPO: "owner/repo"
    private const val GITHUB_REPO = "A-EDev/Flow" 
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: "{}")
            
            // 1. Get Remote Version
            val remoteTag = json.optString("tag_name", "").removePrefix("v").split("-").first()
            val currentTag = currentVersionName.removePrefix("v").split("-").first()

            // 2. Get the APK matching this device, or fall back to the release page.
            val assets = json.optJSONArray("assets")
            val releaseAssets = mutableListOf<ReleaseAsset>()
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        releaseAssets += ReleaseAsset(
                            name = name,
                            downloadUrl = asset.optString("browser_download_url")
                        )
                    }
                }
            }
            val downloadUrl = selectApkDownloadUrl(
                assets = releaseAssets,
                supportedAbis = Build.SUPPORTED_ABIS.asList()
            ) ?: json.optString("html_url")

            // 3. Compare Versions
            if (isNewer(remoteTag, currentTag)) {
                return@withContext UpdateInfo(
                    version = json.optString("tag_name"),
                    changelog = json.optString("body"),
                    downloadUrl = downloadUrl,
                    isNewer = true
                )
            }
            return@withContext null

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Compares two version strings (e.g., "1.2.0" vs "1.1.9").
     * Both strings should already have build-type suffixes stripped (done in checkForUpdate).
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val cleanRemote = remote.split("-").first()
        val cleanCurrent = current.split("-").first()
        val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        
        val length = maxOf(remoteParts.size, currentParts.size)
        
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    internal fun selectApkDownloadUrl(
        assets: List<ReleaseAsset>,
        supportedAbis: List<String>
    ): String? {
        val githubAssets = assets.filterNot {
            it.name.startsWith("flow-foss-", ignoreCase = true)
        }
        val splitAssets = githubAssets.filter {
            it.name.equals("flow-arm64-v8a.apk", ignoreCase = true) ||
                it.name.equals("flow-armeabi-v7a.apk", ignoreCase = true)
        }

        if (splitAssets.isNotEmpty()) {
            val preferredNames = supportedAbis.mapNotNull { abi ->
                when (abi) {
                    "arm64-v8a" -> "flow-arm64-v8a.apk"
                    "armeabi-v7a" -> "flow-armeabi-v7a.apk"
                    else -> null
                }
            }
            return preferredNames.firstNotNullOfOrNull { preferredName ->
                splitAssets.firstOrNull {
                    it.name.equals(preferredName, ignoreCase = true)
                }?.downloadUrl
            }
        }

        return githubAssets.firstOrNull {
            it.name.equals("flow.apk", ignoreCase = true)
        }?.downloadUrl ?: githubAssets.firstOrNull()?.downloadUrl
    }

    // Helper to open browser
    fun triggerDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Could not open browser", e)
        }
    }
}