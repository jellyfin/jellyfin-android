package org.jellyfin.mobile.utils;

import androidx.annotation.IntDef;

import static org.jellyfin.mobile.utils.DownloadMethod.MOBILE_AND_ROAMING;
import static org.jellyfin.mobile.utils.DownloadMethod.MOBILE_DATA;
import static org.jellyfin.mobile.utils.DownloadMethod.WIFI_ONLY;

@IntDef({WIFI_ONLY, MOBILE_DATA, MOBILE_AND_ROAMING})
public @interface DownloadMethod {
    int WIFI_ONLY = 0;
    int MOBILE_DATA = 1;
    int MOBILE_AND_ROAMING = 2;
}