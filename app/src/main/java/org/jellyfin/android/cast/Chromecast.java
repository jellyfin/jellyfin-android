package org.jellyfin.android.cast;

import android.app.Activity;

import androidx.mediarouter.media.MediaRouter.RouteInfo;

import com.google.android.gms.cast.CastDevice;

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
    private CallbackContext scanCallback;
    /**
     * Client's event listener callback.
     */
    private CallbackContext eventCallback;
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

    public boolean execute(String action, JSONArray args, CallbackContext cbContext) throws JSONException {
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
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setup(CallbackContext callbackContext) {
        this.eventCallback = callbackContext;
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
     * @param callbackContext     called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean initialize(final String appId, String autoJoinPolicy, String defaultActionPolicy, final CallbackContext callbackContext) {
        connection.initialize(appId, callbackContext);
        return true;
    }

    /**
     * Request the session for the previously sent appId.
     * THIS IS WHAT LAUNCHES THE CHROMECAST PICKER
     * or, if we already have a session launch the connection options
     * dialog which will have the option to stop casting at minimum.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean requestSession(final CallbackContext callbackContext) {
        connection.requestSession(new ChromecastConnection.RequestSessionCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                callbackContext.success(jsonSession);
            }

            @Override
            public void onError(int errorCode) {
                // TODO maybe handle some of the error codes better
                callbackContext.errorString("session_error");
            }

            @Override
            public void onCancel() {
                callbackContext.errorString("cancel");
            }
        });
        return true;
    }

    /**
     * Selects a route by its id.
     *
     * @param routeId         the id of the route to join
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean selectRoute(final String routeId, final CallbackContext callbackContext) {
        connection.selectRoute(routeId, new ChromecastConnection.SelectRouteCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                callbackContext.success(jsonSession);
            }

            @Override
            public void onError(JSONObject message) {
                callbackContext.error(message);
            }
        });
        return true;
    }

    /**
     * Set the volume level on the receiver - this is a Chromecast volume, not a Media volume.
     *
     * @param newLevel        the level to set the volume to
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setReceiverVolumeLevel(Integer newLevel, CallbackContext callbackContext) {
        return this.setReceiverVolumeLevel(newLevel.doubleValue(), callbackContext);
    }

    /**
     * Set the volume level on the receiver - this is a Chromecast volume, not a Media volume.
     *
     * @param newLevel        the level to set the volume to
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setReceiverVolumeLevel(Double newLevel, CallbackContext callbackContext) {
        this.media.setVolume(newLevel, callbackContext);
        return true;
    }

    /**
     * Sets the muted boolean on the receiver - this is a Chromecast mute, not a Media mute.
     *
     * @param muted           if true set the media to muted, else, unmute
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setReceiverMuted(Boolean muted, CallbackContext callbackContext) {
        this.media.setMute(muted, callbackContext);
        return true;
    }

    /**
     * Send a custom message to the receiver - we don't need this just yet... it was just simple to implement on the js side.
     *
     * @param namespace       namespace
     * @param message         the message to send
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean sendMessage(String namespace, String message, final CallbackContext callbackContext) {
        this.media.sendMessage(namespace, message, callbackContext);
        return true;
    }

    /**
     * Adds a listener to a specific namespace.
     *
     * @param namespace       namespace
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean addMessageListener(String namespace, CallbackContext callbackContext) {
        this.media.addMessageListener(namespace);
        callbackContext.success();
        return true;
    }

    /**
     * Loads some media on the Chromecast using the media APIs.
     *
     * @param contentId       The URL of the media item
     * @param customData      CustomData
     * @param contentType     MIME type of the content
     * @param duration        Duration of the content
     * @param streamType      buffered | live | other
     * @param autoPlay        Whether or not to automatically start playing the media
     * @param currentTime     Where to begin playing from
     * @param metadata        Metadata
     * @param textTrackStyle  The text track style
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean loadMedia(String contentId, JSONObject customData, String contentType, Integer duration, String streamType, Boolean autoPlay, Integer currentTime, JSONObject metadata, JSONObject textTrackStyle, final CallbackContext callbackContext) {
        return this.loadMedia(contentId, customData, contentType, duration, streamType, autoPlay, new Double(currentTime.doubleValue()), metadata, textTrackStyle, callbackContext);
    }

    private boolean loadMedia(String contentId, JSONObject customData, String contentType, Integer duration, String streamType, Boolean autoPlay, Double currentTime, JSONObject metadata, JSONObject textTrackStyle, final CallbackContext callbackContext) {
        this.media.loadMedia(contentId, customData, contentType, duration, streamType, autoPlay, currentTime, metadata, textTrackStyle, callbackContext);
        return true;
    }

    /**
     * Play on the current media in the current session.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaPlay(CallbackContext callbackContext) {
        media.mediaPlay(callbackContext);
        return true;
    }

    /**
     * Pause on the current media in the current session.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaPause(CallbackContext callbackContext) {
        media.mediaPause(callbackContext);
        return true;
    }

    /**
     * Seeks the current media in the current session.
     *
     * @param seekTime        - Seconds to seek to
     * @param resumeState     - Resume state once seeking is complete: PLAYBACK_PAUSE or PLAYBACK_START
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaSeek(Integer seekTime, String resumeState, CallbackContext callbackContext) {
        media.mediaSeek(seekTime.longValue() * 1000, resumeState, callbackContext);
        return true;
    }


    /**
     * Set the volume level and mute state on the media.
     *
     * @param level           the level to set the volume to
     * @param muted           if true set the media to muted, else, unmute
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setMediaVolume(Integer level, Boolean muted, CallbackContext callbackContext) {
        return this.setMediaVolume(level.doubleValue(), muted, callbackContext);
    }

    /**
     * Set the volume level and mute state on the media.
     *
     * @param level           the level to set the volume to
     * @param muted           if true set the media to muted, else, unmute
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean setMediaVolume(Double level, Boolean muted, CallbackContext callbackContext) {
        media.mediaSetVolume(level, muted, callbackContext);
        return true;
    }

    /**
     * Stops the current media.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaStop(CallbackContext callbackContext) {
        media.mediaStop(callbackContext);
        return true;
    }

    /**
     * Handle Track changes.
     *
     * @param activeTrackIds  track Ids to set.
     * @param textTrackStyle  text track style to set.
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean mediaEditTracksInfo(JSONArray activeTrackIds, JSONObject textTrackStyle, final CallbackContext callbackContext) {
        long[] trackIds = new long[activeTrackIds.length()];

        try {
            for (int i = 0; i < activeTrackIds.length(); i++) {
                trackIds[i] = activeTrackIds.getLong(i);
            }
        } catch (JSONException ignored) {
            Timber.tag(TAG).e("Wrong format in activeTrackIds");
        }

        this.media.mediaEditTracksInfo(trackIds, textTrackStyle, callbackContext);
        return true;
    }

    /**
     * Loads a queue of media to the Chromecast.
     *
     * @param queueLoadRequest chrome.cast.media.QueueLoadRequest
     * @param callbackContext  called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean queueLoad(JSONObject queueLoadRequest, final CallbackContext callbackContext) {
        this.media.queueLoad(queueLoadRequest, callbackContext);
        return true;
    }

    /**
     * Plays the item with itemId in the queue.
     *
     * @param itemId          The ID of the item to jump to.
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean queueJumpToItem(Integer itemId, final CallbackContext callbackContext) {
        this.media.queueJumpToItem(itemId, callbackContext);
        return true;
    }

    /**
     * Plays the item with itemId in the queue.
     *
     * @param itemId          The ID of the item to jump to.
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean queueJumpToItem(Double itemId, final CallbackContext callbackContext) {
        if (itemId - Double.valueOf(itemId).intValue() == 0) {
            // Only perform the jump if the double is a whole number
            return queueJumpToItem(Double.valueOf(itemId).intValue(), callbackContext);
        } else {
            return true;
        }
    }

    /**
     * Stops the session.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean sessionStop(CallbackContext callbackContext) {
        connection.endSession(true, callbackContext);
        return true;
    }

    /**
     * Stops the session.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean sessionLeave(CallbackContext callbackContext) {
        connection.endSession(false, callbackContext);
        return true;
    }

    /**
     * Will actively scan for routes and send a json array to the client.
     * It is super important that client calls "stopRouteScan", otherwise the
     * battery could drain quickly.
     *
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean startRouteScan(CallbackContext callbackContext) {
        if (scanCallback != null) {
            scanCallback.error(ChromecastUtilities.createError("cancel", "Started a new route scan before stopping previous one."));
        }
        scanCallback = callbackContext;
        Runnable startScan = () -> {
            clientScan = new ChromecastConnection.ScanCallback() {
                @Override
                void onRouteUpdate(List<RouteInfo> routes) {
                    if (scanCallback != null) {
                        scanCallback.callback(true, null, ChromecastUtilities.createRoutesArray(routes).toString());
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
     * @param callbackContext called with .success or .error depending on the result
     * @return true for cordova
     */
    public boolean stopRouteScan(CallbackContext callbackContext) {
        // Stop any other existing clientScan
        connection.stopRouteScan(clientScan, () -> {
            if (scanCallback != null) {
                scanCallback.error(ChromecastUtilities.createError("cancel", "Scan stopped."));
                scanCallback = null;
            }
            callbackContext.success();
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
        eventCallback.callback(true, null, new JSONArray().put(eventName).put(args).toString());
    }
}
