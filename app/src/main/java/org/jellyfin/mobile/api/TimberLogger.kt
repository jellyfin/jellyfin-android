package org.jellyfin.mobile.api

import org.jellyfin.apiclient.logging.ILogger
import timber.log.Timber

class TimberLogger : ILogger {
    override fun debug(formatString: String?, vararg paramList: Any?) {
        Timber.d(formatString, paramList)
    }

    override fun info(formatString: String?, vararg paramList: Any?) {
        Timber.i(formatString, paramList)
    }

    override fun error(formatString: String?, vararg paramList: Any?) {
        Timber.e(formatString, paramList)
    }

    override fun error(formatString: String?, exception: Exception?, vararg paramList: Any?) {
        Timber.e(exception, formatString, paramList)
    }
}
