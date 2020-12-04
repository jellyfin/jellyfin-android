const features = [
    "filedownload",
    "displaylanguage",
    "subtitleappearancesettings",
    //'sharing',
    "exit",
    "htmlaudioautoplay",
    "htmlvideoautoplay",
    "externallinks",
    "clientsettings",
    "multiserver",
    "physicalvolumecontrol",
    "remotecontrol",
    "castmenuhashchange"
];

let deviceId;
let deviceName;
let appName;
let appVersion;

window.NativeShell = {
    enableFullscreen() {
        window.NativeInterface.enableFullscreen();
    },

    disableFullscreen() {
        window.NativeInterface.disableFullscreen();
    },

    openUrl(url, target) {
        window.NativeInterface.openUrl(url, target);
    },

    updateMediaSession(mediaInfo) {
        window.NativeInterface.updateMediaSession(JSON.stringify(mediaInfo));
    },

    hideMediaSession() {
        window.NativeInterface.hideMediaSession();
    },

    updateVolumeLevel(value) {
        window.NativeInterface.updateVolumeLevel(value);
    },

    downloadFile(downloadInfo) {
        window.NativeInterface.downloadFile(JSON.stringify(downloadInfo));
    },

    openClientSettings() {
        window.NativeInterface.openClientSettings();
    },

    selectServer() {
        window.NativeInterface.openServerSelection();
    },

    getPlugins() {
        return ["native/navigation", "native/exoplayer", "native/externalplayer"];
    },

    execCast(action, args, callback) {
        this.castCallbacks = this.castCallbacks || {};
        this.castCallbacks[action] = callback;
        window.NativeInterface.execCast(action, JSON.stringify(args));
    },

    castCallback(action, keep, err, result) {
        const callbacks = this.castCallbacks || {};
        const callback = callbacks[action];
        callback && callback(err || null, result);
        if (!keep) {
            delete callbacks[action];
        }
    }
};

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
                Condition: "NotEquals",
                Property: "VideoCodecTag",
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

window.NativeShell.AppHost = {
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
    getDefaultLayout() {
        return "mobile";
    },
    supports(command) {
        return features.includes(command.toLowerCase());
    },
    getDeviceProfile,
    getSyncProfile: getDeviceProfile,
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
    },
    exit() {
        window.NativeInterface.exitApp();
    }
};
