package org.jellyfin.mobile.settings;


import androidx.annotation.StringDef;

@StringDef({
    PreferredAudioCodec.AUTO,
    PreferredAudioCodec.AAC,
    PreferredAudioCodec.AC3,
    PreferredAudioCodec.MP3
})
public @interface PreferredAudioCodec {
    String AUTO = "auto";
    String AAC = "aac";
    String AC3 = "ac3";
    String MP3 = "mp3";
}
