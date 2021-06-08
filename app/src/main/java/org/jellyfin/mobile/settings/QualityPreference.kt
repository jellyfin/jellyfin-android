package org.jellyfin.mobile.settings

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.Maxr1998.modernpreferences.helpers.DEFAULT_RES_ID
import de.Maxr1998.modernpreferences.preferences.DialogPreference
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.QualityOption
import org.jellyfin.mobile.utils.QualityOptions
import org.jellyfin.mobile.utils.selected
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class QualityPreference(key: String) : DialogPreference(key), KoinComponent {
    internal lateinit var qualityOptions: List<QualityOption>
    private lateinit var initialSelection: QualityOption
    private var qualityAdapter = QualityAdapter(this)
    private var init = false

    internal fun init() {
        qualityOptions = get<QualityOptions>().getVideoQualityOptions(getInt(QualityOptions.MAX_BITRATE_VIDEO))
        initialSelection = qualityOptions.selected()
        init = true
    }

    override fun resolveSummary(context: Context): CharSequence? {
        if (!init) init()
        return qualityOptions.find { it.selected }?.name
    }

    override fun createDialog(context: Context): Dialog = AlertDialog.Builder(context).apply {
        if (titleRes != DEFAULT_RES_ID) setTitle(titleRes) else setTitle(title)
        setView(RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = qualityAdapter
        })
        setCancelable(false)
        setPositiveButton(android.R.string.ok) { _, _ ->
            if (qualityOptions.selected() != initialSelection) {
                persistSelection()
                requestRebind()
            }
        }
        setNegativeButton(android.R.string.cancel) { _, _ ->
            if (qualityOptions.selected() != initialSelection) {
                resetSelection()
            }
        }
    }.create()

    private fun resetSelection() {
        qualityOptions = get<QualityOptions>().getVideoQualityOptions(getInt(QualityOptions.MAX_BITRATE_VIDEO))
        qualityAdapter.notifySelectionChanged()
    }

    private fun persistSelection() {
        commitInt(qualityOptions.selected().bitrate)
    }

    internal fun select(quality: QualityOption) {
        qualityOptions.forEach {
            it.selected = it == quality
        }
        qualityAdapter.notifySelectionChanged()
    }

    private class QualityAdapter(
        private val preference: QualityPreference,
    ) : RecyclerView.Adapter<QualityAdapter.SelectionViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectionViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val view = layoutInflater.inflate(R.layout.map_dialog_single_choice_item, parent, false)
            return SelectionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SelectionViewHolder, position: Int) {
            val item = preference.qualityOptions[position]
            holder.apply {
                selector.isChecked = item.selected
                title.text = item.name
                summary.isVisible = false
                itemView.setOnClickListener {
                    if (!item.selected) {
                        preference.select(item)
                    }
                }
            }
        }

        override fun getItemCount(): Int = preference.qualityOptions.size

        fun notifySelectionChanged() {
            notifyItemRangeChanged(0, itemCount)
        }

        class SelectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val selector: CompoundButton = itemView.findViewById(R.id.map_selector)
            val title: TextView = itemView.findViewById(android.R.id.title)
            val summary: TextView = itemView.findViewById(android.R.id.summary)
        }
    }
}
