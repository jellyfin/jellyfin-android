package org.jellyfin.android.cast;

import android.app.Activity;

import androidx.mediarouter.media.MediaRouter.RouteInfo;

import com.google.android.gms.cast.CastDevice;

import org.jellyfin.android.bridge.JavascriptCallback;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import timber.log.Timber;

@SuppressWarnings("unused")
public final class Chromecast {

    /**
     * Tag for logging.
     */
    private static final String TAG = "Chromecast";
    /**
     * Object to control the connection to the chromecast.
     */
    private ChromecastConnection connection;
    /**
     * Object to control the media.
     */
    private ChromecastSession media;
    /**
     * Holds the reference to the current client initiated scan.
     */
    private ChromecastConnection.ScanCallback clientScan;
    /**
     * Holds the reference to the current client initiated scan callback.
     */
    private JavascriptCallback scanCallback;
    /**
     * Client's event listener callback.
     */
    private JavascriptCallback eventCallback;
    /**
     * In the case that chromecast can't be used.
     **/
    private String noChromecastError;

    public void initializePlugin(Activity activity) {
        try {
            this.connection = new ChromecastConnection(activity, new ChromecastConnection.Listener() {
                @Override
                public void onSessionRejoin(JSONObject jsonSession) {
                    sendEvent("SESSION_LISTENER", new JSONArray().put(jsonSession));
                }

                @Override
                public void onSessionUpdate(JSONObject jsonSession) {
                    sendEvent("SESSION_UPDATE", new JSONArray().put(jsonSession));
                }

                @Override
                public void onSessionEnd(JSONObject jsonSession) {
                    onSessionUpdate(jsonSession);
                }

                @Override
                public void onReceiverAvailableUpdate(boolean available) {
                    sendEvent("RECEIVER_LISTENER", new JSONArray().put(available));
                }

                @Override
                public void onMediaLoaded(JSONObject jsonMedia) {
                    sendEvent("MEDIA_LOAD", new JSONArray().put(jsonMedia));
                }

                @Override
                public void onMediaUpdate(JSONObject jsonMedia) {
                    JSONArray out = new JSONArray();
                    if (jsonMedia != null) {
                        out.put(jsonMedia);
                    }
                    sendEvent("MEDIA_UPDATE", out);
                }

                @Override
                public void onMessageReceived(CastDevice device, String namespace, String message) {
                    sendEvent("RECEIVER_MESSAGE", new JSONArray().put(namespace).put(message));
                }
            });
            this.media = connection.getChromecastSession();
        } catch (RuntimeException e) {
            noChromecastError = "Could not initialize chromecast: " + e.getMessage();
            e.printStackTrace();
        }
    }

