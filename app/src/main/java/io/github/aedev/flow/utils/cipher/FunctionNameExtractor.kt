package io.github.aedev.flow.utils.cipher

import android.util.Log
import java.security.MessageDigest

/**
 * This is based and ported from Metrolist,
 * see https://github.com/MetrolistGroup/Metrolist for the original code and license.
 * 
 * Extracts cipher function names from YouTube's player.js
 *
 * Handles both legacy patterns and modern Q-array obfuscation (2025+).
 * Falls back to hardcoded configs for known player.js hashes when regex fails.
 */
object FunctionNameExtractor {
    private const val TAG = "Flow_CipherFnExtract"

    // ==================== DATA CLASSES ====================

    data class SigFunctionInfo(
        val name: String,
        val constantArg: Int?,
        val constantArgs: List<Int>? = null,
        val preprocessFunc: String? = null,
        val preprocessArgs: List<Int>? = null,
        val isHardcoded: Boolean = false
    )

    data class NFunctionInfo(
        val name: String,
        val arrayIndex: Int?,
        val constantArgs: List<Int>? = null,
        val isHardcoded: Boolean = false
    )

    data class HardcodedPlayerConfig(
        val sigFuncName: String,
        val sigConstantArg: Int?,
        val sigConstantArgs: List<Int>? = null,
        val sigPreprocessFunc: String? = null,
        val sigPreprocessArgs: List<Int>? = null,
        val nFuncName: String,
        val nArrayIndex: Int?,
        val nConstantArgs: List<Int>?,
        val signatureTimestamp: Int
    )

    // ==================== KNOWN PLAYER CONFIGS ====================

    private val KNOWN_PLAYER_CONFIGS = mapOf(
        "74edf1a3" to HardcodedPlayerConfig(
            sigFuncName = "JI",
            sigConstantArg = 48,
            sigConstantArgs = listOf(48, 1918),
            sigPreprocessFunc = "f1",
            sigPreprocessArgs = listOf(1, 6528),
            nFuncName = "GU",
            nArrayIndex = null,
            nConstantArgs = listOf(6, 6010),
            signatureTimestamp = 20522
        )
    )

    // ==================== DETECTION PATTERNS ====================

    private val Q_ARRAY_PATTERN = Regex("""var\s+Q\s*=\s*"[^"]+"\s*\.\s*split\s*\(\s*"\}"\s*\)""")

    private val PLAYER_HASH_PATTERNS = listOf(
        Regex("""jsUrl['":\s]+[^"']*?/player/([a-f0-9]{8})/"""),
        Regex("""player_ias\.vflset/[^/]+/([a-f0-9]{8})/"""),
        Regex("""/s/player/([a-f0-9]{8})/""")
    )

    private val SIG_FUNCTION_PATTERNS = listOf(
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)"""),
        Regex("""\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\bm=([a-zA-Z0-9${'$'}]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc\s*&&\s*d\.set\([^,]+\s*,\s*(?:encodeURIComponent\s*\()([a-zA-Z0-9$]+)\("""),
        Regex("""\bc\s*&&\s*[a-z]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
    )

    private val N_FUNCTION_PATTERNS = listOf(
        Regex("""\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)"""),
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)"""),
        Regex("""\(\s*([a-zA-Z0-9$]+)\s*=\s*String\.fromCharCode\(110\)"""),
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\([a-zA-Z0-9]\)\s*\{[^}]*?enhanced_except_"""),
    )

    // ==================== EXTRACTION FUNCTIONS ====================

    fun hasQArrayObfuscation(playerJs: String): Boolean {
        val hasQArray = Q_ARRAY_PATTERN.containsMatchIn(playerJs)
        Log.d(TAG, "Q-array obfuscation check: hasQArray=$hasQArray")
        return hasQArray
    }

    fun extractPlayerHash(playerJs: String): String? {
        Log.d(TAG, "Extracting player hash from playerJs (${playerJs.length} chars)")
        for ((index, pattern) in PLAYER_HASH_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val hash = match.groupValues[1]
                Log.d(TAG, "Player hash found via pattern $index: $hash")
                return hash
            }
        }
        val contentToHash = playerJs.take(10000)
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(contentToHash.toByteArray())
        val computedHash = digest.take(4).joinToString("") { "%02x".format(it) }
        Log.d(TAG, "Player hash computed from content: $computedHash")
        return computedHash
    }

    fun getHardcodedConfig(playerHash: String): HardcodedPlayerConfig? {
        val config = KNOWN_PLAYER_CONFIGS[playerHash]
        if (config != null) {
            Log.d(TAG, "Found hardcoded config for hash $playerHash")
        } else {
            Log.w(TAG, "No hardcoded config for hash: $playerHash. Known: ${KNOWN_PLAYER_CONFIGS.keys.joinToString()}")
        }
        return config
    }

    fun extractSigFunctionInfo(playerJs: String, knownHash: String? = null): SigFunctionInfo? {
        Log.d(TAG, "Extracting sig function info (playerJs=${playerJs.length} chars)")
        for ((index, pattern) in SIG_FUNCTION_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val name = match.groupValues[1]
                val constArg = if (match.groupValues.size > 2) match.groupValues[2].toIntOrNull() else null
                Log.d(TAG, "SIG FUNCTION FOUND via pattern $index: name=$name, constantArg=$constArg")
                return SigFunctionInfo(name, constArg, isHardcoded = false)
            }
        }
        Log.w(TAG, "No sig pattern matched, checking Q-array...")
        if (hasQArrayObfuscation(playerJs)) {
            val hashToUse = knownHash ?: extractPlayerHash(playerJs)
            if (hashToUse != null) {
                val config = getHardcodedConfig(hashToUse)
                if (config != null) {
                    Log.d(TAG, "Using hardcoded sig function: ${config.sigFuncName}")
                    return SigFunctionInfo(
                        name = config.sigFuncName,
                        constantArg = config.sigConstantArg,
                        constantArgs = config.sigConstantArgs,
                        preprocessFunc = config.sigPreprocessFunc,
                        preprocessArgs = config.sigPreprocessArgs,
                        isHardcoded = true
                    )
                }
            }
        }
        Log.e(TAG, "Could not find signature deobfuscation function")
        return null
    }

