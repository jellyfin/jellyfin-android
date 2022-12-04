package org.jellyfin.mobile.player.cast;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouter.RouteInfo;

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import org.jellyfin.mobile.R;
import org.jellyfin.mobile.bridge.JavascriptCallback;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChromecastConnection {

    /**
     * A shared handler for this connection instance.
     */
    private final Handler handler;

    /**
     * Lifetime variable.
     */
    @Nullable
    private Activity activity;
    /**
     * settings object.
     */
    private final SharedPreferences settings;
    /**
     * Controls the media.
     */
    private final ChromecastSession media;

    /**
     * Lifetime variable.
     */
    private SessionListener newConnectionListener;

    /**
     * The Listener callback.
     */
    private final Listener listener;

    /**
     * Initialize lifetime variable.
     */
    @NonNull
    private String appId;

    /**
     * Constructor.
     *
     * @param act                the current context
     * @param connectionListener client callbacks for specific events
     */
    ChromecastConnection(Activity act, Listener connectionListener) {
        handler = new Handler(Looper.getMainLooper());
        activity = act;
        settings = activity.getSharedPreferences("CORDOVA-PLUGIN-CHROMECAST_ChromecastConnection", 0);
        appId = settings.getString("appId", CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID);
        listener = connectionListener;
        media = new ChromecastSession(activity, listener);

        // Set the initial appId
        CastOptionsProvider.setAppId(appId);

        // This is the first call to getContext which will start up the
        // CastContext and prep it for searching for a session to rejoin
        // Also adds the receiver update callback
        getContext().addCastStateListener(listener);
    }

    /**
     * Get the ChromecastSession object for controlling media and receiver functions.
     *
     * @return the ChromecastSession object
     */
    ChromecastSession getChromecastSession() {
        return media;
    }

    /**
     * Must be called each time the appId changes and at least once before any other method is called.
     *
     * @param applicationId the app id to use
     * @param callback      called when initialization is complete
     */
    public void initialize(String applicationId, JavascriptCallback callback) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                // If the app Id changed
                if (applicationId == null || !applicationId.equals(appId)) {
                    // If app Id is valid
                    if (isValidAppId(applicationId)) {
                        // Set the new app Id
                        setAppId(applicationId);
                    } else {
                        // Else, just return
                        callback.success();
                        return;
                    }
                }

                // Tell the client that initialization was a success
                callback.success();

                // Check if there is any available receivers for 5 seconds
                startRouteScan(5000L, new ScanCallback() {
                    @Override
                    void onRouteUpdate(List<RouteInfo> routes) {
                        // if the routes have changed, we may have an available device
                        // If there is at least one device available
                        if (getContext().getCastState() != CastState.NO_DEVICES_AVAILABLE) {
                            // Stop the scan
                            stopRouteScan(this, null);
                            // Let the client know a receiver is available
                            listener.onReceiverAvailableUpdate(true);
                            // Since we have a receiver we may also have an active session
                            CastSession session = getSessionManager().getCurrentCastSession();
                            // If we do have a session
                            if (session != null) {
                                // Let the client know
                                media.setSession(session);
                                listener.onSessionRejoin(ChromecastUtilities.createSessionObject(session));
                            }
                        }
                    }
                }, null);
            }
        });
    }

    @Nullable
    private MediaRouter getMediaRouter() {
        return activity != null ? MediaRouter.getInstance(activity) : null;
    }

    private CastContext getContext() {
        return CastContext.getSharedInstance(activity);
    }

    private SessionManager getSessionManager() {
        return getContext().getSessionManager();
    }

    private CastSession getSession() {
        return getSessionManager().getCurrentCastSession();
    }

    private void setAppId(String applicationId) {
        this.appId = applicationId;
        this.settings.edit().putString("appId", appId).apply();
        getContext().setReceiverApplicationId(appId);
    }

    /**
     * Tests if an application receiver id is valid.
     *
     * @param applicationId - application receiver id
     * @return true if valid
     */
    private boolean isValidAppId(String applicationId) {
        try {
            MediaRouter mediaRouter = getMediaRouter();
            if (mediaRouter == null) return false;
            ScanCallback cb = new ScanCallback() {
                @Override
                void onRouteUpdate(List<RouteInfo> routes) {
                }
            };
            // This will throw if the applicationId is invalid
            mediaRouter.addCallback(
                new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(applicationId))
                    .build(),
                cb,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            );
            // If no exception we passed, so remove the callback
            mediaRouter.removeCallback(cb);
            return true;
        } catch (IllegalArgumentException e) {
            // Don't set the appId if it is not a valid receiverApplicationID
            return false;
        }
    }

    /**
     * This will create a new session or seamlessly selectRoute an existing one if we created it.
     *
     * @param routeId  the id of the route to selectRoute
     * @param callback calls callback.onJoin when we have joined a session,
     *                 or callback.onError if an error occurred
     */
    public void selectRoute(final String routeId, SelectRouteCallback callback) {
        activity.runOnUiThread(() -> {
            if (getSession() != null && getSession().isConnected()) {
                callback.onError(ChromecastUtilities.createError("session_error",
                    "Leave or stop current session before attempting to join new session."));
                return;
            }

            // We need this hack so that we can access these values in callbacks without having
            // to store it as a global variable, just always access first element
            final boolean[] foundRoute = {false};
            final boolean[] sentResult = {false};
            final int[] retries = {0};

            // We need to start an active scan because getMediaRouter().getRoutes() may be out
            // of date.  Also, maintaining a list of known routes doesn't work.  It is possible
            // to have a route in your "known" routes list, but is not in
            // getMediaRouter().getRoutes() which will result in "Ignoring attempt to select
            // removed route: ", even if that route *should* be available.  This state could
            // happen because routes are periodically "removed" and "added", and if the last
            // time media router was scanning ended when the route was temporarily removed the
            // getRoutes() fn will have no record of the route.  We need the active scan to
            // avoid this situation as well.  PS. Just running the scan non-stop is a poor idea
            // since it will drain battery power quickly.
            ScanCallback scan = new ScanCallback() {
                @Override
                void onRouteUpdate(List<RouteInfo> routes) {
                    // Look for the matching route
                    for (RouteInfo route : routes) {
                        if (!foundRoute[0] && route.getId().equals(routeId)) {
                            // Found the route!
                            foundRoute[0] = true;
                            // try-catch for issue:
                            // https://github.com/jellyfin/cordova-plugin-chromecast/issues/48
                            try {
                                // Try selecting the route!
                                Objects.requireNonNull(getMediaRouter()).selectRoute(route);
                            } catch (NullPointerException e) {
                                // Let it try to find the route again
                                foundRoute[0] = false;
                            }
                        }
                    }
                }
            };

            Runnable retry = () -> {
                // Reset foundRoute
                foundRoute[0] = false;
                // Feed current routes into scan so that it can retry.
                // If route is there, it will try to join,
                // if not, it should wait for the scan to find the route
                scan.onRouteUpdate(Objects.requireNonNull(getMediaRouter()).getRoutes());
            };

            Function<JSONObject, Void> sendErrorResult = message -> {
                if (!sentResult[0]) {
                    sentResult[0] = true;
                    stopRouteScan(scan, null);
                    callback.onError(message);
                }
                return null;
            };

            listenForConnection(new ConnectionCallback() {
                @Override
                public void onJoin(JSONObject jsonSession) {
                    sentResult[0] = true;
                    stopRouteScan(scan, null);
                    callback.onJoin(jsonSession);
                }

                @Override
                public boolean onSessionStartFailed(int errorCode) {
                    if (errorCode == 7 || errorCode == 15) {
                        // It network or timeout error retry
                        retry.run();
                        return false;
                    } else {
                        sendErrorResult.apply(ChromecastUtilities.createError("session_error",
                            "Failed to start session with error code: " + errorCode));
                        return true;
                    }
                }

                @Override
                public boolean onSessionEndedBeforeStart(int errorCode) {
                    if (retries[0] < 10) {
                        retries[0]++;
                        retry.run();
                        return false;
                    } else {
                        sendErrorResult.apply(ChromecastUtilities.createError("session_error",
                            "Failed to to join existing route (" + routeId + ") " + retries[0] + 1 + " times before giving up."));
                        return true;
                    }
                }
            });

            startRouteScan(15000L, scan, () ->
                sendErrorResult.apply(ChromecastUtilities.createError("timeout", "Failed to join route (" + routeId + ") after 15s and " + (retries[0] + 1) + " tries."))
            );
        });
    }

    /**
     * Will do one of two things:
     * <p>
     * If no current connection will:
     * 1)
     * Displays the built in native prompt to the user.
     * It will actively scan for routes and display them to the user.
     * Upon selection it will immediately attempt to selectRoute the route.
     * Will call onJoin, onError or onCancel, of callback.
     * <p>
     * Else we have a connection, so:
     * 2)
     * Displays the active connection dialog which includes the option
     * to disconnect.
     * Will only call onCancel of callback if the user cancels the dialog.
     *
     * @param callback calls callback.success when we have joined a session,
     *                 or callback.error if an error occurred or if the dialog was dismissed
     */
    public void requestSession(RequestSessionCallback callback) {
        activity.runOnUiThread(() -> {
            CastSession session = getSession();
            if (session == null) {
                // show the "choose a connection" dialog

                // Add the connection listener callback
                listenForConnection(callback);

                // Create the dialog
                // TODO accept theme as a config.xml option
                MediaRouteChooserDialog builder = new MediaRouteChooserDialog(activity, R.style.AppTheme);
                builder.setRouteSelector(new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(appId))
                    .build());
                builder.setCanceledOnTouchOutside(true);
                builder.setOnCancelListener(dialog -> {
                    getSessionManager().removeSessionManagerListener(newConnectionListener, CastSession.class);
                    callback.onCancel();
                });
                builder.show();
            } else {
                // We are are already connected, so show the "connection options" Dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                if (session.getCastDevice() != null) {
                    builder.setTitle(session.getCastDevice().getFriendlyName());
                }
                builder.setOnDismissListener(dialog -> callback.onCancel());
                builder.setPositiveButton("Stop Casting", (dialog, which) ->
                    endSession(true, null)
                );
                builder.show();
            }
        });
    }

    /**
     * Must be called from the main thread.
     *
     * @param callback calls callback.success when we have joined, or callback.error if an error occurred
     */
    private void listenForConnection(ConnectionCallback callback) {
        // We should only ever have one of these listeners active at a time, so remove previous
        getSessionManager().removeSessionManagerListener(newConnectionListener, CastSession.class);
        newConnectionListener = new SessionListener() {
            @Override
            public void onSessionStarted(@NonNull CastSession castSession, @NonNull String sessionId) {
                getSessionManager().removeSessionManagerListener(this, CastSession.class);
                media.setSession(castSession);
                callback.onJoin(ChromecastUtilities.createSessionObject(castSession));
            }

            @Override
            public void onSessionStartFailed(@NonNull CastSession castSession, int errCode) {
                if (callback.onSessionStartFailed(errCode)) {
                    getSessionManager().removeSessionManagerListener(this, CastSession.class);
                }
            }

            @Override
            public void onSessionEnded(@NonNull CastSession castSession, int errCode) {
                if (callback.onSessionEndedBeforeStart(errCode)) {
                    getSessionManager().removeSessionManagerListener(this, CastSession.class);
                }
            }
        };
        getSessionManager().addSessionManagerListener(newConnectionListener, CastSession.class);
    }

    /**
     * Starts listening for receiver updates.
     * Must call stopRouteScan(callback) or the battery will drain with non-stop active scanning.
     *
     * @param timeout   ms until the scan automatically stops,
     *                  if 0 only calls callback.onRouteUpdate once with the currently known routes
     *                  if null, will scan until stopRouteScan is called
     * @param callback  the callback to receive route updates on
     * @param onTimeout called when the timeout hits
     */
    public void startRouteScan(Long timeout, ScanCallback callback, Runnable onTimeout) {
        if (activity == null) return;

        // Add the callback in active scan mode
        activity.runOnUiThread(() -> {
            MediaRouter mediaRouter = getMediaRouter();
            if (mediaRouter == null) return;

            callback.setMediaRouter(mediaRouter);

            if (timeout != null && timeout == 0) {
                // Send out the one time routes
                callback.onFilteredRouteUpdate();
                return;
            }

            // Add the callback in active scan mode
            mediaRouter.addCallback(new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(appId))
                    .build(),
                callback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            );

            // Send out the initial routes after the callback has been added.
            // This is important because if the callback calls stopRouteScan only once, and it
            // happens during this call of "onFilterRouteUpdate", there must actually be an
            // added callback to remove to stop the scan.
            callback.onFilteredRouteUpdate();

            if (timeout != null) {
                // remove the callback after timeout ms, and notify caller
                handler.postDelayed(() -> {
                    // And stop the scan for routes
                    // MediaRouter should never be null, since all callbacks are be removed in destroy()
                    Objects.requireNonNull(getMediaRouter()).removeCallback(callback);
                    // Notify
                    if (onTimeout != null) {
                        onTimeout.run();
                    }
                }, timeout);
            }
        });
    }

    /**
     * Call to stop the active scan if any exist.
     *
     * @param callback           the callback to stop and remove
     * @param completionCallback called on completion
     */
    public void stopRouteScan(ScanCallback callback, Runnable completionCallback) {
        if (callback == null || activity == null) {
            if (completionCallback != null) {
                completionCallback.run();
            }
            return;
        }
        activity.runOnUiThread(() -> {
            callback.stop();
            MediaRouter mediaRouter = getMediaRouter();
            if (mediaRouter != null) {
                mediaRouter.removeCallback(callback);
            }
            if (completionCallback != null) {
                completionCallback.run();
            }
        });
    }

    /**
     * Exits the current session.
     *
     * @param stopCasting should the receiver application  be stopped as well?
     * @param callback    called with .success or .error depending on the initial result
     */
    void endSession(boolean stopCasting, JavascriptCallback callback) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                getSessionManager().addSessionManagerListener(new SessionListener() {
                    @Override
                    public void onSessionEnded(@NonNull CastSession castSession, int error) {
                        getSessionManager().removeSessionManagerListener(this, CastSession.class);
                        media.setSession(null);
                        if (callback != null) {
                            callback.success();
                        }
                        listener.onSessionEnd(ChromecastUtilities.createSessionObject(castSession, stopCasting ? "stopped" : "disconnected"));
                    }
                }, CastSession.class);

                getSessionManager().endCurrentSession(stopCasting);
            }
        });
    }

    public void destroy() {
        getSessionManager().removeSessionManagerListener(newConnectionListener, CastSession.class);
        handler.removeCallbacksAndMessages(null);
        activity = null;
    }

    /**
     * Create this empty class so that we don't have to override every function
     * each time we need a SessionManagerListener.
     */
    private static class SessionListener implements SessionManagerListener<CastSession> {
        @Override
        public void onSessionStarting(@NonNull CastSession castSession) {
        }

        @Override
        public void onSessionStarted(@NonNull CastSession castSession, @NonNull String sessionId) {
        }

        @Override
        public void onSessionStartFailed(@NonNull CastSession castSession, int error) {
        }

        @Override
        public void onSessionEnding(@NonNull CastSession castSession) {
        }

        @Override
        public void onSessionEnded(@NonNull CastSession castSession, int error) {
        }

        @Override
        public void onSessionResuming(@NonNull CastSession castSession, @NonNull String sessionId) {
        }

        @Override
        public void onSessionResumed(@NonNull CastSession castSession, boolean wasSuspended) {
        }

        @Override
        public void onSessionResumeFailed(@NonNull CastSession castSession, int error) {
        }

        @Override
        public void onSessionSuspended(@NonNull CastSession castSession, int reason) {
        }
    }

    interface SelectRouteCallback {
        void onJoin(JSONObject jsonSession);

        void onError(JSONObject message);
    }

    abstract static class RequestSessionCallback implements ConnectionCallback {
        abstract void onError(int errorCode);

        abstract void onCancel();

        @Override
        public final boolean onSessionEndedBeforeStart(int errorCode) {
            onSessionStartFailed(errorCode);
            return true;
        }

        @Override
        public final boolean onSessionStartFailed(int errorCode) {
            onError(errorCode);
            return true;
        }
    }

    interface ConnectionCallback {
        /**
         * Successfully joined a session on a route.
         *
         * @param jsonSession the session we joined
         */
        void onJoin(JSONObject jsonSession);

        /**
         * Called if we received an error.
         *
         * @param errorCode You can find the error meaning here:
         *                  https://developers.google.com/android/reference/com/google/android/gms/cast/CastStatusCodes
         * @return true if we are done listening for join, false, if we to keep listening
         */
        boolean onSessionStartFailed(int errorCode);

        /**
         * Called when we detect a session ended event before session started.
         * See issues:
         * https://github.com/jellyfin/cordova-plugin-chromecast/issues/49
         * https://github.com/jellyfin/cordova-plugin-chromecast/issues/48
         *
         * @param errorCode error to output
         * @return true if we are done listening for join, false, if we to keep listening
         */
        boolean onSessionEndedBeforeStart(int errorCode);
    }

    public abstract static class ScanCallback extends MediaRouter.Callback {
        /**
         * Called whenever a route is updated.
         *
         * @param routes the currently available routes
         */
        abstract void onRouteUpdate(List<RouteInfo> routes);

        /**
         * records whether we have been stopped or not.
         */
        private boolean stopped = false;
        /**
         * Global mediaRouter object.
         */
        private MediaRouter mediaRouter;

        /**
         * Sets the mediaRouter object.
         *
         * @param router mediaRouter object
         */
        void setMediaRouter(MediaRouter router) {
            this.mediaRouter = router;
        }

        /**
         * Call this method when you wish to stop scanning.
         * It is important that it is called, otherwise battery
         * life will drain more quickly.
         */
        void stop() {
            stopped = true;
        }

        private void onFilteredRouteUpdate() {
            if (stopped || mediaRouter == null) {
                return;
            }
            List<RouteInfo> outRoutes = new ArrayList<>();
            // Filter the routes
            for (RouteInfo route : mediaRouter.getRoutes()) {
                // We don't want default routes, or duplicate active routes
                // or multizone duplicates https://github.com/jellyfin/cordova-plugin-chromecast/issues/32
                Bundle extras = route.getExtras();
                if (extras != null) {
                    CastDevice.getFromBundle(extras);
                    if (extras.getString("com.google.android.gms.cast.EXTRA_SESSION_ID") != null) {
                        continue;
                    }
                }
                if (!route.isDefault() && !Objects.equals(route.getDescription(), "Google Cast Multizone Member") && route.getPlaybackType() == RouteInfo.PLAYBACK_TYPE_REMOTE) {
                    outRoutes.add(route);
                }
            }
            onRouteUpdate(outRoutes);
        }

        @Override
        public final void onRouteAdded(@NonNull MediaRouter router, @NonNull RouteInfo route) {
            onFilteredRouteUpdate();
        }

        @Override
        public final void onRouteChanged(@NonNull MediaRouter router, @NonNull RouteInfo route) {
            onFilteredRouteUpdate();
        }

        @Override
        public final void onRouteRemoved(@NonNull MediaRouter router, @NonNull RouteInfo route) {
            onFilteredRouteUpdate();
        }
    }

    abstract static class Listener implements CastStateListener, ChromecastSession.Listener {
        abstract void onReceiverAvailableUpdate(boolean available);

        abstract void onSessionRejoin(JSONObject jsonSession);

        /**
         * CastStateListener functions.
         */
        @Override
        public void onCastStateChanged(int state) {
            onReceiverAvailableUpdate(state != CastState.NO_DEVICES_AVAILABLE);
        }
    }
}
