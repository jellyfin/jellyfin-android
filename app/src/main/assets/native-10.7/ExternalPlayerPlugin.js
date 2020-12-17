export class ExternalPlayerPlugin {
    constructor({ appSettings, events, playbackManager }) {
        window['ExtPlayer'] = this;

        this.appSettings = appSettings;
        this.events = events;
        this.playbackManager = playbackManager;

        this.name = 'External Player';
        this.type = 'mediaplayer';
        this.id = 'externalplayer';
        this.subtitleStreamIndex = -1;
        this.audioStreamIndex = -1;
        this.cachedDeviceProfile = null;

        // Prioritize first
        this.priority = -2;
        this.supportsProgress = false;
        this.isLocalPlayer = true;

        // Disable orientation lock
        this.isExternalPlayer = true;
        this._currentTime = 0;
        this._paused = true;
        this._volume = 100;
        this._currentSrc = null;
        this._isIntro = false;

        this._externalPlayer = window['ExternalPlayer'];
    }

    canPlayMediaType(mediaType) {
        return mediaType === 'Video';
    }

    canPlayItem(item, playOptions) {
        return this._externalPlayer.isEnabled();
    }

    supportsPlayMethod(playMethod, item) {
        return playMethod === 'DirectStream';
    }

    currentSrc() {
        return this._currentSrc;
    }

    async play(options) {
        this._currentTime = (options.playerStartPositionTicks || 0) / 10000;
        this._paused = false;
        this._currentSrc = options.url;
        this._isIntro = options.item && options.item.ProviderIds && options.item.ProviderIds.hasOwnProperty("prerolls.video");
        this._externalPlayer.initPlayer(JSON.stringify(options));
    }

    setSubtitleStreamIndex(index) { }

    canSetAudioStreamIndex() {
        return false;
    }

    setAudioStreamIndex(index) {
    }

    duration(val) {
        return null;
    }

    destroy() { }

    pause() { }

    unpause() { }

    paused() {
        return this._paused;
    }

    async stop(destroyPlayer) {
        if (destroyPlayer) {
            this.destroy();
        }
    }

    volume(val) {
        return this._volume;
    }

    setMute(mute) {
    }

    isMuted() {
        return this._volume == 0;
    }

    async notifyEnded() {
        let stopInfo = {
            src: this._currentSrc
        };

        this.playbackManager._playNextAfterEnded = this._isIntro;
        this.events.trigger(this, 'stopped', [stopInfo]);
        this._currentSrc = this._currentTime = null;
    }

    async notifyTimeUpdate(currentTime) {
        // if no time provided handle like playback completed
        currentTime = currentTime || this.playbackManager.duration(this);
        currentTime = currentTime / 1000;
        this._timeUpdated = this._currentTime != currentTime;
        this._currentTime = currentTime;
        this.events.trigger(this, 'timeupdate');
    }

    notifyCanceled() {
        // required to not mark an item as seen / completed without time changes
        let currentTime = this._currentTime || 0;
        this.notifyTimeUpdate(currentTime - 1);
        if (currentTime > 0) {
            this.notifyTimeUpdate(currentTime);
        }
        this.notifyEnded();
    }

    currentTime() {
        return (this._currentTime || 0) * 1000;
    }

    async changeSubtitleStream(index) {
        var innerIndex = Number(index);
        this.subtitleStreamIndex = innerIndex;
    }

    async changeAudioStream(index) {
        var innerIndex = Number(index);
        this.audioStreamIndex = innerIndex;
    }

    async getDeviceProfile() {
        if (this.cachedDeviceProfile) {
            return this.cachedDeviceProfile;
        }

        var bitrateSetting = this.appSettings.maxStreamingBitrate();

        var profile = {};
        profile.Name = "Android External Player"
        profile.MaxStreamingBitrate = bitrateSetting;
        profile.MaxStaticBitrate = 100000000;
        profile.MusicStreamingTranscodingBitrate = 192000;

        profile.DirectPlayProfiles = [];

        // leave container null for all
        profile.DirectPlayProfiles.push({
            Type: 'Video'
        });

        // leave container null for all
        profile.DirectPlayProfiles.push({
            Type: 'Audio'
        });

        profile.CodecProfiles = [];

        profile.SubtitleProfiles = [];

        var subtitleProfiles = ['ass', 'idx', 'smi', 'srt', 'ssa', 'subrip'];

        var embedSubtitleProfiles = ['pgs', 'pgssub'];

        subtitleProfiles.concat(embedSubtitleProfiles).forEach(function (format) {
            profile.SubtitleProfiles.push({
                Format: format,
                Method: 'Embed'
            });
        });

        var externalSubtitleProfiles = ['sub', 'vtt'];

        subtitleProfiles.concat(externalSubtitleProfiles).forEach(function (format) {
            profile.SubtitleProfiles.push({
                Format: format,
                Method: 'External'
            });
        });

        profile.SubtitleProfiles.push({
            Format: 'dvdsub',
            Method: 'Encode'
        });

        profile.TranscodingProfiles = [];

        this.cachedDeviceProfile = profile;

        return profile;
    }
}
