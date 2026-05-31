package org.jellyfin.mobile.player.deviceprofile

internal data class ExoPlayerContainerSupport(
    val container: String,
    val videoCodecs: List<String>,
    val audioCodecs: List<String>,
)

internal object ExoPlayerDirectPlayProfile {
    val pcmCodecs = listOf(
        "pcm_s8",
        "pcm_s16be",
        "pcm_s16le",
        "pcm_s24le",
        "pcm_s32le",
        "pcm_f32le",
        "pcm_alaw",
        "pcm_mulaw",
    )

    val mpegTsContainers = listOf("mpegts", "ts")
    val mpegTsVideoCodecs = listOf("mpeg1video", "mpeg2video", "mpeg4", "h264", "hevc")
    val mpegTsAudioCodecs = pcmCodecs + listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd")
    val containers = listOf(
        ExoPlayerContainerSupport(
            container = "mp4",
            videoCodecs = listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
            audioCodecs = listOf("mp1", "mp2", "mp3", "aac", "alac", "ac3", "opus"),
        ),
        ExoPlayerContainerSupport(
            container = "fmp4",
            videoCodecs = listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
            audioCodecs = listOf("mp3", "aac", "ac3", "eac3"),
        ),
        ExoPlayerContainerSupport(
            container = "webm",
            videoCodecs = listOf("vp8", "vp9", "av1"),
            audioCodecs = listOf("vorbis", "opus"),
        ),
        ExoPlayerContainerSupport(
            container = "mkv",
            videoCodecs = listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp8", "vp9"),
            audioCodecs = pcmCodecs + listOf("mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd"),
        ),
        ExoPlayerContainerSupport(
            container = "mp3",
            videoCodecs = emptyList(),
            audioCodecs = listOf("mp3"),
        ),
        ExoPlayerContainerSupport(
            container = "ogg",
            videoCodecs = emptyList(),
            audioCodecs = listOf("vorbis", "opus", "flac"),
        ),
        ExoPlayerContainerSupport(
            container = "wav",
            videoCodecs = emptyList(),
            audioCodecs = pcmCodecs,
        ),
        ExoPlayerContainerSupport(
            container = "mpegts",
            videoCodecs = mpegTsVideoCodecs,
            audioCodecs = mpegTsAudioCodecs,
        ),
        ExoPlayerContainerSupport(
            container = "ts",
            videoCodecs = mpegTsVideoCodecs,
            audioCodecs = mpegTsAudioCodecs,
        ),
        ExoPlayerContainerSupport(
            container = "flv",
            videoCodecs = listOf("mpeg4", "h264"),
            audioCodecs = listOf("mp3", "aac"),
        ),
        ExoPlayerContainerSupport(
            container = "aac",
            videoCodecs = emptyList(),
            audioCodecs = listOf("aac"),
        ),
        ExoPlayerContainerSupport(
            container = "flac",
            videoCodecs = emptyList(),
            audioCodecs = listOf("flac"),
        ),
        ExoPlayerContainerSupport(
            container = "3gp",
            videoCodecs = listOf("h263", "mpeg4", "h264", "hevc"),
            audioCodecs = listOf("3gpp", "aac", "flac"),
        ),
    )

    val forcedAudioCodecs = pcmCodecs + listOf(
        "mp1",
        "mp2",
        "mp3",
        "alac",
        "aac",
        "ac3",
        "eac3",
        "dts",
        "mlp",
        "truehd",
    )

    fun isMpegTsContainer(container: String?): Boolean = container
        ?.split(',', '|')
        ?.any { value -> value.trim().lowercase() in mpegTsContainers } == true
}
