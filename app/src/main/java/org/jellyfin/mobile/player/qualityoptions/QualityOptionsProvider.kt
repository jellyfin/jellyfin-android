package org.jellyfin.mobile.player.qualityoptions

import android.util.Rational
import org.jellyfin.mobile.utils.Constants

class QualityOptionsProvider {

    private val defaultQualityOptions = listOf(
        QualityOption(maxHeight = 2160, bitrate = 120000000),
        QualityOption(maxHeight = 2160, bitrate = 80000000),
        QualityOption(maxHeight = 1080, bitrate = 60000000),
        QualityOption(maxHeight = 1080, bitrate = 40000000),
        QualityOption(maxHeight = 1080, bitrate = 20000000),
        QualityOption(maxHeight = 1080, bitrate = 15000000),
        QualityOption(maxHeight = 1080, bitrate = 10000000),
        QualityOption(maxHeight = 720, bitrate = 8000000),
        QualityOption(maxHeight = 720, bitrate = 6000000),
        QualityOption(maxHeight = 720, bitrate = 4000000),
        QualityOption(maxHeight = 480, bitrate = 3000000),
        QualityOption(maxHeight = 480, bitrate = 1500000),
        QualityOption(maxHeight = 480, bitrate = 720000),
        QualityOption(maxHeight = 360, bitrate = 420000),
        QualityOption(maxHeight = 0, bitrate = 0), // auto
    )

    @Suppress("MagicNumber")
    fun getApplicableQualityOptions(videoWidth: Int, videoHeight: Int): List<QualityOption> {
        // If the aspect ratio is less than 16/9, set the width as if it were pillarboxed
        // i.e. 4:3 1440x1080 -> 1920x1080
        val maxAllowedWidth = when {
            Rational(videoWidth, videoHeight) < Constants.ASPECT_RATIO_16_9 -> videoHeight * 16 / 9
            else -> videoWidth
        }

        val maxAllowedHeight = when {
            maxAllowedWidth >= 3800 -> 2160
            // Some 1080p videos are apparently reported as 1912
            maxAllowedWidth >= 1900 -> 1080
            maxAllowedWidth >= 1260 -> 720
            maxAllowedWidth >= 620 -> 480
            else -> 360
        }

        return defaultQualityOptions.takeLastWhile { option -> option.maxHeight <= maxAllowedHeight }
    }
}
