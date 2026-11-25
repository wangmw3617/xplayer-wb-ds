package com.xplayer.app.player.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import java.util.Locale

data class CodecSupport(
    val label: String,
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
 * 本机硬解体检：仅统计真实硬件加速解码器（过滤软件模拟与别名解码器），
 * 检测 H.264 / HEVC 10-bit / AV1 / VP9 / 杜比视界 / HDR10 / HDR10+ 能力。
 */
object CodecCapabilityProbe {

    fun probe(): CodecReport {
        val hwDecoders = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { info ->
                !info.isEncoder && !info.isAlias && info.isHardwareAccelerated && !info.isSoftwareOnly
            }
        }.getOrDefault(emptyList())

        val byMime = HashMap<String, MutableList<MediaCodecInfo>>(8)
        for (info in hwDecoders) {
            for (rawMime in info.supportedTypes) {
                val lower = rawMime.lowercase(Locale.US)
                byMime.getOrPut(lower) { ArrayList(2) }.add(info)
            }
        }

        val h264Decoders = byMime[MIME_H264].orEmpty()
        val hevcDecoders = byMime[MIME_HEVC].orEmpty()
        val av1Decoders = byMime[MIME_AV1].orEmpty()
        val vp9Decoders = byMime[MIME_VP9].orEmpty()
        val dvDecoders = byMime[MIME_DOLBY_VISION].orEmpty()

        val hevcProfilesByDecoder = hevcDecoders.associateWith { profilesFor(it, MIME_HEVC) }
        val hevcAllProfiles = hevcProfilesByDecoder.values.flatten().toHashSet()
        val av1Profiles = av1Decoders.flatMap { profilesFor(it, MIME_AV1) }.toHashSet()
        val vp9Profiles = vp9Decoders.flatMap { profilesFor(it, MIME_VP9) }.toHashSet()

        val h264 = h264Decoders.firstOrNull()
        val hevc = hevcDecoders.firstOrNull()
        val hevc10 = hevcDecoders.firstOrNull { info ->
            hevcProfilesByDecoder[info].orEmpty().any {
                it == CodecProfileLevel.HEVCProfileMain10 ||
                    it == CodecProfileLevel.HEVCProfileMain10HDR10 ||
                    it == CodecProfileLevel.HEVCProfileMain10HDR10Plus
            }
        }
        val av1 = av1Decoders.firstOrNull()
        val vp9 = vp9Decoders.firstOrNull()
        val dolbyVision = dvDecoders.firstOrNull()

        val hdrFormats = buildList {
            if (CodecProfileLevel.HEVCProfileMain10HDR10 in hevcAllProfiles) add("HDR10")
            if (CodecProfileLevel.HEVCProfileMain10HDR10Plus in hevcAllProfiles) add("HDR10+")
            if (CodecProfileLevel.AV1ProfileMain10HDR10 in av1Profiles ||
                CodecProfileLevel.AV1ProfileMain10HDR10Plus in av1Profiles
            ) {
                add("HDR10 (AV1)")
            }
            if (CodecProfileLevel.VP9Profile2HDR in vp9Profiles ||
                CodecProfileLevel.VP9Profile2HDR10Plus in vp9Profiles ||
                CodecProfileLevel.VP9Profile3HDR in vp9Profiles ||
                CodecProfileLevel.VP9Profile3HDR10Plus in vp9Profiles
            ) {
                add("HDR (VP9)")
            }
            if (dolbyVision != null) add("Dolby Vision")
        }.distinct()

        val dvProfiles = if (dolbyVision == null) {
            emptyList()
        } else {
            dvDecoders.flatMap { profilesFor(it, MIME_DOLBY_VISION) }
                .mapNotNull(::dvProfileLabel)
                .distinct()
        }

        return CodecReport(
            chipset = chipsetName(),
            socModel = socModel(),
            codecs = listOf(
                CodecSupport("H.264", h264 != null, h264?.name),
                CodecSupport("HEVC", hevc != null, hevc?.name),
                CodecSupport("HEVC (10-bit)", hevc10 != null, hevc10?.name),
                CodecSupport("AV1", av1 != null, av1?.name),
                CodecSupport("VP9", vp9 != null, vp9?.name),
                CodecSupport("Dolby Vision", dolbyVision != null, dolbyVision?.name),
            ),
            hdrFormats = hdrFormats,
            dolbyVisionProfiles = dvProfiles,
            decoders = hwDecoders.map { it.name }.distinct().sorted(),
        )
    }

    private fun profilesFor(info: MediaCodecInfo, mime: String): List<Int> = runCatching {
        info.getCapabilitiesForType(mime).profileLevels.map { it.profile }
    }.getOrDefault(emptyList())

    private fun dvProfileLabel(profile: Int): String? = when (profile) {
        CodecProfileLevel.DolbyVisionProfileDvavPer -> "0"
        CodecProfileLevel.DolbyVisionProfileDvavPen -> "1"
        CodecProfileLevel.DolbyVisionProfileDvheDer -> "2"
        CodecProfileLevel.DolbyVisionProfileDvheDen -> "3"
        CodecProfileLevel.DolbyVisionProfileDvheDtr -> "4"
        CodecProfileLevel.DolbyVisionProfileDvheStn -> "5"
        CodecProfileLevel.DolbyVisionProfileDvheDth -> "6"
        CodecProfileLevel.DolbyVisionProfileDvheDtb -> "7"
        CodecProfileLevel.DolbyVisionProfileDvheSt -> "8"
        CodecProfileLevel.DolbyVisionProfileDvavSe -> "9"
        else -> null
    }

    private fun socModel(): String {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        } else {
            null
        } ?: Build.HARDWARE
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun chipsetName(): String {
        val manufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
                ?: Build.MANUFACTURER
        } else {
            Build.MANUFACTURER
        }
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val family = when {
            hardware.contains("qcom") || hardware.contains("qualcomm") || QCOM_SM_REGEX.containsMatchIn(hardware) ||
                hardware.contains("kona") || hardware.contains("taro") || hardware.contains("kalama") ||
                hardware.contains("lahaina") || hardware.contains("pineapple") -> "Snapdragon"
            hardware.contains("mt") || hardware.contains("dimensity") -> "Dimensity / MediaTek"
            hardware.contains("kirin") || hardware.contains("hi3") -> "Kirin"
            hardware.contains("exynos") || hardware.contains("universal") || hardware.contains("smdk") -> "Exynos"
            hardware.contains("tensor") -> "Tensor"
            else -> manufacturer
        }
        return "$family · $hardware"
    }

    private val QCOM_SM_REGEX = Regex("""(^|[_-])sm\d""")
    private const val MIME_H264 = "video/avc"
    private const val MIME_HEVC = "video/hevc"
    private const val MIME_AV1 = "video/av01"
    private const val MIME_VP9 = "video/x-vnd.on2.vp9"
    private const val MIME_DOLBY_VISION = "video/dolby-vision"
}