    public boolean execute(String action, JSONArray args, JavascriptCallback cbContext) throws JSONException {
        if (noChromecastError != null) {
            cbContext.error(ChromecastUtilities.createError("api_not_initialized", noChromecastError));
            return true;
        }
        try {
            Method[] list = this.getClass().getMethods();
            Method methodToExecute = null;
            for (Method method : list) {
                if (method.getName().equals(action)) {
                    Type[] types = method.getGenericParameterTypes();
                    // +1 is the cbContext
                    if (args.length() + 1 == types.length) {
                        boolean isValid = true;
                        for (int i = 0; i < args.length(); i++) {
                            // Handle null/undefined arguments
                            if (JSONObject.NULL.equals(args.get(i))) {
                                continue;
                            }
                            Class arg = args.get(i).getClass();
                            if (types[i] != arg) {
                                isValid = false;
                                break;
                            }
                        }
                        if (isValid) {
                            methodToExecute = method;
                            break;
                        }
                    }
                }
            }
            if (methodToExecute != null) {
                Type[] types = methodToExecute.getGenericParameterTypes();
                Object[] variableArgs = new Object[types.length];
                for (int i = 0; i < args.length(); i++) {
                    variableArgs[i] = args.get(i);
                    // Translate null JSONObject to null
                    if (JSONObject.NULL.equals(variableArgs[i])) {
                        variableArgs[i] = null;
                    }
                }
                variableArgs[variableArgs.length - 1] = cbContext;
                Class<?> r = methodToExecute.getReturnType();
                if (r == boolean.class) {
                    return (Boolean) methodToExecute.invoke(this, variableArgs);
                } else {
                    methodToExecute.invoke(this, variableArgs);
                    return true;
                }
            } else {
                return false;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return false;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Do everything you need to for "setup" - calling back sets the isAvailable and lets every function on the
     * javascript side actually do stuff.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setup(JavascriptCallback javascriptCallback) {
        this.eventCallback = javascriptCallback;
        // Ensure any existing scan is stopped
        connection.stopRouteScan(clientScan, () -> {
            if (scanCallback != null) {
                scanCallback.error(ChromecastUtilities.createError("cancel", "Scan stopped because setup triggered."));
                scanCallback = null;
            }
            sendEvent("SETUP", new JSONArray());
        });
        return true;
    }

    /**
     * Initialize all of the MediaRouter stuff with the AppId.
     * For now, ignore the autoJoinPolicy and defaultActionPolicy; those will come later
     *
     * @param appId               The appId we're going to use for ALL session requests
     * @param autoJoinPolicy      tab_and_origin_scoped | origin_scoped | page_scoped
     * @param defaultActionPolicy create_session | cast_this_tab
     * @param javascriptCallback  called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean initialize(final String appId, String autoJoinPolicy, String defaultActionPolicy, final JavascriptCallback javascriptCallback) {
        connection.initialize(appId, javascriptCallback);
        return true;
    }

    /**
     * Request the session for the previously sent appId.
     * THIS IS WHAT LAUNCHES THE CHROMECAST PICKER
     * or, if we already have a session launch the connection options
     * dialog which will have the option to stop casting at minimum.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean requestSession(final JavascriptCallback javascriptCallback) {
        connection.requestSession(new ChromecastConnection.RequestSessionCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                javascriptCallback.success(jsonSession);
            }

            @Override
            public void onError(int errorCode) {
                // TODO maybe handle some of the error codes better
                javascriptCallback.error("session_error");
            }

            @Override
            public void onCancel() {
                javascriptCallback.error("cancel");
            }
        });
        return true;
    }

    /**
     * Selects a route by its id.
     *
     * @param routeId            the id of the route to join
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean selectRoute(final String routeId, final JavascriptCallback javascriptCallback) {
        connection.selectRoute(routeId, new ChromecastConnection.SelectRouteCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                javascriptCallback.success(jsonSession);
            }

            @Override
            public void onError(JSONObject message) {
                javascriptCallback.error(message);
            }
        });
        return true;
    }

    /**
     * Set the volume level on the receiver - this is a Chromecast volume, not a Media volume.
     *
     * @param newLevel           the level to set the volume to
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setReceiverVolumeLevel(Integer newLevel, JavascriptCallback javascriptCallback) {
        return this.setReceiverVolumeLevel(newLevel.doubleValue(), javascriptCallback);
    }

    /**
     * Set the volume level on the receiver - this is a Chromecast volume, not a Media volume.
     *
     * @param newLevel           the level to set the volume to
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setReceiverVolumeLevel(Double newLevel, JavascriptCallback javascriptCallback) {
        this.media.setVolume(newLevel, javascriptCallback);
        return true;
    }

    /**
     * Sets the muted boolean on the receiver - this is a Chromecast mute, not a Media mute.
     *
     * @param muted              if true set the media to muted, else, unmute
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setReceiverMuted(Boolean muted, JavascriptCallback javascriptCallback) {
        this.media.setMute(muted, javascriptCallback);
        return true;
    }

    /**
     * Send a custom message to the receiver - we don't need this just yet... it was just simple to implement on the js side.
     *
     * @param namespace          namespace
     * @param message            the message to send
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean sendMessage(String namespace, String message, final JavascriptCallback javascriptCallback) {
        this.media.sendMessage(namespace, message, javascriptCallback);
        return true;
    }

    /**
     * Adds a listener to a specific namespace.
     *
     * @param namespace          namespace
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean addMessageListener(String namespace, JavascriptCallback javascriptCallback) {
        this.media.addMessageListener(namespace);
        javascriptCallback.success();
        return true;
    }

    /**
     * Loads some media on the Chromecast using the media APIs.
     *
     * @param contentId          The URL of the media item
     * @param customData         CustomData
     * @param contentType        MIME type of the content
     * @param duration           Duration of the content
     * @param streamType         buffered | live | other
     * @param autoPlay           Whether or not to automatically start playing the media
     * @param currentTime        Where to begin playing from
     * @param metadata           Metadata
     * @param textTrackStyle     The text track style
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean loadMedia(String contentId, JSONObject customData, String contentType, Integer duration, String streamType, Boolean autoPlay, Integer currentTime, JSONObject metadata, JSONObject textTrackStyle, final JavascriptCallback javascriptCallback) {
        return this.loadMedia(contentId, customData, contentType, duration, streamType, autoPlay, new Double(currentTime.doubleValue()), metadata, textTrackStyle, javascriptCallback);
    }

    private boolean loadMedia(String contentId, JSONObject customData, String contentType, Integer duration, String streamType, Boolean autoPlay, Double currentTime, JSONObject metadata, JSONObject textTrackStyle, final JavascriptCallback javascriptCallback) {
        this.media.loadMedia(contentId, customData, contentType, duration, streamType, autoPlay, currentTime, metadata, textTrackStyle, javascriptCallback);
        return true;
    }

    /**
     * Play on the current media in the current session.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaPlay(JavascriptCallback javascriptCallback) {
        media.mediaPlay(javascriptCallback);
        return true;
    }

    /**
     * Pause on the current media in the current session.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaPause(JavascriptCallback javascriptCallback) {
        media.mediaPause(javascriptCallback);
        return true;
    }

    /**
     * Seeks the current media in the current session.
     *
     * @param seekTime           - Seconds to seek to
     * @param resumeState        - Resume state once seeking is complete: PLAYBACK_PAUSE or PLAYBACK_START
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaSeek(Integer seekTime, String resumeState, JavascriptCallback javascriptCallback) {
        media.mediaSeek(seekTime.longValue() * 1000, resumeState, javascriptCallback);
        return true;
    }


    /**
     * Set the volume level and mute state on the media.
     *
     * @param level              the level to set the volume to
     * @param muted              if true set the media to muted, else, unmute
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setMediaVolume(Integer level, Boolean muted, JavascriptCallback javascriptCallback) {
        return this.setMediaVolume(level.doubleValue(), muted, javascriptCallback);
    }

    /**
     * Set the volume level and mute state on the media.
     *
     * @param level              the level to set the volume to
     * @param muted              if true set the media to muted, else, unmute
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setMediaVolume(Double level, Boolean muted, JavascriptCallback javascriptCallback) {
        media.mediaSetVolume(level, muted, javascriptCallback);
        return true;
    }

    /**
     * Stops the current media.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaStop(JavascriptCallback javascriptCallback) {
        media.mediaStop(javascriptCallback);
        return true;
    }

    /**
     * Handle Track changes.
     *
     * @param activeTrackIds     track Ids to set.
     * @param textTrackStyle     text track style to set.
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaEditTracksInfo(JSONArray activeTrackIds, JSONObject textTrackStyle, final JavascriptCallback javascriptCallback) {
        long[] trackIds = new long[activeTrackIds.length()];

        try {
            for (int i = 0; i < activeTrackIds.length(); i++) {
                trackIds[i] = activeTrackIds.getLong(i);
            }
        } catch (JSONException ignored) {
            Timber.tag(TAG).e("Wrong format in activeTrackIds");
        }

        this.media.mediaEditTracksInfo(trackIds, textTrackStyle, javascriptCallback);
        return true;
    }

    /**
     * Loads a queue of media to the Chromecast.
     *
     * @param queueLoadRequest   chrome.cast.media.QueueLoadRequest
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean queueLoad(JSONObject queueLoadRequest, final JavascriptCallback javascriptCallback) {
        this.media.queueLoad(queueLoadRequest, javascriptCallback);
        return true;
    }

    /**
     * Plays the item with itemId in the queue.
     *
     * @param itemId             The ID of the item to jump to.
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean queueJumpToItem(Integer itemId, final JavascriptCallback javascriptCallback) {
        this.media.queueJumpToItem(itemId, javascriptCallback);
        return true;
    }

    /**
     * Plays the item with itemId in the queue.
     *
     * @param itemId             The ID of the item to jump to.
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean queueJumpToItem(Double itemId, final JavascriptCallback javascriptCallback) {
        if (itemId - Double.valueOf(itemId).intValue() == 0) {
            // Only perform the jump if the double is a whole number
            return queueJumpToItem(Double.valueOf(itemId).intValue(), javascriptCallback);
        } else {
            return true;
        }
    }

    /**
     * Stops the session.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean sessionStop(JavascriptCallback javascriptCallback) {
        connection.endSession(true, javascriptCallback);
        return true;
    }

    /**
     * Stops the session.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean sessionLeave(JavascriptCallback javascriptCallback) {
        connection.endSession(false, javascriptCallback);
        return true;
    }

    /**
     * Will actively scan for routes and send a json array to the client.
     * It is super important that client calls "stopRouteScan", otherwise the
     * battery could drain quickly.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean startRouteScan(JavascriptCallback javascriptCallback) {
        if (scanCallback != null) {
            scanCallback.error(ChromecastUtilities.createError("cancel", "Started a new route scan before stopping previous one."));
        }
        scanCallback = javascriptCallback;
        Runnable startScan = () -> {
            clientScan = new ChromecastConnection.ScanCallback() {
                @Override
                void onRouteUpdate(List<RouteInfo> routes) {
                    if (scanCallback != null) {
                        scanCallback.success(true, ChromecastUtilities.createRoutesArray(routes));
                    } else {
                        // Try to get the scan to stop because we already ended the scanCallback
                        connection.stopRouteScan(clientScan, null);
                    }
                }
            };
            connection.startRouteScan(null, clientScan, null);
        };
        if (clientScan != null) {
            // Stop any other existing clientScan
            connection.stopRouteScan(clientScan, startScan);
        } else {
            startScan.run();
        }
        return true;
    }

    /**
     * Stops the scan started by startRouteScan.
     *
     * @param javascriptCallback called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean stopRouteScan(JavascriptCallback javascriptCallback) {
        // Stop any other existing clientScan
        connection.stopRouteScan(clientScan, () -> {
            if (scanCallback != null) {
                scanCallback.error(ChromecastUtilities.createError("cancel", "Scan stopped."));
                scanCallback = null;
            }
            javascriptCallback.success();
        });
        return true;
    }

    /**
     * This triggers an event on the JS-side.
     *
     * @param eventName - The name of the JS event to trigger
     * @param args      - The arguments to pass the JS event
     */
    private void sendEvent(String eventName, JSONArray args) {
        if (eventCallback == null) {
            return;
        }
        eventCallback.success(true, new JSONArray().put(eventName).put(args));
    }

    public void destroy() {
        final JavascriptCallback callback = new JavascriptCallback() {
            @Override
            protected void callback(boolean keep, @Nullable String err, @Nullable String result) {
                // ignored
            }
        };

        stopRouteScan(callback);
        sessionStop(callback);
        media.destroy();
        media = null;
        connection.destroy();
        connection = null;
        eventCallback = null;
    }
}
