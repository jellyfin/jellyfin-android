package org.jellyfin.mobile.player.mediasegments

import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.utils.extensions.duration
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.operations.MediaSegmentsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

fun Map<MediaSegmentType, MediaSegmentAction>.toMediaSegmentActionsString() =
    map { "${it.key.serialName}=${it.value.name}" }
        .joinToString(",")

class MediaSegmentRepository : KoinComponent {
    companion object {
        /**
         * All media segments currently supported by the app.
         */
        val SupportedTypes = listOf(
            MediaSegmentType.INTRO,
            MediaSegmentType.OUTRO,
            MediaSegmentType.PREVIEW,
            MediaSegmentType.RECAP,
            MediaSegmentType.COMMERCIAL,
        )

        /**
         * The minimum duration for a media segment to allow the [MediaSegmentAction.SKIP] action.
         */
        val SkipMinDuration = 1.seconds
    }

    private val appPreferences: AppPreferences by inject()
    private val apiClient: ApiClient = get()
    private val mediaSegmentsApi: MediaSegmentsApi = apiClient.mediaSegmentsApi

    private val mediaTypeActions = mutableMapOf<MediaSegmentType, MediaSegmentAction>()

    init {
        restoreMediaTypeActions()
    }

    private fun restoreMediaTypeActions() {
        val restoredMediaTypeActions = appPreferences.mediaSegmentActions
            .split(",")
            .mapNotNull {
                runCatching {
                    val (type, action) = it.split('=', limit = 2)
                    MediaSegmentType.fromName(type) to MediaSegmentAction.valueOf(action)
                }.getOrNull()
            }

        mediaTypeActions.clear()
        mediaTypeActions.putAll(restoredMediaTypeActions)
    }

    private fun saveMediaTypeActions() {
        appPreferences.mediaSegmentActions = mediaTypeActions.toMediaSegmentActionsString()
    }

    fun getDefaultSegmentTypeAction(type: MediaSegmentType): MediaSegmentAction {
        // Always return no action for unsupported types
        if (!SupportedTypes.contains(type)) return MediaSegmentAction.NOTHING

        return mediaTypeActions.getOrDefault(type, MediaSegmentAction.NOTHING)
    }

    fun setDefaultSegmentTypeAction(type: MediaSegmentType, action: MediaSegmentAction) {
        // Don't allow modifying actions for unsupported types
        if (!SupportedTypes.contains(type)) return

        mediaTypeActions[type] = action
        saveMediaTypeActions()
    }

    fun getMediaSegmentAction(segment: MediaSegmentDto): MediaSegmentAction {
        val action = getDefaultSegmentTypeAction(segment.type)
        // Skip the skip action if timespan is too short
        if (action == MediaSegmentAction.SKIP && segment.duration < SkipMinDuration) return MediaSegmentAction.NOTHING
        return action
    }

    suspend fun getSegmentsForItem(item: BaseItemDto): List<MediaSegmentDto> = runCatching {
        mediaSegmentsApi.getItemSegments(
            itemId = item.id,
            includeSegmentTypes = SupportedTypes,
        ).content.items
    }.getOrDefault(emptyList())
}
