package org.jellyfin.mobile.settings;


import androidx.annotation.StringDef;

@StringDef({
    ExternalPlayerPackage.MPV_PLAYER,
    ExternalPlayerPackage.MX_PLAYER_FREE,
    ExternalPlayerPackage.MX_PLAYER_PRO,
    ExternalPlayerPackage.VLC_PLAYER,
    ExternalPlayerPackage.SYSTEM_DEFAULT
})
public @interface ExternalPlayerPackage {
    String MPV_PLAYER = "is.xyz.mpv";
    String MX_PLAYER_FREE = "com.mxtech.videoplayer.ad";
    String MX_PLAYER_PRO = "com.mxtech.videoplayer.pro";
    String VLC_PLAYER = "org.videolan.vlc";
    String SYSTEM_DEFAULT = "~system~";
}
