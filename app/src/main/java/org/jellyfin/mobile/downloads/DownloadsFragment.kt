package org.jellyfin.mobile.downloads

import DownloadsViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentDownloadsBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.withThemedContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsFragment: Fragment(), KoinComponent {
    private lateinit var viewModel: DownloadsViewModel
    private lateinit var adapter: DownloadsAdapter
    private val activityEventHandler: ActivityEventHandler by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), R.style.AppTheme_Settings)
        val binding = FragmentDownloadsBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()
        binding.toolbar.setTitle(R.string.downloads)

        requireMainActivity().apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        adapter = DownloadsAdapter { download -> onDownloadItemClick(download) }
        binding.recyclerView.adapter = adapter

        viewModel = ViewModelProvider(this)[DownloadsViewModel::class.java]
        viewModel.getAllDownloads().observe(viewLifecycleOwner, Observer { downloads ->
            downloads?.let { adapter.setDownloads(it) }
        })

        return binding.root
    }
    private fun onDownloadItemClick(download: DownloadItem) {
        val playOptions = PlayOptions(
            ids = listOf(download.mediaSource.itemId),
            mediaSourceId = download.mediaSource.id,
            startIndex = 0,
            startPositionTicks= null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }

}
