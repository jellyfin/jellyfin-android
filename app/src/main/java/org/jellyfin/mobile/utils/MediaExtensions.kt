@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import com.google.android.exoplayer2.audio.AudioAttributes as ExoPlayerAudioAttributes

inline fun MediaSession.applyDefaultLocalAudioAttributes(contentType: Int) {
    val audioAttributes = AudioAttributes.Builder().apply {
        setUsage(AudioAttributes.USAGE_MEDIA)
        setContentType(contentType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        }
    }.build()
    setPlaybackToLocal(audioAttributes)
}

fun JellyfinMediaSource.toMediaMetadata(): MediaMetadata = MediaMetadata.Builder().apply {
    putString(MediaMetadata.METADATA_KEY_MEDIA_ID, itemId.toString())
    putString(MediaMetadata.METADATA_KEY_TITLE, name)
    putLong(MediaMetadata.METADATA_KEY_DURATION, runTimeMs)
}.build()

fun MediaSession.setPlaybackState(playbackState: Int, position: Long, playbackActions: Long) {
    val state = PlaybackState.Builder().apply {
        setState(playbackState, position, 1.0f)
        setActions(playbackActions)
    }.build()
    setPlaybackState(state)
}

fun MediaSession.setPlaybackState(isPlaying: Boolean, position: Long, playbackActions: Long) {
    setPlaybackState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position, playbackActions)
}

fun MediaSession.setPlaybackState(player: Player, playbackActions: Long) {
    val playbackState = when (val playerState = player.playbackState) {
        Player.STATE_IDLE, Player.STATE_ENDED -> PlaybackState.STATE_NONE
        Player.STATE_READY -> if (player.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
        else -> throw IllegalStateException("Invalid player playbackState $playerState")
    }
    setPlaybackState(playbackState, player.currentPosition, playbackActions)
}

fun AudioManager.getVolumeRange(streamType: Int): IntRange {
    val minVolume = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getStreamMinVolume(streamType) else 0)
    val maxVolume = getStreamMaxVolume(streamType)
    return minVolume..maxVolume
}

fun AudioManager.getVolumeLevelPercent(): Int {
    val stream = AudioManager.STREAM_MUSIC
    val volumeRange = getVolumeRange(stream)
    val currentVolume = getStreamVolume(stream)
    return (currentVolume - volumeRange.first) * Constants.PERCENT_MAX / volumeRange.width
}

/**
 * Set ExoPlayer [ExoPlayerAudioAttributes], make ExoPlayer handle audio focus
 */
inline fun SimpleExoPlayer.applyDefaultAudioAttributes(@C.AudioContentType contentType: Int) {
    val audioAttributes = ExoPlayerAudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(contentType)
        .build()
    setAudioAttributes(audioAttributes, true)
}

/**
 * Get the index of the first renderer with the specified [type]
 */
fun ExoPlayer.getRendererIndexByType(type: Int): Int {
    for (i in 0 until rendererCount) {
        if (getRendererType(i) == type) return i
    }
    return -1
}

fun ExoPlayer.seekToOffset(offsetMs: Long) {
    var positionMs = currentPosition + offsetMs
    val durationMs = duration
    if (durationMs != C.TIME_UNSET) {
        positionMs = positionMs.coerceAtMost(durationMs)
    }
    positionMs = positionMs.coerceAtLeast(0)
    seekTo(positionMs)
}
