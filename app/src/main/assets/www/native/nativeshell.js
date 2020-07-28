window.NativeShell = {
    enableFullscreen: NativeInterface.enableFullscreen,
    disableFullscreen: NativeInterface.disableFullscreen,
    openUrl: NativeInterface.openUrl,
    hideMediaSession: NativeInterface.hideMediaSession,

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
