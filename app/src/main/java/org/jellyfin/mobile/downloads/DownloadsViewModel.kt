import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsViewModel() : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()

    fun getAllDownloads(): LiveData<List<DownloadEntity>> {
        return liveData {
            emit(downloadDao.getAllDownloads())
        }
    }
}
