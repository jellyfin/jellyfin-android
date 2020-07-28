const features = [
    "filedownload",
    "displaylanguage",
    //'externalplayerintent',
    "subtitleappearancesettings",
    //'sharing',
    "exit",
    "htmlaudioautoplay",
    "htmlvideoautoplay",
    "externallinks",
    "multiserver",
    "physicalvolumecontrol",
    "remotecontrol",
    "castmenuhashchange"
];

function getDeviceProfile(profileBuilder, item) {
    const profile = profileBuilder({
        enableMkvProgressive: false
    });

    profile.CodecProfiles = profile.CodecProfiles.filter(function (i) {
        return i.Type === "Audio";
    });

    profile.SubtitleProfiles.push(
        {
            Format: "ssa",
            Method: "External"
        },
        {
            Format: "ass",
            Method: "External"
        }
    );

    profile.CodecProfiles.push({
        Type: "Video",
        Container: "avi",
        Conditions: [
            {
                Condition: "NotEqual",
                Property: "CodecTag",
                Value: "xvid"
            }
        ]
    });

    profile.CodecProfiles.push({
        Type: "Video",
        Codec: "h264",
        Conditions: [
            {
                Condition: "EqualsAny",
                Property: "VideoProfile",
                Value: "high|main|baseline|constrained baseline"
            },
            {
                Condition: "LessThanEqual",
                Property: "VideoLevel",
                Value: "41"
            }]
    });

    profile.TranscodingProfiles.reduce(function (profiles, p) {
        if (p.Type === "Video" && p.CopyTimestamps === true && p.VideoCodec === "h264") {
            p.AudioCodec += ",ac3";
            profiles.push(p);
        }
        return profiles;
    }, []);

    return profile;
}

function parseVideoTrack(track) {
    return {
        codec: track.Codec,
        bitRate: track.BitRate,
        width: track.Width,
        height: track.Height,
        frameRate: track.RealFrameRate
    };
}

function parseAudioTrack(track) {
    return {
        codec: track.Codec,
        bitRate: track.BitRate,
        channelCount: track.Channels,
        sampleRate: track.SampleRate
    };
}

function parseSubtitleTrack(track) {
    return {
        codec: track.Codec
    };
}

function getDeviceProfileForVideo(item) {
    const container = item.Container;
    const videoTracks = [];
    const audioTracks = [];
    const subtitleTracks = [];

    for (let i = 0; i < item.MediaStreams.length; i++) {
        const track = item.MediaStreams[i];

        switch (track.Type) {
            case "Video":
                videoTracks.push(parseVideoTrack(track));
                break;
            case "Audio":
                audioTracks.push(parseAudioTrack(track));
                break;
            case "Subtitle":
                subtitleTracks.push(parseSubtitleTrack(track));
                break;
        }
    }

    const supportedTracks = window.ExoPlayer.checkTracksSupport(container, videoTracks, audioTracks, subtitleTracks);
    // TODO: check if the given tracks are supported. If not, they are not added up to directPlayProfiles
}

let deviceId;
let deviceName;
let appName;
let appVersion;

window.NativeShell.AppHost = {
    exit() {
        if (navigator.app && navigator.app.exitApp) {
            navigator.app.exitApp();
        } else {
            window.close();
        }
    },
    supports(command) {
        return features.includes(command.toLowerCase());
    },
    getSyncProfile: getDeviceProfile,
    getDefaultLayout() {
        return "mobile";
    },
    getDeviceProfile,
    init() {
        try {
            const result = JSON.parse(window.NativeInterface.getDeviceInformation());
            // set globally so they can be used elsewhere
            deviceId = result.deviceId;
            deviceName = result.deviceName;
            appName = result.appName;
            appVersion = result.appVersion;

            return Promise.resolve({
                deviceId,
                deviceName,
                appName,
                appVersion,
            });
        } catch (e) {
            return Promise.reject(e);
        }
    },
    deviceName() {
        return deviceName;
    },
    deviceId() {
        return deviceId;
    },
    appName() {
        return appName;
    },
    appVersion() {
        return appVersion;
    }
};