package org.jellyfin.mobile.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.FragmentDownloadsBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.withThemedContext
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsFragment : Fragment(), KoinComponent {
    private val viewModel: DownloadsViewModel by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val apiClient: ApiClient by inject()
    private lateinit var adapter: DownloadsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), R.style.AppTheme_Settings)
        val binding = FragmentDownloadsBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()
        binding.toolbar.setTitle(R.string.downloads)

        requireMainActivity().apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        adapter = DownloadsAdapter(
            apiClient,
            onItemClick = { download -> onDownloadItemClick(download) },
            onItemHold = { download -> onDownloadItemHold(download) },
        )
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloads.collect { downloads ->
                    adapter.submitList(downloads)
                }
            }
        }

        return binding.root
    }

    private fun onDownloadItemClick(download: DownloadEntity) {
        val playOptions = PlayOptions(
            ids = listOf(download.mediaSource.itemId),
            mediaSourceId = download.mediaSource.id,
            startIndex = 0,
            startPosition = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }

    private fun onDownloadItemHold(download: DownloadEntity) {
        activityEventHandler.emit(ActivityEvent.RemoveDownload(download.mediaSource))
    }
}
