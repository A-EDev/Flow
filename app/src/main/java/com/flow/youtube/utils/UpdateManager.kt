package com.flow.youtube.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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

object UpdateManager {
    private val client = OkHttpClient()
    
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

            // 2. Get Download URL (Prioritize APK asset, fallback to browser link)
            val assets = json.optJSONArray("assets")
            var downloadUrl = json.optString("html_url") // Default to GitHub page
            
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

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