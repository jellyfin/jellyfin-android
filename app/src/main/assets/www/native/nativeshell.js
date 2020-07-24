function NativeShell() {}

NativeShell.prototype.getDeviceInformation = function(successCallback, errorCallback) {
    var result = window.NativeInterface.getDeviceInformation();
    if (result) {
        if (successCallback) successCallback(JSON.parse(result));
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.enableFullscreen = function(successCallback, errorCallback) {
    var result = window.NativeInterface.enableFullscreen();
    if (result) {
        if (successCallback) successCallback();
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.disableFullscreen = function(successCallback, errorCallback) {
    var result = window.NativeInterface.disableFullscreen();
    if (result) {
        if (successCallback) successCallback();
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.openUrl = function(url, target, successCallback, errorCallback) {
    var result = window.NativeInterface.openIntent(url);
    if (result) {
        if (successCallback) successCallback();
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.updateMediaSession = function(options, successCallback, errorCallback) {
    var result = window.NativeInterface.updateMediaSession(JSON.stringify(options));
    if (result) {
        if (successCallback) successCallback();
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.hideMediaSession = function(successCallback, errorCallback) {
    var result = window.NativeInterface.hideMediaSession();
    if (result) {
        if (successCallback) successCallback();
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.downloadFile = function(options, successCallback, errorCallback) {
    var result = window.NativeInterface.downloadFile(JSON.stringify(options));
    if (result) {
        if (successCallback) successCallback();
    } else {
        if (errorCallback) errorCallback();
    }
};

NativeShell.prototype.getPlugins = function() {
    return [];
};

window.NativeShell = new NativeShell();