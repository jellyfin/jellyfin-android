package org.jellyfin.android.player

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import org.jellyfin.android.player.source.MediaSourceManager
import org.jellyfin.android.utils.getRendererIndexByType

class PlayerViewModel(application: Application) : AndroidViewModel(application), LifecycleObserver, Player.EventListener {
    val mediaSourceManager = MediaSourceManager(this)

    private val _player = MutableLiveData<ExoPlayer?>()
    private val _loading = MutableLiveData<Boolean>(false)
    private var shouldPlayOnStart = false

    // Public LiveData getters
    val player: LiveData<ExoPlayer?> get() = _player
    val loading: LiveData<Boolean> get() = _loading

    init {
        // Alternatively expose this as a dependency
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun setupPlayer() {
        _player.value = SimpleExoPlayer.Builder(getApplication())
            .setTrackSelector(mediaSourceManager.trackSelector)
            .build()
            .apply { addListener(this@PlayerViewModel) }
    }

    private fun releasePlayer() {
        val player = _player.value ?: return
        _player.value = null
        player.removeListener(this)
        player.release()
    }

    fun playMedia(source: MediaSource, replace: Boolean = false) {
        val player = _player.value ?: return
        if (replace || player.contentDuration == C.TIME_UNSET /* no content loaded yet */) {
            player.prepare(source, false, false)
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
        _loading.value = playbackState == Player.STATE_BUFFERING
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