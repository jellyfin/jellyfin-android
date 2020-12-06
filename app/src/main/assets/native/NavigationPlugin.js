export class NavigationPlugin {
    constructor({ playbackManager }) {
        window['NavigationHelper'] = this;

        this.playbackManager = playbackManager;
    }

    goBack() {
        var appRouter = window['Emby']['Page'];
        if (appRouter.canGoBack()) {
            appRouter.back();
        } else {
            window['NativeInterface'].exitApp();
        }
    }
}
