package org.jellyfin.mobile.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder().setReceiverApplicationId(appId).build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }

    companion object {
        /** The app id.  */
        private var appId: String? = null

        /**
         * Sets the app ID.
         * @param applicationId appId
         */
        @JvmStatic
        fun setAppId(applicationId: String?) {
            appId = applicationId
        }
    }
}