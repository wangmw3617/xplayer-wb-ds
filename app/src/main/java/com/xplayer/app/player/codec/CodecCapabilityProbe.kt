package com.xplayer.app.player.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import java.util.Locale

data class CodecSupport(
    val label: String,
    val mime: String,
    val supported: Boolean,
    val decoder: String? = null,
)

data class CodecReport(
    val chipset: String,
    val socModel: String,
    val codecs: List<CodecSupport>,
    val hdrFormats: List<String>,
    val dolbyVisionProfiles: List<String>,
    val decoders: List<String>,
)

/**
 * 本机硬解体检：直接读取 MediaCodecList，统计 H.264 / HEVC 10-bit / AV1 / VP9 /
 * 杜比视界 / HDR10 / HDR10+ 的硬件解码支持情况与解码器名称。
 */
object CodecCapabilityProbe {

    fun probe(): CodecReport {
        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        }.getOrDefault(emptyList())

        fun decodersFor(mime: String): List<MediaCodecInfo> =
            codecInfos.filter { info -> info.supportedTypes.any { it.equals(mime, true) } }

        fun firstDecoder(mime: String): MediaCodecInfo? = decodersFor(mime).firstOrNull()

        val h264 = firstDecoder(MIME_H264)
        val hevc = firstDecoder(MIME_HEVC)
        val hevc10 = decodersFor(MIME_HEVC).firstOrNull { info ->
            hevcProfiles(info).any {
                it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                    it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                    it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            }
        }
        val av1 = firstDecoder(MIME_AV1)
        val vp9 = firstDecoder(MIME_VP9)
        val dolbyVision = firstDecoder(MIME_DOLBY_VISION)

        val hdrFormats = buildList {
            val hevcProfileList = decodersFor(MIME_HEVC).flatMap { hevcProfiles(it) }
            if (hevcProfileList.contains(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10)) add("HDR10")
            if (hevcProfileList.contains(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)) add("HDR10+")
            if (decodersFor(MIME_AV1).isNotEmpty()) add("HDR10 (AV1)")
            if (dolbyVision != null) add("Dolby Vision")
        }.distinct()

        val dvProfiles = if (dolbyVision == null) {
            emptyList()
        } else {
            runCatching {
                dolbyVision.getCapabilitiesForType(MIME_DOLBY_VISION).profileLevels
                    .map { profileLabel(it.profile) }
                    .distinct()
            }.getOrDefault(listOf("5 / 8"))
        }

        return CodecReport(
            chipset = chipsetName(),
            socModel = socModel(),
            codecs = listOf(
                CodecSupport("H.264", MIME_H264, h264 != null, h264?.name),
                CodecSupport("HEVC", MIME_HEVC, hevc != null, hevc?.name),
                CodecSupport("HEVC (10-bit)", MIME_HEVC, hevc10 != null, hevc10?.name),
                CodecSupport("AV1", MIME_AV1, av1 != null, av1?.name),
                CodecSupport("VP9", MIME_VP9, vp9 != null, vp9?.name),
                CodecSupport("Dolby Vision", MIME_DOLBY_VISION, dolbyVision != null, dolbyVision?.name),
            ),
            hdrFormats = hdrFormats,
            dolbyVisionProfiles = dvProfiles,
            decoders = codecInfos.map { it.name }.distinct().sorted(),
        )
    }

    private fun hevcProfiles(info: MediaCodecInfo): List<Int> = runCatching {
        info.getCapabilitiesForType(MIME_HEVC).profileLevels.map { it.profile }
    }.getOrDefault(emptyList())

    /** 杜比视界 profile 编码的近似映射（不同平台上报值略有差异，仅作参考） */
    private fun profileLabel(profile: Int): String = when (profile) {
        0x100, 0x101 -> "5"
        0x200 -> "7"
        0x201, 0x202 -> "8"
        0x400, 0x401 -> "8.4"
        else -> profile.toString()
    }

    private fun socModel(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    } else {
        Build.HARDWARE
    }

    private fun chipsetName(): String {
        val manufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else Build.MANUFACTURER
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val family = when {
            hardware.contains("qcom") || hardware.contains("sm") || hardware.contains("kona") ||
                hardware.contains("taro") || hardware.contains("kalama") -> "Snapdragon"
            hardware.contains("mt") || hardware.contains("dimensity") -> "Dimensity / MediaTek"
            hardware.contains("kirin") || hardware.contains("hi3") -> "Kirin"
            hardware.contains("exynos") || hardware.contains("universal") -> "Exynos"
            hardware.contains("tensor") -> "Tensor"
            else -> manufacturer
        }
        return "$family · $hardware"
    }

    private const val MIME_H264 = "video/avc"
    private const val MIME_HEVC = "video/hevc"
    private const val MIME_AV1 = "video/av01"
    private const val MIME_VP9 = "video/x-vnd.on2.vp9"
    private const val MIME_DOLBY_VISION = "video/dolby-vision"
}
