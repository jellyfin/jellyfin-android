package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()

    // This is a mutable state flow that will be used internally in the viewmodel, empty list is given as initial value.
    private val _downloads = MutableStateFlow(emptyList<DownloadEntity>())

    // Immutable state flow that you expose to your UI
    val downloads = _downloads.asStateFlow()

    init {
        getAllDownloads()
    }

    private fun getAllDownloads() {
        viewModelScope.launch { // this: CoroutineScope
            downloadDao.getAllDownloads().flowOn(Dispatchers.IO).collect { downloads: List<DownloadEntity> ->
                _downloads.update { downloads }
            }
        }
    }
}
