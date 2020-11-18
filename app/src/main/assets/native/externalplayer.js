
class ExExternalPlayerPlugin {
    constructor({appSettings, events, playbackManager}) {
        this.self = this;

        self.appSettings = appSettings;
        self.events = events;
        self.playbackManager = playbackManager;

        window.ExtPlayer = self;

        self.name = 'External Player';
        self.type = 'mediaplayer';
        self.id = 'externalplayer';
        self.subtitleStreamIndex = -1;
        self.audioStreamIndex = -1;
        self.cachedDeviceProfile = null;

        // Prioritize first
        self.priority = -2;
        self.supportsProgress = false;
        self.isLocalPlayer = true;

        // Disable orientation lock
        self.isExternalPlayer = true;
        self._currentTime = 0;
        self._paused = true;
        self._volume = 100;
        self._currentSrc = null;
    }

    canPlayMediaType(mediaType) {
        return mediaType === 'Video';
    }

    canPlayItem(item, playOptions) {
        var mediaSource = item.MediaSources && item.MediaSources[0];
        return window.ExternalPlayer.isEnabled() && mediaSource && mediaSource.SupportsDirectStream;
    }

    supportsPlayMethod(playMethod, item) {
        return playMethod === 'DirectStream';
    }

    currentSrc() {
        return self._currentSrc;
    }

    play(options) {
        return new Promise(function (resolve) {
            self._currentTime = (options.playerStartPositionTicks || 0) / 10000;
            self._paused = false;
            self._currentSrc = options.url;
            window.ExternalPlayer.initPlayer(JSON.stringify(options));
            resolve();
        });
    }

    setSubtitleStreamIndex(index) {}

    setAudioStreamIndex(index) {}

    canSetAudioStreamIndex() {
        return false;
    }

    setAudioStreamIndex(index) {
    }

    // Save this for when playback stops, because querying the time at that point might return 0
    currentTime(val) {
        return null;
    }

    duration(val) {
        return null;
    }

    destroy() {}

    pause() {}

    unpause() {}

    paused() {
        return self._paused;
    }

    stop(destroyPlayer) {
        return new Promise(function (resolve) {
            if (destroyPlayer) {
                self.destroy();
            }
            resolve();
        });
    }

    volume(val) {
        return self._volume;
    }

    setMute(mute) {
    }

    isMuted() {
        return self._volume == 0;
    }

    notifyEnded() {
        new Promise(function () {
            let stopInfo = {
                src: self._currentSrc
            };

            self.events.trigger(self, 'stopped', [stopInfo]);
            self._currentSrc = self._currentTime = null;
        });
    }

    notifyTimeUpdate(currentTime) {
        // if no time provided handle like playback completed
        currentTime = currentTime || self.playbackManager.duration(self);
        new Promise(function () {
            currentTime = currentTime / 1000;
            self._timeUpdated = self._currentTime != currentTime;
            self._currentTime = currentTime;
            self.events.trigger(self, 'timeupdate');
        });
    }

    notifyCanceled() {
        // required to not mark an item as seen / completed without time changes
        let currentTime = self._currentTime || 0;
        self.notifyTimeUpdate(currentTime - 1);
        if (currentTime > 0) {
            self.notifyTimeUpdate(currentTime);
        }
        self.notifyEnded();
    }

    currentTime() {
        return (self._currentTime || 0) * 1000;
    }

    changeSubtitleStream(index) {
        // detach from the main ui thread
        new Promise(function () {
            var innerIndex = Number(index);
            self.subtitleStreamIndex = innerIndex;
        });
    }

    changeAudioStream(index) {
        // detach from the main ui thread
        new Promise(function () {
            var innerIndex = Number(index);
            self.audioStreamIndex = innerIndex;
        });
    }

    getDeviceProfile() {
        return new Promise(function (resolve) {
            if (self.cachedDeviceProfile) {
                resolve(self.cachedDeviceProfile);
            }

            var bitrateSetting = self.appSettings.maxStreamingBitrate();

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

            self.cachedDeviceProfile = profile;

            resolve(profile);
        });
    }
}

window.ExternalPlayerPlugin = ExExternalPlayerPlugin;
