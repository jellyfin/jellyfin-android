define(['inputManager', 'playbackManager'], function (inputManager, playbackManager) {
    "use strict";

    return function () {
        window.NavigationHelper = this;

        this.goBack = function () {
            inputManager.trigger('back');
        };

        this.playbackManager = playbackManager;
    };
});
