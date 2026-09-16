export class ExoPlayerPlugin {
    constructor({ events, playbackManager, appSettings, loading }) {
        window['ExoPlayer'] = this;

        this.events = events;
        this.playbackManager = playbackManager;
        this.appSettings = appSettings;
        this.loading = loading;

        this.name = 'ExoPlayer';
        this.type = 'mediaplayer';
        this.id = 'exoplayer';

        // Prioritize first
        this.priority = -1;
        this.isLocalPlayer = true;

        // Current playback position in milliseconds
        this._currentTime = 0;
        this._paused = true;

        this._nativePlayer = window['NativePlayer'];
    }

    async play(options) {
        // Sanitize input
        options.ids = options.items.map(item => item.Id);
        delete options.items;

        const preferredTranscodeVideoCodec = typeof this.appSettings.preferredTranscodeVideoCodec === 'function'
            ? this.appSettings.preferredTranscodeVideoCodec()
            : null;
        const preferredTranscodeVideoAudioCodec = typeof this.appSettings.preferredTranscodeVideoAudioCodec === 'function'
            ? this.appSettings.preferredTranscodeVideoAudioCodec()
            : null;
        let preferFmp4HlsContainer = null;
        if (typeof this.appSettings?.preferFmp4HlsContainer === 'function') {
            preferFmp4HlsContainer = this.appSettings.preferFmp4HlsContainer();
        } else if (typeof this.appSettings?.preferFmp4HlsContainer === 'boolean') {
            preferFmp4HlsContainer = this.appSettings.preferFmp4HlsContainer;
        } else if (typeof this.appSettings?.preferForHls === 'function') {
            preferFmp4HlsContainer = this.appSettings.preferForHls();
        } else if (typeof this.appSettings?.get === 'function') {
            const val = this.appSettings.get('preferFmp4HlsContainer') ?? this.appSettings.get('preferForHls') ?? this.appSettings.get('preferFmp4Hls');
            if (typeof val === 'boolean') {
                preferFmp4HlsContainer = val;
            } else if (typeof val === 'string') {
                preferFmp4HlsContainer = val === 'true';
            }
        }

        if (preferFmp4HlsContainer === null) {
            try {
                for (let i = 0; i < localStorage.length; i++) {
                    const key = localStorage.key(i);
                    if (key && (key === 'preferFmp4HlsContainer' || key.endsWith('-preferFmp4HlsContainer') || key === 'preferForHls' || key.endsWith('-preferForHls'))) {
                        const val = localStorage.getItem(key);
                        if (val !== null) {
                            preferFmp4HlsContainer = val === 'true';
                            break;
                        }
                    }
                }
            } catch (err) {
                // Ignore security/quota exceptions in restricted web views
            }
        }

        const preferences = {
            maxStreamingBitrateLocal: this.appSettings.maxStreamingBitrate(true, 'Video'),
            maxStreamingBitrateRemote: this.appSettings.maxStreamingBitrate(false, 'Video'),
            preferredTranscodeVideoCodec: preferredTranscodeVideoCodec || null,
            preferredTranscodeVideoAudioCodec: preferredTranscodeVideoAudioCodec || null,
            preferFmp4HlsContainer: preferFmp4HlsContainer !== null ? preferFmp4HlsContainer : null,
        };

        this._paused = false;
        this._nativePlayer.loadPlayer(JSON.stringify(options), JSON.stringify(preferences));
        this.loading.hide();
    }

    shuffle(item) {}

    instantMix(item) {}

    queue(options) {}

    queueNext(options) {}

    canPlayMediaType(mediaType) {
        return mediaType === 'Video';
    }

    canQueueMediaType(mediaType) {
        return this.canPlayMediaType(mediaType);
    }

    canPlayItem(item, playOptions) {
        return this._nativePlayer.isEnabled() &&
            playOptions.fullscreen &&
            !this.playbackManager.syncPlayEnabled;
    }

    async stop(destroyPlayer) {
        this._nativePlayer.stopPlayer();

        if (destroyPlayer) {
            this.destroy();
        }
    }

    nextTrack() {}

    previousTrack() {}

    seek(ticks) {
        this._nativePlayer.seekTicks(ticks);
    }

    currentTime(ms) {
        if (ms !== undefined) {
            this._nativePlayer.seekMs(ms);
        }
        return this._currentTime;
    }

    duration(val) {
        return null;
    }

    /**
     * Get or set volume percentage as as string
     */
    volume(volume) {
        if (volume !== undefined) {
            this.setVolume(volume);
        }
        return null;
    }

    getVolume() {}

    setVolume(vol) {
        let volume = parseInt(vol);
        this._nativePlayer.setVolume(volume);
    }

    volumeUp() {}

    volumeDown() {}

    isMuted() {
        return false;
    }

    setMute(mute) {
        // Assume 30% as default when unmuting
        this._nativePlayer.setVolume(mute ? 0 : 30);
    }

    toggleMute() {}

    paused() {
        return this._paused;
    }

    pause() {
        this._paused = true;
        this._nativePlayer.pausePlayer();
    }

    unpause() {
        this._paused = false;
        this._nativePlayer.resumePlayer();
    }

    playPause() {
        if (this._paused) {
            this.unpause();
        } else {
            this.pause();
        }
    }

    canSetAudioStreamIndex() {
        return false;
    }

    setAudioStreamIndex(index) {}

    setSubtitleStreamIndex(index) {}

    async changeAudioStream(index) {}

    async changeSubtitleStream(index) {}

    getPlaylist() {
        return Promise.resolve([]);
    }

    getCurrentPlaylistItemId() {}

    setCurrentPlaylistItem() {
        return Promise.resolve();
    }

    removeFromPlaylist() {
        return Promise.resolve();
    }

    destroy() {
        this._nativePlayer.destroyPlayer();
    }

    async getDeviceProfile() {
        return {
            Name: 'ExoPlayer Stub',
            MaxStreamingBitrate: 100000000,
            MaxStaticBitrate: 100000000,
            MusicStreamingTranscodingBitrate: 320000,
            DirectPlayProfiles: [{Type: 'Video'}, {Type: 'Audio'}],
            CodecProfiles: [],
            SubtitleProfiles: [],
            TranscodingProfiles: []
        };
    }
}
