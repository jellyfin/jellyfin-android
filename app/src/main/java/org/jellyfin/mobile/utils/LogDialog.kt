package org.jellyfin.mobile.utils

import android.app.Dialog
import android.content.Context
import de.Maxr1998.modernpreferences.preferences.DialogPreference

class LogDialog : DialogPreference("delete-log-dialog") {
    override fun createDialog(context: Context): Dialog =
        Config.dialogBuilderFactory(context)
            .setTitle("Clear logs")
            .setMessage("Are you sure you want to clear the logs?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok){ _, _ ->
                context.deleteAllLogs()
            }
            .create()
}
