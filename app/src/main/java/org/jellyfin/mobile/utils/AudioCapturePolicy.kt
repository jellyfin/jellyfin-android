package org.jellyfin.mobile.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

fun Context.allowPlaybackCaptureByAll() {
    if (AndroidVersion.isAtLeastQ) {
        getSystemService(AudioManager::class.java)
            .allowedCapturePolicy = AudioAttributes.ALLOW_CAPTURE_BY_ALL
    }
}

