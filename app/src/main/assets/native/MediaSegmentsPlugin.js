export class MediaSegmentsPlugin {
    SETTING_PREFIX = 'segmentTypeAction';

    constructor({ events, appSettings, dashboard, ServerConnections }) {
        this.appSettings = appSettings;
        this.dashboard = dashboard;

        events.on(ServerConnections, 'localusersignedin', (_, user) => this.onUserSignedIn());
        events.on(appSettings, 'change', (_, name) => this.onSettingsChanged(name));
    }

    getSettingId(type) {
        return `${this.SETTING_PREFIX}__${type}`;
    }

    getSettingValue(id) {
        var userId = this.dashboard.getCurrentUserId();

        return this.appSettings.get(id, userId);
    }

    // Update all media segment actions on initialization
    onUserSignedIn() {
        var supportedSegmentTypes = JSON.parse(MediaSegments.getSupportedSegmentTypes());

        for (const type of supportedSegmentTypes) {
            var action = this.getSettingValue(this.getSettingId(type));

            if (type != null && action != null) {
                MediaSegments.setSegmentTypeAction(type, action);
            }
        }
    }

    // Update media segment action
    onSettingsChanged(name) {
        if (name.startsWith(this.SETTING_PREFIX)) {
            var type = name.slice(this.SETTING_PREFIX.length + 2);
            var action = this.getSettingValue(this.getSettingId(type));

            if (type != null && action != null) {
                MediaSegments.setSegmentTypeAction(type, action);
            }
        }
    }
}