    fun extractNFunctionInfo(playerJs: String, knownHash: String? = null): NFunctionInfo? {
        Log.d(TAG, "Extracting n-function info (playerJs=${playerJs.length} chars)")
        for ((index, pattern) in N_FUNCTION_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                return when (index) {
                    0 -> {
                        val name = match.groupValues[1]
                        val arrayIdx = match.groupValues[2].toIntOrNull()
                        Log.d(TAG, "N-FUNCTION via pattern $index: name=$name, arrayIndex=$arrayIdx")
                        NFunctionInfo(name, arrayIdx, isHardcoded = false)
                    }
                    1 -> {
                        val name = match.groupValues[2]
                        val arrayIdx = match.groupValues[3].toIntOrNull()
                        Log.d(TAG, "N-FUNCTION via pattern $index: name=$name, arrayIndex=$arrayIdx")
                        NFunctionInfo(name, arrayIdx, isHardcoded = false)
                    }
                    else -> {
                        val name = match.groupValues[1]
                        Log.d(TAG, "N-FUNCTION via pattern $index: name=$name")
                        NFunctionInfo(name, null, isHardcoded = false)
                    }
                }
            }
        }
        Log.w(TAG, "No n-func pattern matched, checking Q-array...")
        if (hasQArrayObfuscation(playerJs)) {
            val hashToUse = knownHash ?: extractPlayerHash(playerJs)
            if (hashToUse != null) {
                val config = getHardcodedConfig(hashToUse)
                if (config != null) {
                    Log.d(TAG, "Using hardcoded n-function: ${config.nFuncName}[${config.nArrayIndex}]")
                    return NFunctionInfo(config.nFuncName, config.nArrayIndex, config.nConstantArgs, isHardcoded = true)
                }
            }
        }
        Log.e(TAG, "Could not find n-transform function")
        return null
    }

    fun extractSignatureTimestamp(playerJs: String): Int? {
        val patterns = listOf(
            Regex("""signatureTimestamp['":\s]+(\d+)"""),
            Regex("""sts['":\s]+(\d+)"""),
            Regex(""""signatureTimestamp"\s*:\s*(\d+)""")
        )
        for ((index, pattern) in patterns.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val sts = match.groupValues[1].toIntOrNull()
                if (sts != null) {
                    Log.d(TAG, "signatureTimestamp found via pattern $index: $sts")
                    return sts
                }
            }
        }
        val playerHash = extractPlayerHash(playerJs)
        if (playerHash != null) {
            val config = getHardcodedConfig(playerHash)
            if (config != null) {
                Log.d(TAG, "Using hardcoded signatureTimestamp: ${config.signatureTimestamp}")
                return config.signatureTimestamp
            }
        }
        Log.w(TAG, "Could not extract signatureTimestamp")
        return null
    }

    fun analyzePlayerJs(playerJs: String, knownHash: String? = null): PlayerAnalysis {
        Log.d(TAG, "=== PLAYER.JS CIPHER ANALYSIS ===")
        val playerHash = knownHash ?: extractPlayerHash(playerJs)
        val hasQArray = hasQArrayObfuscation(playerJs)
        val sigInfo = extractSigFunctionInfo(playerJs, playerHash)
        val nFuncInfo = extractNFunctionInfo(playerJs, playerHash)
        val signatureTimestamp = extractSignatureTimestamp(playerJs)

        Log.d(TAG, "Hash=$playerHash, Q-array=$hasQArray, sig=${sigInfo?.name}, nFunc=${nFuncInfo?.name}, sts=$signatureTimestamp")
        return PlayerAnalysis(playerHash, hasQArray, sigInfo, nFuncInfo, signatureTimestamp)
    }

    data class PlayerAnalysis(
        val playerHash: String?,
        val hasQArrayObfuscation: Boolean,
        val sigInfo: SigFunctionInfo?,
        val nFuncInfo: NFunctionInfo?,
        val signatureTimestamp: Int?
    )
}
