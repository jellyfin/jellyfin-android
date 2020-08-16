package org.jellyfin.mobile.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.screen
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.Constants

class SettingsActivity : AppCompatActivity() {

    private val settingsAdapter: PreferencesAdapter by lazy { PreferencesAdapter(buildSettingsScreen()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = settingsAdapter
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun buildSettingsScreen() = screen(this) {
        collapseIcon = true
        checkBox(Constants.PREF_ENABLE_EXOPLAYER) {
            titleRes = R.string.pref_enable_exoplayer_title
            summaryRes = R.string.pref_enable_exoplayer_summary
            defaultValue = true
        }
    }
}