package org.jellyfin.mobile.player.interaction

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class PlayerWebPreferences(
    val maxStreamingBitrateLocal: Int,
    val maxStreamingBitrateRemote: Int,
) : Parcelable
