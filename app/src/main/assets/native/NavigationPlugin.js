export class NavigationPlugin {
    constructor({ playbackManager }) {
        window['NavigationHelper'] = this;

        this.playbackManager = playbackManager;
    }

    async goBack() {
        if (!window.ApiClient._currentUser) {
            window['NativeInterface'].exitApp();
        } else {
            window.ApiClient.sendCommand(await window.NativeShell.getSavedSessionId(), { "Name": "Back" });
        }
    }
}
