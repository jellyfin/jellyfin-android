package org.jellyfin.mobile.settings;


import androidx.annotation.StringDef;

@StringDef({
    MaxTranscodeResolution.AUTO,
    MaxTranscodeResolution.P1080,
    MaxTranscodeResolution.P720,
    MaxTranscodeResolution.P480
})
public @interface MaxTranscodeResolution {
    String AUTO = "auto";
    String P1080 = "1080";
    String P720 = "720";
    String P480 = "480";
}
