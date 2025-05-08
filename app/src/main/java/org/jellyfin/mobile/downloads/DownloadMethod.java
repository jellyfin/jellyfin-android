package org.jellyfin.mobile.downloads;

import static org.jellyfin.mobile.downloads.DownloadMethod.MOBILE_AND_ROAMING;
import static org.jellyfin.mobile.downloads.DownloadMethod.MOBILE_DATA;
import static org.jellyfin.mobile.downloads.DownloadMethod.WIFI_ONLY;

import androidx.annotation.IntDef;

@IntDef({WIFI_ONLY, MOBILE_DATA, MOBILE_AND_ROAMING})
public @interface DownloadMethod {
    int WIFI_ONLY = 0;
    int MOBILE_DATA = 1;
    int MOBILE_AND_ROAMING = 2;
}
