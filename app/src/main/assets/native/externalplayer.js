define(['events', 'playbackManager', 'toast'], function (events, playbackManager, toast) {
    "use strict";

    return function () {
        var self = this;

        window.ExtPlayer = this;

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

        self.canPlayMediaType = function (mediaType) {
            return mediaType === 'Video';
        };

        self.canPlayItem = function (item, playOptions) {
            var mediaSource = item.MediaSources[0] || false;
            return window.ExternalPlayer.isEnabled() && mediaSource && mediaSource.SupportsDirectStream;
        };

        self.supportsPlayMethod = function (playMethod, item) {
            return playMethod === 'DirectStream';
        };

        self.currentSrc = function () {
            return self._currentSrc;
        };

        self.play = function (options) {
            return new Promise(function (resolve) {
                self._currentTime = 0;
                self._paused = false;
                self._currentSrc = options.url;
                window.ExternalPlayer.initPlayer(JSON.stringify(options));
                resolve();
            });
        };

        self.setSubtitleStreamIndex = function (index) {
        };

        self.setAudioStreamIndex = function (index) {
        };

        self.canSetAudioStreamIndex = function () {
            return false;
        };

        self.setAudioStreamIndex = function (index) {
        };

        // Save this for when playback stops, because querying the time at that point might return 0
        self.currentTime = function (val) {
            return null;
        };

        self.duration = function (val) {
            return null;
        };

        self.destroy = function () {
        };

        self.pause = function () {
        };

        self.unpause = function () {
        };

        self.paused = function () {
            return self._paused;
        };

        self.stop = function (destroyPlayer) {
            return new Promise(function (resolve) {
                if (destroyPlayer) {
                    self.destroy();
                }
                resolve();
            });
        };

        self.volume = function (val) {
            return self._volume;
        };

        self.setMute = function (mute) {
        };

        self.isMuted = function () {
            return self._volume == 0;
        };

        self.notifyEnded = function () {
            new Promise(function () {
                let stopInfo = {
                    src: self._currentSrc
                };

                events.trigger(self, 'stopped', [stopInfo]);
                self._currentSrc = self._currentTime = null;
            });
        };

        self.notifyTimeUpdate = function (currentTime) {
            // if no time provided handle like playback completed
            currentTime = currentTime || playbackManager.duration(self);
            new Promise(function () {
                currentTime = currentTime / 1000;
                self._timeUpdated = self._currentTime != currentTime;
                self._currentTime = currentTime;
                events.trigger(self, 'timeupdate');
            });
        };

        self.notifyCanceled = function (message) {
            // required to not mark an item as seen / completed without time changes
            var currentTime = self._currentTime || 0;
            self.notifyTimeUpdate(currentTime + 1);
            self.notifyTimeUpdate(currentTime - 1);
            self.notifyEnded();
            if (message) {
                toast(message);
            }
        };

        self.currentTime = function () {
            return (self._currentTime || 0) * 1000;
        };

        self.changeSubtitleStream = function (index) {
            // detach from the main ui thread
            new Promise(function () {
                var innerIndex = Number(index);
                self.subtitleStreamIndex = innerIndex;
            });
        };

        self.changeAudioStream = function (index) {
            // detach from the main ui thread
            new Promise(function () {
                var innerIndex = Number(index);
                self.audioStreamIndex = innerIndex;
            });
        }

        self.getDeviceProfile = function () {
            // using exoplayer implementation for now
            return window.ExoPlayer.getDeviceProfile();
        };
    };
});
