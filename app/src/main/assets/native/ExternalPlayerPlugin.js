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

    currentSrc() {
        return this._currentSrc;
    }

    async play(options) {
        this._currentTime = (options.playerStartPositionTicks || 0) / 10000;
        this._paused = false;
        this._currentSrc = options.url;
        this._isIntro = options.item && options.item.ProviderIds && options.item.ProviderIds.hasOwnProperty("prerolls.video");
        this._externalPlayer.initPlayer(this.prepareMedia(options));
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

    getMaxStreamingBitrate() {
        const endpointInfo = window.NativeShell.getSavedEndpointInfo();
        return this.appSettings.maxStreamingBitrate(endpointInfo.IsInNetwork, 'Video');
    }

    enableAutomaticBitrateDetection() {
        const endpointInfo = window.NativeShell.getSavedEndpointInfo();
        return this.appSettings.enableAutomaticBitrateDetection(endpointInfo.IsInNetwork, 'Video');
    }

    prepareMedia(options) {
        options.currentMaxBitrate = this.getMaxStreamingBitrate();
        options.isAutomaticBitrateEnabled = this.enableAutomaticBitrateDetection();
        if (options.mediaType === "Video") {
            options.qualityOptions = window.NativeShell.getVideoQualityOptions({
                currentMaxBitrate: options.currentMaxBitrate,
                isAutomaticBitrateEnabled: options.isAutomaticBitrateEnabled,
                videoWidth: options.item.Width,
                videoHeight: options.item.Height,
                enableAuto: true
            }).map(function (opt, index) {
                return {
                    Index: index,
                    DisplayTitle: opt.name,
                    maxHeight: opt.maxHeight,
                    bitrate: opt.bitrate,
                    selected: opt.selected || false
                }
            });
        }
        return JSON.stringify(options);
    }

    async getDeviceProfile() {
        var bitrateSetting = this.getMaxStreamingBitrate();

        if (this.cachedDeviceProfile) {
            this.cachedDeviceProfile.MaxStreamingBitrate = bitrateSetting;
            return this.cachedDeviceProfile;
        }

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

        var codecs = JSON.parse(window.NativePlayer.getSupportedFormats());
        profile.CodecProfiles = [];

        for (var index in codecs.videoCodecs) {
            if (codecs.videoCodecs.hasOwnProperty(index)) {
                var videoCodec = codecs.videoCodecs[index];
                if (videoCodec.codec == 'h264') {
                    var profiles = videoCodec.profiles.join('|');
                    var maxLevel = videoCodec.levels.length && Math.max.apply(null, videoCodec.levels);
                    var conditions = [{
                        Condition: 'LessThanEqual',
                        Property: 'Width',
                        Value: videoCodec.maxWidth.toString()
                    },
                    {
                        Condition: 'LessThanEqual',
                        Property: 'Height',
                        Value: videoCodec.maxHeight.toString()
                    },
                    {
                        Condition: 'LessThanEqual',
                        Property: 'VideoBitrate',
                        Value: videoCodec.maxBitrate.toString()
                    }];
                    if (profiles) {
                        conditions.push({
                            Condition: 'EqualsAny',
                            Property: 'VideoProfile',
                            Value: profiles
                        });
                    }
                    if (maxLevel) {
                        conditions.push({
                            Condition: 'LessThanEqual',
                            Property: 'VideoLevel',
                            Value: maxLevel.toString()
                        });
                    }
                    if (conditions.length) {
                        profile.CodecProfiles.push({
                            Type: 'Video',
                            Codec: videoCodec.codec,
                            Conditions: conditions
                        });
                    }
                    break;
                }
            }
        }

        profile.SubtitleProfiles = [];

        var subtitleProfiles = ['ass', 'ssa', 'srt', 'sub'];

        subtitleProfiles.forEach(function (format) {
            profile.SubtitleProfiles.push({
                Format: format,
                Method: 'External'
            });
        });

        subtitleProfiles.forEach(function (format) {
            profile.SubtitleProfiles.push({
                Format: format,
                Method: 'Embed'
            });
        });

        profile.SubtitleProfiles.push({
            Format: 'dvdsub',
            Method: 'Encode'
        });

        var audioProfiles = {
            'ts': ['aac', 'mp3', 'mp1', 'mp2', 'ac3', 'dts'],
            'mkv': ['aac', 'mp3', 'dts', 'flac', 'vorbis', 'opus', 'ac3', 'wma', 'mp1', 'mp2']
        };
        var audioCodecs = [];

        for (var index in codecs.audioCodecs) {
            if (codecs.audioCodecs.hasOwnProperty(index)) {
                var audioCodec = codecs.audioCodecs[index];
                audioCodecs.push(audioCodec.codec);
            }
        }

        profile.TranscodingProfiles = [
            {
                Container: 'ts',
                Type: 'Video',
                AudioCodec: audioProfiles['ts'].filter(function (codec) {
                    return audioCodecs.indexOf(codec) !== -1;
                }).join(','),
                VideoCodec: 'h264',
                Context: 'Streaming',
                Protocol: 'hls',
                MinSegments: '1'
            },
            {
                Container: 'mkv',
                Type: 'Video',
                AudioCodec: audioProfiles['mkv'].filter(function (codec) {
                    return audioCodecs.indexOf(codec) !== -1;
                }).join(','),
                VideoCodec: 'h264',
                Context: 'Streaming'
            }
        ];

        this.cachedDeviceProfile = profile;

        return profile;
    }
}
