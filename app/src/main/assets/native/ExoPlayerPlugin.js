export class ExoPlayerPlugin {
    constructor({ appSettings, events, playbackManager, loading }) {
        window['ExoPlayer'] = this;

        this.appSettings = appSettings;
        this.events = events;
        this.playbackManager = playbackManager;
        this.loading = loading;

        this.name = 'ExoPlayer';
        this.type = 'mediaplayer';
        this.id = 'exoplayer';
        this.subtitleStreamIndex = -1;
        this.audioStreamIndex = -1;
        this.cachedDeviceProfile = null;

        // Prioritize first
        this.priority = -1;
        this.supportsProgress = false;
        this.isLocalPlayer = true;

        // Current playback position in milliseconds
        this._currentTime = 0;
        this._paused = true;
        this._currentSrc = null;

        this._nativePlayer = window['NativePlayer'];
    }

    canPlayMediaType(mediaType) {
        return mediaType === 'Video';
    }

    canPlayItem(item, playOptions) {
        return this._nativePlayer.isEnabled();
    }

    currentSrc() {
        return this._currentSrc;
    }

    async play(options) {
        this.prepareAudioTracksCapabilities(options);

        this._paused = false;
        this._currentSrc = options.url;
        this._nativePlayer.loadPlayer(JSON.stringify(options));
        this.loading.hide();
    }

    duration(val) {
        return null;
    }

    destroy() {
        this._nativePlayer.destroyPlayer();
    }

    pause() {
        this._paused = true;
        this._nativePlayer.pausePlayer();
    }

    unpause() {
        this._paused = false;
        this._nativePlayer.resumePlayer();
    }

    paused() {
        return this._paused;
    }

    async stop(destroyPlayer) {
        this._nativePlayer.stopPlayer();

        if (destroyPlayer) {
            this.destroy();
        }
    }

    /**
     * Get or set volume percentage as as string
     */
    volume(volume) {
        if (volume !== undefined) {
            let volumeInt = parseInt(volume);
            this._nativePlayer.setVolume(volumeInt);
        }
        return null;
    }

    setMute(mute) {
        // Assume 30% as default when unmuting
        this._nativePlayer.setVolume(mute ? 0 : 30);
    }

    isMuted() {
        return false;
    }

    seekable() {
        return true;
    }

    seek(ticks) {
        this._nativePlayer.seek(ticks);
    }

    currentTime(ms) {
        if (ms !== undefined) {
            this._nativePlayer.seekMs(ms);
        }
        return this._currentTime;
    }

    setSubtitleStreamIndex(index) {
        this.subtitleStreamIndex = index;
    }

    canSetAudioStreamIndex() {
        return false;
    }

    setAudioStreamIndex(index) {
        this.audioStreamIndex = index;
    }

    async changeSubtitleStream(index) {
        var innerIndex = Number(index);
        this.playbackManager.setSubtitleStreamIndex(innerIndex);
        this.subtitleStreamIndex = innerIndex;
    }

    async changeAudioStream(index) {
        var innerIndex = Number(index);
        this.playbackManager.setAudioStreamIndex(innerIndex);
        this.audioStreamIndex = innerIndex;
    }

    prepareAudioTracksCapabilities(options) {
        var directPlayProfiles = this.cachedDeviceProfile.DirectPlayProfiles;
        var container = options.mediaSource.Container;

        options.mediaSource.MediaStreams.forEach(function (track) {
            if (track.Type === "Audio") {
                var codec = (track.Codec || '').toLowerCase();

                track.supportsDirectPlay = directPlayProfiles.filter(function (profile) {
                    return profile.Container === container && profile.Type === 'Video' && profile.AudioCodec.indexOf(codec) !== -1;
                }).length > 0;
            }
        });
    }

    async notifyStopped() {
        let stopInfo = {
            src: this._currentSrc
        };

        this.events.trigger(this, 'stopped', [stopInfo]);
        this._currentSrc = this._currentTime = null;
    }

    async getDeviceProfile() {
        if (this.cachedDeviceProfile) {
            return this.cachedDeviceProfile;
        }

        var bitrateSetting = this.appSettings.maxStreamingBitrate();

        var profile = {};
        profile.Name = "Android ExoPlayer"
        profile.MaxStreamingBitrate = bitrateSetting;
        profile.MaxStaticBitrate = 100000000;
        profile.MusicStreamingTranscodingBitrate = 192000;

        profile.SubtitleProfiles = [];
        profile.DirectPlayProfiles = [];
        profile.CodecProfiles = [];

        var videoProfiles = {
            '3gp': ['h263', 'h264', 'mpeg4', 'hevc'],
            'mp4': ['h263', 'h264', 'mpeg4', 'hevc', 'mpeg2video', 'av1', 'mpeg1video'],
            'ts': ['h264', 'mpeg4'],
            'webm': ['vp8', 'vp9', 'av1'],
            'mkv': ['h264', 'mpeg4', 'hevc', 'vp8', 'vp9', 'av1', 'mpeg2video', 'mpeg1video'],
            'flv': ['h264', 'mpeg4'],
            'asf': ['mpeg2video', 'mpeg4', 'h263', 'h264', 'hevc', 'vp8', 'vp9', 'mpeg1video'],
            'm2ts': ['mp2g2video', 'mpeg4', 'h264', 'mpeg1video'],
            'vob': ['mpeg1video', 'mpeg2video'],
            'mov': ['mpeg1video', 'mpeg2video', 'mpeg4', 'h263', 'h264', 'hevc']
        };

        var audioProfiles = {
            '3gp': ['aac', '3gpp', 'flac'],
            'mp4': ['mp3', 'aac', 'mp1', 'mp2'],
            'ts': ['mp3', 'aac', 'mp1', 'mp2', 'ac3', 'dts'],
            'flac': ['flac'],
            'aac': ['aac'],
            'mkv': ['mp3', 'aac', 'dts', 'flac', 'vorbis', 'opus', 'ac3', 'wma', 'mp1', 'mp2'],
            'mp3': ['mp3'],
            'ogg': ['ogg', 'opus', 'vorbis'],
            'webm': ['vorbis', 'opus'],
            'flv': ['mp3', 'aac'],
            'asf': ['aac', 'ac3', 'dts', 'wma', 'flac', 'pcm'],
            'm2ts': ['aac', 'ac3', 'dts', 'pcm'],
            'vob': ['mp1'],
            'mov': ['mp3', 'aac', 'ac3', 'dts-hd', 'pcm']
        };

        var subtitleProfiles = ['ass', 'idx', 'pgs', 'pgssub', 'smi', 'srt', 'ssa', 'subrip'];

        subtitleProfiles.forEach(function (format) {
            profile.SubtitleProfiles.push({
                Format: format,
                Method: 'Embed'
            });
        });

        var externalSubtitleProfiles = ['srt', 'sub', 'subrip', 'vtt'];

        externalSubtitleProfiles.forEach(function (format) {
            profile.SubtitleProfiles.push({
                Format: format,
                Method: 'External'
            });
        });

        profile.SubtitleProfiles.push({
            Format: 'dvdsub',
            Method: 'Encode'
        });

        var codecs = JSON.parse(this._nativePlayer.getSupportedFormats());
        var videoCodecs = [];
        var audioCodecs = [];

        for (var index in codecs.audioCodecs) {
            if (codecs.audioCodecs.hasOwnProperty(index)) {
                var audioCodec = codecs.audioCodecs[index];
                audioCodecs.push(audioCodec.codec);

                var profiles = audioCodec.profiles.join('|');
                var maxChannels = audioCodec.maxChannels;
                var maxSampleRate = audioCodec.maxSampleRate;

                var conditions = [{
                    Condition: 'LessThanEqual',
                    Property: 'AudioBitrate',
                    Value: audioCodec.maxBitrate.toString()
                }];

                if (profiles) {
                    conditions.push({
                        Condition: 'EqualsAny',
                        Property: 'AudioProfile',
                        Value: profiles
                    });
                }

                if (maxChannels) {
                    conditions.push({
                        Condition: 'LessThanEqual',
                        Property: 'AudioChannels',
                        Value: maxChannels.toString()
                    });
                }

                if (maxSampleRate) {
                    conditions.push({
                        Condition: 'LessThanEqual',
                        Property: 'AudioSampleRate',
                        Value: maxSampleRate.toString()
                    });
                }

                profile.CodecProfiles.push({
                    Type: 'Audio',
                    Codec: audioCodec.codec,
                    Conditions: conditions
                });
            }
        }

        for (var index in codecs.videoCodecs) {
            if (codecs.videoCodecs.hasOwnProperty(index)) {
                var videoCodec = codecs.videoCodecs[index];
                videoCodecs.push(videoCodec.codec);

                var profiles = videoCodec.profiles.join('|');
                var maxLevel = videoCodec.levels.length && Math.max.apply(null, videoCodec.levels);
                var conditions = [{
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
            }
        }

        for (var container in videoProfiles) {
            if (videoProfiles.hasOwnProperty(container)) {
                profile.DirectPlayProfiles.push({
                    Container: container,
                    Type: 'Video',
                    VideoCodec: videoProfiles[container].filter(function (codec) {
                        return videoCodecs.indexOf(codec) !== -1;
                    }).join(','),
                    AudioCodec: audioProfiles[container].filter(function (codec) {
                        return audioCodecs.indexOf(codec) !== -1;
                    }).join(',')
                });
            }
        }

        for (var container in audioProfiles) {
            if (audioProfiles.hasOwnProperty(container)) {
                profile.DirectPlayProfiles.push({
                    Container: container,
                    Type: 'Audio',
                    AudioCodec: audioProfiles[container].filter(function (codec) {
                        return audioCodecs.indexOf(codec) !== -1;
                    }).join(',')
                });
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
                MinSegments: 1
            },
            {
                Container: 'mkv',
                Type: 'Video',
                AudioCodec: audioProfiles['mkv'].filter(function (codec) {
                    return audioCodecs.indexOf(codec) !== -1;
                }).join(','),
                VideoCodec: 'h264',
                Context: 'Streaming'
            },
            {
                Container: 'mp3',
                Type: 'Audio',
                AudioCodec: 'mp3',
                Context: 'Streaming',
                Protocol: 'http'
            },

        ];

        this.cachedDeviceProfile = profile;

        return profile;
    }
}
