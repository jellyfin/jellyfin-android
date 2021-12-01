package org.jellyfin.mobile.settings;


import androidx.annotation.StringDef;

@StringDef({
        VideoPlayerType.WEB_PLAYER,
        VideoPlayerType.EXO_PLAYER,
        VideoPlayerType.MPV_PLAYER,
        VideoPlayerType.EXTERNAL_PLAYER
})
public @interface VideoPlayerType {
    String WEB_PLAYER = "webui";
    String EXO_PLAYER = "exoplayer";
    String MPV_PLAYER = "mpvplayer";
    String EXTERNAL_PLAYER = "external";
}
