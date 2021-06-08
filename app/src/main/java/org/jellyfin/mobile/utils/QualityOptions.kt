// Taken and adapted from https://github.com/jellyfin/jellyfin-web/blob/ebb4b050817d4d1920ec28209dee0539410c06ef/src/components/qualityOptions.js

package org.jellyfin.mobile.utils

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import org.jellyfin.mobile.AppPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class QualityOptions(context: Context) : KoinComponent {
    private val appPreferences by inject<AppPreferences>()
    private val connectivityManager: ConnectivityManager by lazy { context.getSystemService()!! }

    /**
     * Get or set the maximum allowed bitrate for video media based on current network state.
     */
    var currentMaxBitrateVideo: Int
        get() = if (connectivityManager.isActiveNetworkMetered) {
            appPreferences.maxBitrateVideoMetered
        } else {
            appPreferences.maxBitrateVideo
        }
        set(value) = if (connectivityManager.isActiveNetworkMetered) {
            appPreferences.maxBitrateVideoMetered = value
        } else {
            appPreferences.maxBitrateVideo = value
        }

    /**
     * Returns a list of video quality options according to their bitrate and aspect ratio.
     *
     * @return List of quality options
     */
    fun getVideoQualityOptions(
        currentMaxBitrate: Int = currentMaxBitrateVideo,
        videoWidth: Int? = null,
        videoHeight: Int? = null
    ): List<QualityOption> {
        var maxAllowedWidth = videoWidth ?: FALLBACK_WIDTH
        val maxAllowedHeight = videoHeight ?: FALLBACK_HEIGHT

        // If the aspect ratio is less than 16/9 (1.77), set the width as if it were pillarboxed.
        // 4:3 1440x1080 -> 1920x1080
        if (maxAllowedWidth / maxAllowedHeight < 16 / 9) {
            maxAllowedWidth = maxAllowedHeight * (16 / 9)
        }

        val qualityOptions = mutableListOf<QualityOption>()
        qualityOptions.add(QualityOption(name = "Auto", bitrate = MAX_BITRATE_VIDEO))

        // Quality options are indexed by bitrate.
        // If you must duplicate them, make sure each of them are unique (by making the last digit a 1)
        if (maxAllowedWidth >= 3800) {
            qualityOptions.add(QualityOption(name = "4K - 120 Mbps", maxHeight = 2160, bitrate = 120_000_000))
            qualityOptions.add(QualityOption(name = "4K - 80 Mbps", maxHeight = 2160, bitrate = 80_000_000))
        }
        // Some 1080- videos are reported as 1912?
        if (maxAllowedWidth >= 1900) {
            qualityOptions.add(QualityOption(name = "1080p - 60 Mbps", maxHeight = 1080, bitrate = 60_000_000))
            qualityOptions.add(QualityOption(name = "1080p - 40 Mbps", maxHeight = 1080, bitrate = 40_000_000))
            qualityOptions.add(QualityOption(name = "1080p - 20 Mbps", maxHeight = 1080, bitrate = 20_000_000))
            qualityOptions.add(QualityOption(name = "1080p - 15 Mbps", maxHeight = 1080, bitrate = 15_000_000))
            qualityOptions.add(QualityOption(name = "1080p - 10 Mbps", maxHeight = 1080, bitrate = 10_000_000))
        }
        if (maxAllowedWidth >= 1260) {
            qualityOptions.add(QualityOption(name = "720p - 8 Mbps", maxHeight = 720, bitrate = 8_000_000))
            qualityOptions.add(QualityOption(name = "720p - 6 Mbps", maxHeight = 720, bitrate = 6_000_000))
            qualityOptions.add(QualityOption(name = "720p - 4 Mbps", maxHeight = 720, bitrate = 4_000_000))
        }
        if (maxAllowedWidth >= 620) {
            qualityOptions.add(QualityOption(name = "480p - 3 Mbps", maxHeight = 480, bitrate = 3_000_000))
            qualityOptions.add(QualityOption(name = "480p - 1.5 Mbps", maxHeight = 480, bitrate = 1_500_000))
            qualityOptions.add(QualityOption(name = "480p - 720 kbps", maxHeight = 480, bitrate = 720_000))
        }

        qualityOptions.add(QualityOption(name = "360p - 420 kbps", maxHeight = 360, bitrate = 420_000))

        // Always select "Auto" if option with current bitrate is not found
        with(qualityOptions.find { it.bitrate == currentMaxBitrate } ?: qualityOptions.first()) {
            selected = true
        }

        return qualityOptions
    }

    /*fun getAudioQualityOptions(
        currentMaxBitrate: Int
    ): List<QualityOption> {
        val qualityOptions = mutableListOf<QualityOption>()

        qualityOptions.add(QualityOption(name = "Auto", bitrate = 1_000_000_000))
        qualityOptions.add(QualityOption(name = "2 Mbps", bitrate = 2_000_000))
        qualityOptions.add(QualityOption(name = "1.5 Mbps", bitrate = 1_500_000))
        qualityOptions.add(QualityOption(name = "1 Mbps", bitrate = 1_000_000))
        qualityOptions.add(QualityOption(name = "320 kbps", bitrate = 320_000))
        qualityOptions.add(QualityOption(name = "256 kbps", bitrate = 256_000))
        qualityOptions.add(QualityOption(name = "192 kbps", bitrate = 192_000))
        qualityOptions.add(QualityOption(name = "128 kbps", bitrate = 128_000))
        qualityOptions.add(QualityOption(name = "96 kbps", bitrate = 96_000))
        qualityOptions.add(QualityOption(name = "64 kbps", bitrate = 64_000))

        with(qualityOptions.find { it.bitrate == currentMaxBitrate } ?: qualityOptions.first()) {
            selected = true
        }

        return qualityOptions
    }*/

    companion object {
        /**
         * Maximum allowed bitrate for video media
         * @return 1 GB/s
         */
        const val MAX_BITRATE_VIDEO = 1_000_000_000

        /**
         * Fallback width if not provided. Update if higher quality is added.
         */
        private const val FALLBACK_WIDTH = 4096

        /**
         * Fallback height if not provided.
         */
        private const val FALLBACK_HEIGHT = 1
    }
}

/**
 * Returns the active quality option from a list of quality options.
 *
 * @return The current selected quality option.
 */
fun List<QualityOption>.selected() = find { it.selected }!!

data class QualityOption(
    val name: String,
    val maxHeight: Int? = null,
    val bitrate: Int,
    var selected: Boolean = false
)
