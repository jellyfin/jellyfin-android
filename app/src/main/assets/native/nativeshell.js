const features = [
    "castmenuhashchange",
    "clientsettings",
    "displaylanguage",
    "downloadmanagement",
    "exit",
    "externallinks",
    "filedownload",
    "fileinput",
    "htmlaudioautoplay",
    "htmlvideoautoplay",
    "multiserver",
    "physicalvolumecontrol",
    "remotecontrol",
    "subtitleappearancesettings",
    "subtitleburnsettings"
];

const plugins = [
    'NavigationPlugin',
    'ExoPlayerPlugin',
    'ExternalPlayerPlugin',
    'MediaSegmentsPlugin'
];

// Add plugin loaders
for (const plugin of plugins) {
    window[plugin] = async () => {
        const pluginDefinition = await import(`/native/${plugin}.js`);
        return pluginDefinition[plugin];
    };
}

// In desktop mode (e.g. Samsung DeX) the web client adds 'mouseIdle' to <body>
// after pointer inactivity but never removes it in the mobile layout, leaving
// the cursor permanently hidden. Drive the hide/show cycle from our side.
(function () {
    const IDLE_MS = 3000;
    let idleTimer = null;
    document.addEventListener('pointermove', () => {
        document.body.classList.remove('mouseIdle');
        clearTimeout(idleTimer);
        idleTimer = setTimeout(() => document.body.classList.add('mouseIdle'), IDLE_MS);
    }, { capture: true, passive: true });
}());

// Prevent pointer lock from hiding the cursor in desktop mode.
Object.defineProperty(Element.prototype, 'requestPointerLock', {
    value: function() { return Promise.resolve(); },
    writable: false,
    configurable: false,
});

const { deviceId, deviceName, appName, appVersion } = JSON.parse(window.NativeInterface.getDeviceInformation());
const codecCaps = JSON.parse(window.NativeInterface.getCodecCapabilities());

window.NativeShell = {
    enableFullscreen() {
        window.NativeInterface.enableFullscreen();
    },

    disableFullscreen() {
        window.NativeInterface.disableFullscreen();
    },

    openUrl(url, target) {
        window.NativeInterface.openUrl(url);
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
        window.NativeInterface.downloadFiles(JSON.stringify([downloadInfo]));
    },

    downloadFiles(downloadInfo) {
        window.NativeInterface.downloadFiles(JSON.stringify(downloadInfo));
    },

    openDownloadManager() {
        window.NativeInterface.openDownloadManager();
    },

    openClientSettings() {
        window.NativeInterface.openClientSettings();
    },

    selectServer() {
        window.NativeInterface.openServerSelection();
    },

    getPlugins() {
        return plugins;
    },

    async execCast(action, args, callback) {
        this.castCallbacks = this.castCallbacks || {};
        this.castCallbacks[action] = callback;
        window.NativeInterface.execCast(action, JSON.stringify(args));
    },

    async castCallback(action, keep, err, result) {
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
                Value: codecCaps.h264MaxLevel
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
    init() {},
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
