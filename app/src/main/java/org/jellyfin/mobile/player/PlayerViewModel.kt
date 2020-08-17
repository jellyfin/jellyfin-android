package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.util.Clock
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.player.source.MediaSourceManager
import org.jellyfin.mobile.utils.getRendererIndexByType

class PlayerViewModel(application: Application) : AndroidViewModel(application), LifecycleObserver, Player.EventListener {
    val mediaSourceManager = MediaSourceManager(this)

    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()
    private var shouldPlayOnStart = false

    // Public LiveData getters
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    init {
        // Alternatively expose this as a dependency
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun setupPlayer() {
        _player.value = SimpleExoPlayer.Builder(getApplication()).apply {
            setTrackSelector(mediaSourceManager.trackSelector)
            if (BuildConfig.DEBUG) {
                setAnalyticsCollector(AnalyticsCollector(Clock.DEFAULT).apply {
                    addListener(mediaSourceManager.eventLogger)
                })
            }
        }.build().apply {
            addListener(this@PlayerViewModel)
        }
    }

    private fun releasePlayer() {
        val player = _player.value ?: return
        _player.value = null
        player.removeListener(this)
        player.release()
    }

    fun playMedia(source: MediaSource, replace: Boolean = false, startPosition: Long = 0) {
        val player = _player.value ?: return
        if (replace || player.contentDuration == C.TIME_UNSET /* no content loaded yet */) {
            player.prepare(source, false, false)
            if (startPosition > 0) player.seekTo(startPosition)
            player.playWhenReady = true
        }
    }

    fun seekToOffset(offsetMs: Long) {
        val player = _player.value ?: return
        var positionMs = player.currentPosition + offsetMs
        val durationMs = player.duration
        if (durationMs != C.TIME_UNSET) {
            positionMs = positionMs.coerceAtMost(durationMs)
        }
        positionMs = positionMs.coerceAtLeast(0)
        player.seekTo(positionMs)
    }

    fun getPlayerRendererIndex(type: Int): Int {
        return _player.value?.getRendererIndexByType(type) ?: -1
    }

    // (Lifecycle) Callbacks

    @SuppressLint("SwitchIntDef")
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> mediaSourceManager.selectInitialTracks()
        }
        _playerState.value = playbackState
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        setupPlayer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (shouldPlayOnStart) _player.value?.playWhenReady = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        shouldPlayOnStart = _player.value?.isPlaying ?: false
        _player.value?.playWhenReady = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        releasePlayer()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}