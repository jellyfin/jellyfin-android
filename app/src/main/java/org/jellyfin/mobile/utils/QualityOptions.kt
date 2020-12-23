// Taken and adapted from https://github.com/jellyfin/jellyfin-web/blob/ebb4b050817d4d1920ec28209dee0539410c06ef/src/components/qualityOptions.js

package org.jellyfin.mobile.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.StringCharacterIterator

class QualityOptions {
    fun getVideoQualityOptions(options: String): String {
        with(JSONObject(options)) {
            return getVideoQualityOptions(
                currentMaxBitrate = optLong("currentMaxBitrate"),
                isAutomaticBitrateEnabled = optBoolean("isAutomaticBitrateEnabled"),
                videoWidth = optInt("videoWidth", 4096),
                videoHeight = optInt("videoHeight"),
                enableAuto = optBoolean("enableAuto")
            ).toJSONString()
        }
    }

    private fun getVideoQualityOptions(
        currentMaxBitrate: Long,
        isAutomaticBitrateEnabled: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        enableAuto: Boolean
    ): List<QualityOption> {
        var width = videoWidth

        // If the aspect ratio is less than 16/9 (1.77), set the width as if it were pillarboxed.
        // 4:3 1440x1080 -> 1920x1080
        if (width / videoHeight < 16 / 9) {
            width = videoHeight * (16 / 9)
        }

        val qualityOptions = mutableListOf<QualityOption>()

        if (enableAuto) {
            qualityOptions.add(QualityOption(name = "Auto", selected = isAutomaticBitrateEnabled))
        }

        // Quality options are indexed by bitrate. If you must duplicate them, make sure each of them are unique (by making the last digit a 1)
        if (width >= 3800) {
            qualityOptions.add(QualityOption(name = "4K - 120 Mbps", maxHeight = 2160, bitrate = 120000000))
            qualityOptions.add(QualityOption(name = "4K - 80 Mbps", maxHeight = 2160, bitrate = 80000000))
        }
        // Some 1080- videos are reported as 1912?
        if (width >= 1900) {
            qualityOptions.add(QualityOption(name = "1080p - 60 Mbps", maxHeight = 1080, bitrate = 60000000))
            qualityOptions.add(QualityOption(name = "1080p - 40 Mbps", maxHeight = 1080, bitrate = 40000000))
            qualityOptions.add(QualityOption(name = "1080p - 20 Mbps", maxHeight = 1080, bitrate = 20000000))
            qualityOptions.add(QualityOption(name = "1080p - 15 Mbps", maxHeight = 1080, bitrate = 15000000))
            qualityOptions.add(QualityOption(name = "1080p - 10 Mbps", maxHeight = 1080, bitrate = 10000000))
        }
        if (width >= 1260) {
            qualityOptions.add(QualityOption(name = "720p - 8 Mbps", maxHeight = 720, bitrate = 8000000))
            qualityOptions.add(QualityOption(name = "720p - 6 Mbps", maxHeight = 720, bitrate = 6000000))
            qualityOptions.add(QualityOption(name = "720p - 4 Mbps", maxHeight = 720, bitrate = 4000000))
        }
        if (width >= 620) {
            qualityOptions.add(QualityOption(name = "480p - 3 Mbps", maxHeight = 480, bitrate = 3000000))
            qualityOptions.add(QualityOption(name = "480p - 1.5 Mbps", maxHeight = 480, bitrate = 1500000))
            qualityOptions.add(QualityOption(name = "480p - 720 kbps", maxHeight = 480, bitrate = 720000))
        }

        qualityOptions.add(QualityOption(name = "360p - 420 kbps", maxHeight = 360, bitrate = 420000))

        if (currentMaxBitrate > 0) {
            var selectedIndex = qualityOptions.lastIndex

            for (index in 0..qualityOptions.size) {
                if (qualityOptions[index].bitrate in 1..currentMaxBitrate) {
                    selectedIndex = index
                    break
                }
            }

            if (!isAutomaticBitrateEnabled) {
                qualityOptions[selectedIndex].selected = true
            }
        }

        return qualityOptions
    }

    /*fun getAudioQualityOptions(options: String): String {
        with(JSONObject(options)) {
            return getAudioQualityOptions(
                currentMaxBitrate = optInt("currentMaxBitrate"),
                isAutomaticBitrateEnabled = optBoolean("isAutomaticBitrateEnabled"),
                enableAuto = optBoolean("enableAuto")
            ).toJSONString()
        }
    }

    private fun getAudioQualityOptions(
        currentMaxBitrate: Int,
        isAutomaticBitrateEnabled: Boolean,
        enableAuto: Boolean
    ): List<QualityOption> {
        val qualityOptions = mutableListOf<QualityOption>()

        if (enableAuto) {
            qualityOptions.add(QualityOption(name = "Auto", selected = isAutomaticBitrateEnabled))
        }

        qualityOptions.add(QualityOption(name = "2 Mbps", bitrate = 2000000))
        qualityOptions.add(QualityOption(name = "1.5 Mbps", bitrate = 1500000))
        qualityOptions.add(QualityOption(name = "1 Mbps", bitrate = 1000000))
        qualityOptions.add(QualityOption(name = "320 kbps", bitrate = 320000))
        qualityOptions.add(QualityOption(name = "256 kbps", bitrate = 256000))
        qualityOptions.add(QualityOption(name = "192 kbps", bitrate = 192000))
        qualityOptions.add(QualityOption(name = "128 kbps", bitrate = 128000))
        qualityOptions.add(QualityOption(name = "96 kbps", bitrate = 96000))
        qualityOptions.add(QualityOption(name = "64 kbps", bitrate = 64000))

        if (currentMaxBitrate > 0) {
            var selectedIndex = qualityOptions.lastIndex

            for (index in 0..qualityOptions.size) {
                if (qualityOptions[index].bitrate in 1..currentMaxBitrate) {
                    selectedIndex = index
                    break
                }
            }

            if (!isAutomaticBitrateEnabled) {
                qualityOptions[selectedIndex].selected = true
            }
        }

        return qualityOptions
    }*/

    private data class QualityOption(
        val name: String,
        val maxHeight: Int? = null,
        val bitrate: Long = 0,
        var selected: Boolean? = null
    )

    private fun QualityOption.toJSONObject(): JSONObject? = try {
        JSONObject().apply {
            put("name", name)
            put("maxHeight", maxHeight)
            put("bitrate", bitrate)
            put("selected", selected)
        }
    } catch (e: JSONException) {
        null
    }

    private fun List<QualityOption>.toJSONString() = try {
        JSONArray().apply {
            for (qualityOption in this@toJSONString) {
                put(qualityOption.toJSONObject())
            }
        }
    } catch (e: JSONException) {
        JSONObject()
    }.toString()
}

fun Long.toHumanBitRate(): String {
    var bits = if (this < 0) 0 else this
    if (bits < 1_000) {
        return "$bits bps"
    }
    val ci = StringCharacterIterator("kMGTPE")
    while (bits >= 1_000_000) {
        bits /= 1_000
        ci.next()
    }
    with(DecimalFormat("0.##")) {
        return "${format(bits / 1_000.0)} ${ci.current()}bps"
    }
}
