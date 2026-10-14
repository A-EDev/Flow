package io.github.aedev.flow.utils

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.view.Display
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoCodec
import kotlin.math.roundToInt

/** One labelled fact about the device. */
@Immutable
data class DeviceFact(
    val label: String,
    val value: String,
)

/** A titled group of [DeviceFact]s, such as the display or the video decoders. */
@Immutable
data class DeviceFactGroup(
    @StringRes val title: Int,
    val facts: List<DeviceFact>,
)

/**
 * What the About page tells about this device: the hardware, the system, the display, which video
 * formats decode in hardware (the reason a codec plays smoothly or not), and this build of Flow.
 * Enumerates the codec list and reads storage, so call it off the main thread.
 */
object DeviceDetails {
    fun collect(context: Context): List<DeviceFactGroup> {
        val unknown = context.getString(R.string.unknown)
        return listOf(
            DeviceFactGroup(R.string.device_info_section_device, device(context, unknown)),
            DeviceFactGroup(R.string.device_info_section_system, system(context, unknown)),
            DeviceFactGroup(R.string.device_info_section_display, display(context, unknown)),
            DeviceFactGroup(R.string.device_info_section_media, decoders(context)),
            DeviceFactGroup(R.string.device_info_section_app, app(context, unknown)),
        )
    }

    /** The groups as plain text, for the clipboard. */
    fun asText(
        context: Context,
        groups: List<DeviceFactGroup>,
    ): String =
        groups.joinToString("\n\n") { group ->
            (listOf(context.getString(group.title)) + group.facts.map { "${it.label}: ${it.value}" }).joinToString("\n")
        }

    private fun device(
        context: Context,
        unknown: String,
    ): List<DeviceFact> {
        val memory =
            ActivityManager.MemoryInfo().also { info ->
                context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            }
        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val chipset =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ")
            } else {
                Build.HARDWARE
            }
        return listOf(
            DeviceFact(context.getString(R.string.device_info_model), "${Build.MANUFACTURER} ${Build.MODEL}"),
            DeviceFact(context.getString(R.string.device_info_brand), Build.BRAND),
            DeviceFact(context.getString(R.string.device_info_codename), Build.DEVICE),
            DeviceFact(context.getString(R.string.device_info_chipset), chipset.ifBlank { unknown }),
            DeviceFact(context.getString(R.string.device_info_abis), Build.SUPPORTED_ABIS.joinToString(", ")),
            DeviceFact(
                context.getString(R.string.device_info_memory),
                if (memory.totalMem > 0) Formatter.formatShortFileSize(context, memory.totalMem) else unknown,
            ),
            DeviceFact(
                context.getString(R.string.device_info_storage),
                storage?.let {
                    context.getString(
                        R.string.settings_storage_usage,
                        Formatter.formatShortFileSize(context, it.availableBytes),
                        Formatter.formatShortFileSize(context, it.totalBytes),
                    )
                } ?: unknown,
            ),
        )
    }

    private fun system(
        context: Context,
        unknown: String,
    ): List<DeviceFact> =
        listOf(
            DeviceFact(
                context.getString(R.string.device_info_android),
                context.getString(R.string.device_info_android_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            ),
            DeviceFact(context.getString(R.string.device_info_security_patch), Build.VERSION.SECURITY_PATCH.ifBlank { unknown }),
            DeviceFact(context.getString(R.string.device_info_build), Build.DISPLAY),
            DeviceFact(context.getString(R.string.device_info_kernel), System.getProperty("os.version") ?: unknown),
        )

    private fun display(
        context: Context,
        unknown: String,
    ): List<DeviceFact> {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val mode = display?.mode
        val supported = context.getString(R.string.device_info_supported)
        val notSupported = context.getString(R.string.device_info_not_supported)
        return listOf(
            DeviceFact(
                context.getString(R.string.device_info_resolution),
                mode?.let { context.getString(R.string.device_info_resolution_value, it.physicalWidth, it.physicalHeight) } ?: unknown,
            ),
            DeviceFact(
                context.getString(R.string.device_info_density),
                context.getString(R.string.device_info_density_value, metrics.densityDpi),
            ),
            DeviceFact(
                context.getString(R.string.device_info_refresh_rate),
                display?.let { context.getString(R.string.device_info_refresh_rate_value, it.refreshRate.roundToInt()) } ?: unknown,
            ),
            DeviceFact(context.getString(R.string.device_info_hdr), if (display?.isHdr == true) supported else notSupported),
        )
    }

    private fun decoders(context: Context): List<DeviceFact> {
        val codecs =
            runCatching {
                MediaCodecList(
                    MediaCodecList.REGULAR_CODECS,
                ).codecInfos.filterNot { it.isEncoder }
            }.getOrDefault(emptyList())

        fun support(mime: String): String {
            val matching = codecs.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            return context.getString(
                when {
                    matching.isEmpty() -> R.string.device_info_not_supported
                    matching.any { isHardware(it) } -> R.string.device_info_hardware
                    else -> R.string.device_info_software
                },
            )
        }
        return listOf(
            DeviceFact(VideoCodec.AV1.label, support(MediaFormat.MIMETYPE_VIDEO_AV1)),
            DeviceFact(VideoCodec.VP9.label, support(MediaFormat.MIMETYPE_VIDEO_VP9)),
            DeviceFact(context.getString(R.string.codec_name_hevc), support(MediaFormat.MIMETYPE_VIDEO_HEVC)),
            DeviceFact(VideoCodec.H264.label, support(MediaFormat.MIMETYPE_VIDEO_AVC)),
        )
    }

    private fun isHardware(info: android.media.MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
        }

    private fun app(
        context: Context,
        unknown: String,
    ): List<DeviceFact> {
        val installer =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
            }.getOrNull()
        return listOf(
            DeviceFact(
                context.getString(R.string.device_info_version),
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            ),
            DeviceFact(context.getString(R.string.device_info_build_type), "${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE}"),
            DeviceFact(context.getString(R.string.device_info_installer), installer ?: unknown),
            DeviceFact(context.getString(R.string.device_info_package), context.packageName),
        )
    }
}
