package org.jellyfin.mobile.cast;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueData;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.cast.TextTrackStyle;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.common.images.WebImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class ChromecastUtilities {
    /**
     * Stores a cache of the queueItems for building Media Objects.
     */
    private static JSONArray queueItems = null;

    private ChromecastUtilities() {
        //not called
    }

    /**
     * Sets the queueItems to be returned with the media object so they don't have to be calculated
     * every time we need to send an update.
     *
     * @param items queueItems
     */
    static void setQueueItems(JSONArray items) {
        queueItems = items;
    }

    static String getMediaIdleReason(int idleReason) {
        switch (idleReason) {
            case MediaStatus.IDLE_REASON_CANCELED:
                return "CANCELLED";
            case MediaStatus.IDLE_REASON_ERROR:
                return "ERROR";
            case MediaStatus.IDLE_REASON_FINISHED:
                return "FINISHED";
            case MediaStatus.IDLE_REASON_INTERRUPTED:
                return "INTERRUPTED";
            case MediaStatus.IDLE_REASON_NONE:
            default:
                return null;
        }
    }

    static String getMediaPlayerState(int playerState) {
        switch (playerState) {
            case MediaStatus.PLAYER_STATE_LOADING:
            case MediaStatus.PLAYER_STATE_BUFFERING:
                return "BUFFERING";
            case MediaStatus.PLAYER_STATE_IDLE:
                return "IDLE";
            case MediaStatus.PLAYER_STATE_PAUSED:
                return "PAUSED";
            case MediaStatus.PLAYER_STATE_PLAYING:
                return "PLAYING";
            case MediaStatus.PLAYER_STATE_UNKNOWN:
                return "UNKNOWN";
            default:
                return null;
        }
    }

    static String getMediaInfoStreamType(MediaInfo mediaInfo) {
        switch (mediaInfo.getStreamType()) {
            case MediaInfo.STREAM_TYPE_BUFFERED:
                return "BUFFERED";
            case MediaInfo.STREAM_TYPE_LIVE:
                return "LIVE";
            case MediaInfo.STREAM_TYPE_NONE:
                return "OTHER";
            default:
                return null;
        }
    }

    static String getTrackType(MediaTrack track) {
        switch (track.getType()) {
            case MediaTrack.TYPE_AUDIO:
                return "AUDIO";
            case MediaTrack.TYPE_TEXT:
                return "TEXT";
            case MediaTrack.TYPE_VIDEO:
                return "VIDEO";
            default:
                return null;
        }
    }

    static String getTrackSubtype(MediaTrack track) {
        switch (track.getSubtype()) {
            case MediaTrack.SUBTYPE_CAPTIONS:
                return "CAPTIONS";
            case MediaTrack.SUBTYPE_CHAPTERS:
                return "CHAPTERS";
            case MediaTrack.SUBTYPE_DESCRIPTIONS:
                return "DESCRIPTIONS";
            case MediaTrack.SUBTYPE_METADATA:
                return "METADATA";
            case MediaTrack.SUBTYPE_SUBTITLES:
                return "SUBTITLES";
            case MediaTrack.SUBTYPE_NONE:
            default:
                return null;
        }
    }

    static String getEdgeType(TextTrackStyle textTrackStyle) {
        switch (textTrackStyle.getEdgeType()) {
            case TextTrackStyle.EDGE_TYPE_DEPRESSED:
                return "DEPRESSED";
            case TextTrackStyle.EDGE_TYPE_DROP_SHADOW:
                return "DROP_SHADOW";
            case TextTrackStyle.EDGE_TYPE_OUTLINE:
                return "OUTLINE";
            case TextTrackStyle.EDGE_TYPE_RAISED:
                return "RAISED";
            case TextTrackStyle.EDGE_TYPE_NONE:
            default:
                return "NONE";
        }
    }

    static String getFontGenericFamily(TextTrackStyle textTrackStyle) {
        switch (textTrackStyle.getFontGenericFamily()) {
            case TextTrackStyle.FONT_FAMILY_CURSIVE:
                return "CURSIVE";
            case TextTrackStyle.FONT_FAMILY_MONOSPACED_SANS_SERIF:
                return "MONOSPACED_SANS_SERIF";
            case TextTrackStyle.FONT_FAMILY_MONOSPACED_SERIF:
                return "MONOSPACED_SERIF";
            case TextTrackStyle.FONT_FAMILY_SANS_SERIF:
                return "SANS_SERIF";
            case TextTrackStyle.FONT_FAMILY_SMALL_CAPITALS:
                return "SMALL_CAPITALS";
            case TextTrackStyle.FONT_FAMILY_SERIF:
            default:
                return "SERIF";
        }
    }

    static String getFontStyle(TextTrackStyle textTrackStyle) {
        switch (textTrackStyle.getFontStyle()) {
            case TextTrackStyle.FONT_STYLE_BOLD:
                return "BOLD";
            case TextTrackStyle.FONT_STYLE_BOLD_ITALIC:
                return "BOLD_ITALIC";
            case TextTrackStyle.FONT_STYLE_ITALIC:
                return "ITALIC";
            case TextTrackStyle.FONT_STYLE_UNSPECIFIED:
            case TextTrackStyle.FONT_STYLE_NORMAL:
            default:
                return "NORMAL";
        }
    }

    static String getWindowType(TextTrackStyle textTrackStyle) {
        switch (textTrackStyle.getWindowType()) {
            case TextTrackStyle.WINDOW_TYPE_NORMAL:
                return "NORMAL";
            case TextTrackStyle.WINDOW_TYPE_ROUNDED:
                return "ROUNDED_CORNERS";
            case TextTrackStyle.WINDOW_TYPE_NONE:
            default:
                return "NONE";
        }
    }

    static String getRepeatMode(int repeatMode) {
        switch (repeatMode) {
            case MediaStatus.REPEAT_MODE_REPEAT_OFF:
                return "REPEAT_OFF";
            case MediaStatus.REPEAT_MODE_REPEAT_ALL:
                return "REPEAT_ALL";
            case MediaStatus.REPEAT_MODE_REPEAT_SINGLE:
                return "REPEAT_SINGLE";
            case MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE:
                return "REPEAT_ALL_AND_SHUFFLE";
            default:
                return null;
        }
    }

    static int getAndroidRepeatMode(String clientRepeatMode) throws JSONException {
        switch (clientRepeatMode) {
            case "REPEAT_OFF":
                return MediaStatus.REPEAT_MODE_REPEAT_OFF;
            case "REPEAT_ALL":
                return MediaStatus.REPEAT_MODE_REPEAT_ALL;
            case "REPEAT_SINGLE":
                return MediaStatus.REPEAT_MODE_REPEAT_SINGLE;
            case "REPEAT_ALL_AND_SHUFFLE":
                return MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE;
            default:
                throw new JSONException("Invalid repeat mode: " + clientRepeatMode);
        }
    }

    static String getAndroidMetadataName(String clientName) {
        switch (clientName) {
            case "albumArtist":
                return MediaMetadata.KEY_ALBUM_ARTIST;
            case "albumName":
                return MediaMetadata.KEY_ALBUM_TITLE;
            case "artist":
                return MediaMetadata.KEY_ARTIST;
            case "bookTitle":
                return MediaMetadata.KEY_BOOK_TITLE;
            case "broadcastDate":
                return MediaMetadata.KEY_BROADCAST_DATE;
            case "chapterNumber":
                return MediaMetadata.KEY_CHAPTER_NUMBER;
            case "chapterTitle":
                return MediaMetadata.KEY_CHAPTER_TITLE;
            case "composer":
                return MediaMetadata.KEY_COMPOSER;
            case "creationDate":
            case "creationDateTime":
                return MediaMetadata.KEY_CREATION_DATE;
            case "discNumber":
                return MediaMetadata.KEY_DISC_NUMBER;
            case "episode":
                return MediaMetadata.KEY_EPISODE_NUMBER;
            case "height":
                return MediaMetadata.KEY_HEIGHT;
            case "latitude":
                return MediaMetadata.KEY_LOCATION_LATITUDE;
            case "longitude":
                return MediaMetadata.KEY_LOCATION_LONGITUDE;
            case "locationName":
                return MediaMetadata.KEY_LOCATION_NAME;
            case "queueItemId":
                return MediaMetadata.KEY_QUEUE_ITEM_ID;
            case "releaseDate":
            case "originalAirDate":
                return MediaMetadata.KEY_RELEASE_DATE;
            case "season":
                return MediaMetadata.KEY_SEASON_NUMBER;
            case "sectionDuration":
                return MediaMetadata.KEY_SECTION_DURATION;
            case "sectionStartAbsoluteTime":
                return MediaMetadata.KEY_SECTION_START_ABSOLUTE_TIME;
            case "sectionStartTimeInContainer":
                return MediaMetadata.KEY_SECTION_START_TIME_IN_CONTAINER;
            case "sectionStartTimeInMedia":
                return MediaMetadata.KEY_SECTION_START_TIME_IN_MEDIA;
            case "seriesTitle":
                return MediaMetadata.KEY_SERIES_TITLE;
            case "studio":
                return MediaMetadata.KEY_STUDIO;
            case "subtitle":
                return MediaMetadata.KEY_SUBTITLE;
            case "title":
                return MediaMetadata.KEY_TITLE;
            case "trackNumber":
                return MediaMetadata.KEY_TRACK_NUMBER;
            case "width":
                return MediaMetadata.KEY_WIDTH;
            default:
                return clientName;
        }
    }

    static String getClientMetadataName(String androidName) {
        switch (androidName) {
            case MediaMetadata.KEY_ALBUM_ARTIST:
                return "albumArtist";
            case MediaMetadata.KEY_ALBUM_TITLE:
                return "albumName";
            case MediaMetadata.KEY_ARTIST:
                return "artist";
            case MediaMetadata.KEY_BOOK_TITLE:
                return "bookTitle";
            case MediaMetadata.KEY_BROADCAST_DATE:
                return "broadcastDate";
            case MediaMetadata.KEY_CHAPTER_NUMBER:
                return "chapterNumber";
            case MediaMetadata.KEY_CHAPTER_TITLE:
                return "chapterTitle";
            case MediaMetadata.KEY_COMPOSER:
                return "composer";
            case MediaMetadata.KEY_CREATION_DATE:
                return "creationDate";
            case MediaMetadata.KEY_DISC_NUMBER:
                return "discNumber";
            case MediaMetadata.KEY_EPISODE_NUMBER:
                return "episode";
            case MediaMetadata.KEY_HEIGHT:
                return "height";
            case MediaMetadata.KEY_LOCATION_LATITUDE:
                return "latitude";
            case MediaMetadata.KEY_LOCATION_LONGITUDE:
                return "longitude";
            case MediaMetadata.KEY_LOCATION_NAME:
                return "location";
            case MediaMetadata.KEY_QUEUE_ITEM_ID:
                return "queueItemId";
            case MediaMetadata.KEY_RELEASE_DATE:
                return "releaseDate";
            case MediaMetadata.KEY_SEASON_NUMBER:
                return "season";
            case MediaMetadata.KEY_SECTION_DURATION:
                return "sectionDuration";
            case MediaMetadata.KEY_SECTION_START_ABSOLUTE_TIME:
                return "sectionStartAbsoluteTime";
            case MediaMetadata.KEY_SECTION_START_TIME_IN_CONTAINER:
                return "sectionStartTimeInContainer";
            case MediaMetadata.KEY_SECTION_START_TIME_IN_MEDIA:
                return "sectionStartTimeInMedia";
            case MediaMetadata.KEY_SERIES_TITLE:
                return "seriesTitle";
            case MediaMetadata.KEY_STUDIO:
                return "studio";
            case MediaMetadata.KEY_SUBTITLE:
                return "subtitle";
            case MediaMetadata.KEY_TITLE:
                return "title";
            case MediaMetadata.KEY_TRACK_NUMBER:
                return "trackNumber";
            case MediaMetadata.KEY_WIDTH:
                return "width";
            default:
                return androidName;
        }
    }

    static String getMetadataType(String androidName) {
        switch (androidName) {
            case MediaMetadata.KEY_ALBUM_ARTIST:
            case MediaMetadata.KEY_ALBUM_TITLE:
            case MediaMetadata.KEY_ARTIST:
            case MediaMetadata.KEY_BOOK_TITLE:
            case MediaMetadata.KEY_CHAPTER_NUMBER:
            case MediaMetadata.KEY_CHAPTER_TITLE:
            case MediaMetadata.KEY_COMPOSER:
            case MediaMetadata.KEY_LOCATION_NAME:
            case MediaMetadata.KEY_SERIES_TITLE:
            case MediaMetadata.KEY_STUDIO:
            case MediaMetadata.KEY_SUBTITLE:
            case MediaMetadata.KEY_TITLE:
                return "string"; // 1 in MediaMetadata
            case MediaMetadata.KEY_DISC_NUMBER:
            case MediaMetadata.KEY_EPISODE_NUMBER:
            case MediaMetadata.KEY_HEIGHT:
            case MediaMetadata.KEY_QUEUE_ITEM_ID:
            case MediaMetadata.KEY_SEASON_NUMBER:
            case MediaMetadata.KEY_TRACK_NUMBER:
            case MediaMetadata.KEY_WIDTH:
                return "int"; // 2 in MediaMetadata
            case MediaMetadata.KEY_LOCATION_LATITUDE:
            case MediaMetadata.KEY_LOCATION_LONGITUDE:
                return "double"; // 3 in MediaMetadata
            case MediaMetadata.KEY_BROADCAST_DATE:
            case MediaMetadata.KEY_CREATION_DATE:
            case MediaMetadata.KEY_RELEASE_DATE:
                return "date"; // 4 in MediaMetadata
            case MediaMetadata.KEY_SECTION_DURATION:
            case MediaMetadata.KEY_SECTION_START_ABSOLUTE_TIME:
            case MediaMetadata.KEY_SECTION_START_TIME_IN_CONTAINER:
            case MediaMetadata.KEY_SECTION_START_TIME_IN_MEDIA:
                return "ms"; // 5 in MediaMetadata
            default:
                return "custom";
        }
    }

    static TextTrackStyle parseTextTrackStyle(JSONObject textTrackSytle) {
        TextTrackStyle out = new TextTrackStyle();

        if (textTrackSytle == null) {
            return out;
        }

        try {
            if (!textTrackSytle.isNull("backgroundColor")) {
                out.setBackgroundColor(Color.parseColor(textTrackSytle.getString("backgroundColor")));
            }

            if (!textTrackSytle.isNull("edgeColor")) {
                out.setEdgeColor(Color.parseColor(textTrackSytle.getString("edgeColor")));
            }

            if (!textTrackSytle.isNull("foregroundColor")) {
                out.setForegroundColor(Color.parseColor(textTrackSytle.getString("foregroundColor")));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static String getHexColor(int color) {
        return "#" + Integer.toHexString(color);
    }

    static JSONObject createSessionObject(CastSession session, String state) {
        JSONObject s = createSessionObject(session);
        if (state != null) {
            try {
                s.put("status", state);
            } catch (JSONException ignored) {
            }
        }
        return s;
    }

    static JSONObject createSessionObject(CastSession session) {
        JSONObject out = new JSONObject();
        try {
            ApplicationMetadata metadata = session.getApplicationMetadata();
            if (metadata != null) {
                out.put("appId", metadata.getApplicationId());
                out.put("appImages", createImagesArray(metadata.getImages()));
                out.put("displayName", metadata.getName());
                out.put("media", createMediaArray(session));
                out.put("receiver", createReceiverObject(session));
                out.put("sessionId", session.getSessionId());
            }
        } catch (JSONException | NullPointerException | IllegalStateException ignored) {
        }
        return out;
    }

    private static JSONArray createImagesArray(List<WebImage> images) throws JSONException {
        JSONArray appImages = new JSONArray();
        JSONObject img;
        for (WebImage o : images) {
            img = new JSONObject();
            img.put("url", o.getUrl().toString());
            appImages.put(img);
        }
        return appImages;
    }

    private static JSONObject createReceiverObject(CastSession session) {
        JSONObject out = new JSONObject();
        try {
            out.put("friendlyName", session.getCastDevice().getFriendlyName());
            out.put("label", session.getCastDevice().getDeviceId());

            JSONObject volume = new JSONObject();
            volume.put("level", session.getVolume());
            volume.put("muted", session.isMute());
            out.put("volume", volume);
        } catch (JSONException | NullPointerException ignored) {
        }
        return out;
    }

    static JSONArray createMediaArray(CastSession session) {
        JSONArray out = new JSONArray();
        JSONObject mediaInfoObj = createMediaObject(session);
        if (mediaInfoObj != null) {
            out.put(mediaInfoObj);
        }
        return out;
    }

    static JSONObject createMediaObject(CastSession session) {
        return createMediaObject(session, queueItems);
    }

    static JSONObject createMediaObject(CastSession session, JSONArray items) {
        JSONObject out = new JSONObject();

        try {
            MediaStatus mediaStatus = session.getRemoteMediaClient().getMediaStatus();

            // TODO: Missing attributes are commented out.
            //  These are returned by the chromecast desktop SDK, we should probbaly return them too
            //out.put("breakStatus",);
            out.put("currentItemId", mediaStatus.getCurrentItemId());
            out.put("currentTime", mediaStatus.getStreamPosition() / 1000.0);
            out.put("customData", mediaStatus.getCustomData());
            //out.put("extendedStatus",);
            String idleReason = ChromecastUtilities.getMediaIdleReason(mediaStatus.getIdleReason());
            if (idleReason != null) {
                out.put("idleReason", idleReason);
            }
            out.put("items", items);
            out.put("isAlive", mediaStatus.getPlayerState() != MediaStatus.PLAYER_STATE_IDLE);
            //out.put("liveSeekableRange",);
            out.put("loadingItemId", mediaStatus.getLoadingItemId());
            out.put("media", createMediaInfoObject(session.getRemoteMediaClient().getMediaInfo()));
            out.put("mediaSessionId", 1);
            out.put("playbackRate", mediaStatus.getPlaybackRate());
            out.put("playerState", ChromecastUtilities.getMediaPlayerState(mediaStatus.getPlayerState()));
            out.put("preloadedItemId", mediaStatus.getPreloadedItemId());
            out.put("queueData", createQueueData(mediaStatus));
            out.put("repeatMode", getRepeatMode(mediaStatus.getQueueRepeatMode()));
            out.put("sessionId", session.getSessionId());
            //out.put("supportedMediaCommands", );
            //out.put("videoInfo", );

            JSONObject volume = new JSONObject();
            volume.put("level", mediaStatus.getStreamVolume());
            volume.put("muted", mediaStatus.isMute());
            out.put("volume", volume);
            out.put("activeTrackIds", createActiveTrackIds(mediaStatus.getActiveTrackIds()));
        } catch (JSONException ignored) {
        } catch (NullPointerException e) {
            return null;
        }

        return out;
    }

    private static JSONArray createActiveTrackIds(long[] activeTrackIds) {
        JSONArray out = new JSONArray();
        try {
            if (activeTrackIds.length == 0) {
                return null;
            }
            for (long id : activeTrackIds) {
                out.put(id);
            }
        } catch (NullPointerException e) {
            return null;
        }
        return out;
    }

    static JSONObject createQueueData(MediaStatus status) {
        JSONObject out = new JSONObject();
        try {
            MediaQueueData data = status.getQueueData();
            if (data == null) {
                return null;
            }
            out.put("repeatMode", ChromecastUtilities.getRepeatMode(data.getRepeatMode()));
            out.put("shuffle", data.getRepeatMode() == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE);
            out.put("startIndex", data.getStartIndex());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("See above stack trace for error: " + e.getMessage());
        }
        return out;
    }

    static JSONObject createQueueItem(@NonNull MediaQueueItem item, int orderId) {
        JSONObject out = new JSONObject();
        try {
            out.put("activeTrackIds", createActiveTrackIds(item.getActiveTrackIds()));
            out.put("autoplay", item.getAutoplay());
            out.put("customData", item.getCustomData());
            out.put("itemId", item.getItemId());
            out.put("media", createMediaInfoObject(item.getMedia()));
            out.put("orderId", orderId);
            Double playbackDuration = item.getPlaybackDuration();
            if (Double.isInfinite(playbackDuration)) {
                playbackDuration = null;
            }
            out.put("playbackDuration", playbackDuration);
            out.put("preloadTime", item.getPreloadTime());
            Double startTime = item.getStartTime();
            if (Double.isNaN(startTime)) {
                startTime = null;
            }
            out.put("startTime", startTime);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("See above stack trace for error: " + e.getMessage());
        }
        return out;
    }

    private static JSONArray createMediaInfoTracks(MediaInfo mediaInfo) {
        JSONArray out = new JSONArray();

        try {
            if (mediaInfo.getMediaTracks() == null) {
                return out;
            }

            for (MediaTrack track : mediaInfo.getMediaTracks()) {
                JSONObject jsonTrack = new JSONObject();


                // TODO: Missing attributes are commented out.
                //  These are returned by the chromecast desktop SDK, we should probbaly return them too

                jsonTrack.put("trackId", track.getId());
                jsonTrack.put("customData", track.getCustomData());
                jsonTrack.put("language", track.getLanguage());
                jsonTrack.put("name", track.getName());
                jsonTrack.put("subtype", ChromecastUtilities.getTrackSubtype(track));
                jsonTrack.put("trackContentId", track.getContentId());
                jsonTrack.put("trackContentType", track.getContentType());
                jsonTrack.put("type", ChromecastUtilities.getTrackType(track));

                out.put(jsonTrack);
            }
        } catch (JSONException | NullPointerException ignored) {
        }

        return out;
    }

    private static JSONObject createMediaInfoObject(MediaInfo mediaInfo) {
        JSONObject out = new JSONObject();

        try {
            // TODO: Missing attributes are commented out.
            //  These are returned by the chromecast desktop SDK, we should probably return them too
            //out.put("breakClips",);
            //out.put("breaks",);
            out.put("contentId", mediaInfo.getContentId());
            out.put("contentType", mediaInfo.getContentType());
            out.put("customData", mediaInfo.getCustomData());
            out.put("duration", mediaInfo.getStreamDuration() / 1000.0);
            //out.put("mediaCategory",);
            out.put("metadata", createMetadataObject(mediaInfo.getMetadata()));
            out.put("streamType", ChromecastUtilities.getMediaInfoStreamType(mediaInfo));
            out.put("tracks", createMediaInfoTracks(mediaInfo));
            out.put("textTrackStyle", ChromecastUtilities.createTextTrackObject(mediaInfo.getTextTrackStyle()));

        } catch (JSONException | NullPointerException ignored) {
        }

        return out;
    }

    static JSONObject createMetadataObject(MediaMetadata metadata) {
        JSONObject out = new JSONObject();
        if (metadata == null) {
            return out;
        }
        try {
            try {
                // Must be in own try catch
                out.put("images", createImagesArray(metadata.getImages()));
            } catch (Exception ignored) {
            }
            out.put("metadataType", metadata.getMediaType());
            out.put("type", metadata.getMediaType());

            Set<String> keys = metadata.keySet();
            String outKey;
            // First translate and add the Android specific keys
            for (String key : keys) {
                outKey = ChromecastUtilities.getClientMetadataName(key);
                if (outKey.equals(key) || outKey.equals("type")) {
                    continue;
                }
                switch (ChromecastUtilities.getMetadataType(key)) {
                    case "string":
                        out.put(outKey, metadata.getString(key));
                        break;
                    case "int":
                        out.put(outKey, metadata.getInt(key));
                        break;
                    case "double":
                        out.put(outKey, metadata.getDouble(key));
                        break;
                    case "date":
                        out.put(outKey, metadata.getDate(key).getTimeInMillis());
                        break;
                    case "ms":
                        out.put(outKey, metadata.getTimeMillis(key));
                        break;
                    default:
                }
            }
            // Then add the non-Android specific keys ensuring we don't overwrite existing keys
            for (String key : keys) {
                outKey = ChromecastUtilities.getClientMetadataName(key);
                if (!outKey.equals(key) || out.has(outKey) || outKey.equals("type")) {
                    continue;
                }
                if (outKey.startsWith("cordova-plugin-chromecast_metadata_key=")) {
                    outKey = outKey.substring("cordova-plugin-chromecast_metadata_key=".length());
                }
                out.put(outKey, metadata.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out;
    }

    private static JSONObject createTextTrackObject(TextTrackStyle textTrackStyle) {
        if (textTrackStyle == null) {
            return null;
        }
        JSONObject out = new JSONObject();
        try {
            out.put("backgroundColor", getHexColor(textTrackStyle.getBackgroundColor()));
            out.put("customData", textTrackStyle.getCustomData());
            out.put("edgeColor", getHexColor(textTrackStyle.getEdgeColor()));
            out.put("edgeType", getEdgeType(textTrackStyle));
            out.put("fontFamily", textTrackStyle.getFontFamily());
            out.put("fontGenericFamily", getFontGenericFamily(textTrackStyle));
            out.put("fontScale", textTrackStyle.getFontScale());
            out.put("fontStyle", getFontStyle(textTrackStyle));
            out.put("foregroundColor", getHexColor(textTrackStyle.getForegroundColor()));
            out.put("windowColor", getHexColor(textTrackStyle.getWindowColor()));
            out.put("windowRoundedCornerRadius", textTrackStyle.getWindowCornerRadius());
            out.put("windowType", getWindowType(textTrackStyle));
        } catch (JSONException ignored) {
        }
        return out;
    }

    /**
     * Simple helper to convert a route to JSON for passing down to the javascript side.
     *
     * @param routes the routes to convert
     * @return a JSON Array of JSON representations of the routes
     */
    @SuppressLint("RestrictedApi")
    static JSONArray createRoutesArray(List<MediaRouter.RouteInfo> routes) {
        JSONArray routesArray = new JSONArray();
        for (MediaRouter.RouteInfo route : routes) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", route.getName());
                obj.put("id", route.getId());

                CastDevice device = CastDevice.getFromBundle(route.getExtras());
                if (device != null) {
                    obj.put("isNearbyDevice", !device.isOnLocalNetwork());
                    obj.put("isCastGroup", route.isGroup());
                }

                routesArray.put(obj);
            } catch (JSONException ignored) {
            }
        }
        return routesArray;
    }

    static JSONObject createError(String code, String message) {
        JSONObject out = new JSONObject();
        try {
            out.put("code", code);
            out.put("description", message);
        } catch (JSONException ignored) {
        }
        return out;
    }

    /* -------------------   Create NON-JSON (non-output) Objects  ---------------------------------- */

    /**
     * Creates a MediaQueueItem from a JSONObject representation of a MediaQueueItem.
     *
     * @param mediaQueueItem a JSONObject representation of a MediaQueueItem
     * @return a MediaQueueItem
     * @throws JSONException If the input mediaQueueItem is incorrect
     */
    static MediaQueueItem createMediaQueueItem(JSONObject mediaQueueItem) throws JSONException {
        MediaInfo mediaInfo = createMediaInfo(mediaQueueItem.getJSONObject("media"));
        MediaQueueItem.Builder builder = new MediaQueueItem.Builder(mediaInfo);

        try {
            long[] activeTrackIds;
            JSONArray trackIds = mediaQueueItem.getJSONArray("activeTrackIds");
            activeTrackIds = new long[trackIds.length()];
            for (int i = 0; i < trackIds.length(); i++) {
                activeTrackIds[i] = trackIds.getLong(i);
            }
            builder.setActiveTrackIds(activeTrackIds);
        } catch (JSONException ignored) {
        }
        try {
            builder.setAutoplay(mediaQueueItem.getBoolean("autoplay"));
        } catch (JSONException ignored) {
        }
        try {
            builder.setPlaybackDuration(mediaQueueItem.getDouble("playbackDuration"));
        } catch (JSONException ignored) {
        }
        try {
            builder.setPreloadTime(mediaQueueItem.getDouble("preloadTime"));
        } catch (JSONException ignored) {
        }
        try {
            builder.setStartTime(mediaQueueItem.getDouble("startTime"));
        } catch (JSONException ignored) {
        }
        return builder.build();
    }

    static MediaInfo createMediaInfo(JSONObject mediaInfo) {
        String contentId = mediaInfo.optString("contentId", "");
        JSONObject customData = mediaInfo.optJSONObject("customData");
        if (customData == null) customData = new JSONObject();
        String contentType = mediaInfo.optString("contentType", "unknown");
        long duration = mediaInfo.optLong("duration");
        String streamType = mediaInfo.optString("streamType", "unknown");
        JSONObject metadata = mediaInfo.optJSONObject("metadata");
        if (metadata == null) metadata = new JSONObject();
        JSONObject textTrackStyle = mediaInfo.optJSONObject("textTrackStyle");
        if (textTrackStyle == null) textTrackStyle = new JSONObject();
        return createMediaInfo(contentId, customData, contentType, duration, streamType, metadata, textTrackStyle);
    }

    static MediaInfo createMediaInfo(String contentId, JSONObject customData, String contentType, long duration, String streamType, JSONObject metadata, JSONObject textTrackStyle) {
        MediaInfo.Builder mediaInfoBuilder = new MediaInfo.Builder(contentId);

        mediaInfoBuilder.setMetadata(createMediaMetadata(metadata));

        int intStreamType;
        switch (streamType) {
            case "buffered":
                intStreamType = MediaInfo.STREAM_TYPE_BUFFERED;
                break;
            case "live":
                intStreamType = MediaInfo.STREAM_TYPE_LIVE;
                break;
            default:
                intStreamType = MediaInfo.STREAM_TYPE_NONE;
        }

        TextTrackStyle trackStyle = ChromecastUtilities.parseTextTrackStyle(textTrackStyle);

        mediaInfoBuilder
                .setContentType(contentType)
                .setCustomData(customData)
                .setStreamType(intStreamType)
                .setStreamDuration(duration)
                .setTextTrackStyle(trackStyle);

        return mediaInfoBuilder.build();
    }

    private static MediaMetadata createMediaMetadata(JSONObject metadata) {

        MediaMetadata mediaMetadata;
        try {
            mediaMetadata = new MediaMetadata(metadata.getInt("metadataType"));
        } catch (JSONException e) {
            mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);
        }
        // Add any images
        try {
            JSONArray images = metadata.getJSONArray("images");
            for (int i = 0; i < images.length(); i++) {
                JSONObject imageObj = images.getJSONObject(i);
                try {
                    Uri imageURI = Uri.parse(imageObj.getString("url"));
                    mediaMetadata.addImage(new WebImage(imageURI));
                } catch (Exception ignored) {
                }
            }
        } catch (JSONException ignored) {
        }

        // Dynamically add other parameters
        Iterator<String> keys = metadata.keys();
        String key;
        String convertedKey;
        Object value;
        while (keys.hasNext()) {
            key = keys.next();
            if (key.equals("metadataType")
                    || key.equals("images")
                    || key.equals("type")) {
                continue;
            }
            try {
                value = metadata.get(key);
                convertedKey = ChromecastUtilities.getAndroidMetadataName(key);
                // Try to add the translated version of the key
                switch (ChromecastUtilities.getMetadataType(convertedKey)) {
                    case "string":
                        mediaMetadata.putString(convertedKey, metadata.getString(key));
                        break;
                    case "int":
                        mediaMetadata.putInt(convertedKey, metadata.getInt(key));
                        break;
                    case "double":
                        mediaMetadata.putDouble(convertedKey, metadata.getDouble(key));
                        break;
                    case "date":
                        GregorianCalendar c = new GregorianCalendar();
                        if (value instanceof java.lang.Integer
                                || value instanceof java.lang.Long
                                || value instanceof java.lang.Float
                                || value instanceof java.lang.Double) {
                            c.setTimeInMillis(metadata.getLong(key));
                            mediaMetadata.putDate(convertedKey, c);
                        } else {
                            String stringValue;
                            try {
                                stringValue = " value: " + metadata.getString(key);
                            } catch (JSONException e) {
                                stringValue = "";
                            }
                            new Error("Cannot date from metadata key: " + key + stringValue
                                    + "\n Dates must be in milliseconds from epoch UTC")
                                    .printStackTrace();
                        }
                        break;
                    case "ms":
                        mediaMetadata.putTimeMillis(convertedKey, metadata.getLong(key));
                        break;
                    default:
                }
                // Also always add the client's version of the key because sometimes the
                // MediaMetadata object removes some parameters.
                // eg. If you pass metadataType == 2 == MEDIA_TYPE_TV_SHOW you will lose any
                // subtitle added for "com.google.android.gms.cast.metadata.SUBTITLE", but this
                // is not in-line with chrome desktop which preserves the value.
                if (!key.equals(convertedKey)) {
                    // It is is really stubborn and if you try to add the key "subtitle" that is
                    // also stripped.  (Hence the "cordova-plugin-chromecast_metadata_key=" prefix
                    convertedKey = "cordova-plugin-chromecast_metadata_key=" + key;
                }
                mediaMetadata.putString(convertedKey, metadata.getString(key));
            } catch (JSONException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return mediaMetadata;
    }
}