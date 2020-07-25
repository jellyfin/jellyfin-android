function NativeShell() {}

NativeShell.prototype.getDeviceInformation = function(successCallback, errorCallback) {
    var result = window.NativeInterface.getDeviceInformation();
    if (result) {
        successCallback && successCallback(JSON.parse(result));
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.enableFullscreen = function(successCallback, errorCallback) {
    var result = window.NativeInterface.enableFullscreen();
    if (result) {
        successCallback && successCallback();
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.disableFullscreen = function(successCallback, errorCallback) {
    var result = window.NativeInterface.disableFullscreen();
    if (result) {
        successCallback && successCallback();
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.openUrl = function(url, target, successCallback, errorCallback) {
    var result = window.NativeInterface.openIntent(url);
    if (result) {
        successCallback && successCallback();
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.updateMediaSession = function(options, successCallback, errorCallback) {
    var result = window.NativeInterface.updateMediaSession(JSON.stringify(options));
    if (result) {
        successCallback && successCallback();
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.hideMediaSession = function(successCallback, errorCallback) {
    var result = window.NativeInterface.hideMediaSession();
    if (result) {
        successCallback && successCallback();
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.downloadFile = function(options, successCallback, errorCallback) {
    var result = window.NativeInterface.downloadFile(JSON.stringify(options));
    if (result) {
        successCallback && successCallback();
    } else {
        errorCallback && errorCallback();
    }
};

NativeShell.prototype.getPlugins = function() {
    return [];
};

NativeShell.prototype.execCast = function(action, args, callback) {
    this.castCallbacks = this.castCallbacks || {};
    this.castCallbacks[action] = callback;
    window.NativeInterface.execCast(action, JSON.stringify(args));
}

NativeShell.prototype.castCallback = function(action, keep, err, result) {
    var callbacks = this.castCallbacks || {};
    var callback = callbacks[action];
    callback && callback(err || null, result);
    if (!keep) {
        delete callbacks[action];
    }
}

window.NativeShell = new NativeShell();