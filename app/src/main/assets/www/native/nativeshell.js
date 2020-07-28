window.NativeShell = {
    enableFullscreen: NativeInterface.enableFullscreen,
    disableFullscreen: NativeInterface.disableFullscreen,
    openUrl: NativeInterface.openUrl,
    hideMediaSession: NativeInterface.hideMediaSession,

    getDeviceInformation() {
        const result = window.NativeInterface.getDeviceInformation();
        if (result) {
            successCallback && successCallback(JSON.parse(result));
        } else {
            errorCallback && errorCallback();
        }
    },

    updateMediaSession(mediaInfo) {
        window.NativeInterface.updateMediaSession(JSON.stringify(mediaInfo));
    },


    downloadFile(downloadInfo) {
        window.NativeInterface.downloadFile(JSON.stringify(downloadInfo));
    },

    getPlugins() {
        return [];
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
