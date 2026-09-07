package wiki.kivo.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wiki.kivo.core.data.ContentRepository
import wiki.kivo.core.data.local.LibraryRepository
import wiki.kivo.core.data.local.SettingsRepository
import wiki.kivo.core.data.network.friendlyMessage
import wiki.kivo.core.model.AppSettings
import wiki.kivo.core.model.ContentCard

@HiltViewModel
class AppViewModel
@Inject
constructor(
    val repository: ContentRepository,
    val library: LibraryRepository,
    val preferences: SettingsRepository,
    val account: wiki.kivo.core.data.account.AccountRepository,
    private val characterAssets: wiki.kivo.core.media.CharacterAssets,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> =
        preferences.settings
            // 首次显示页面前应用已保存的预算；降低上限时立即淘汰旧媒体。
            .onEach { characterAssets.setCacheLimit(it.cacheLimit) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val cacheSize = MutableStateFlow<Long?>(null)
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = mutableMessages.asSharedFlow()

    fun setCacheLimit(limit: wiki.kivo.core.model.CacheLimit) {
        viewModelScope.launch {
            try {
                preferences.setCacheLimit(limit)
                characterAssets.setCacheLimit(limit)
                measureCache()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableMessages.emit(e.friendlyMessage())
            }
        }
    }

    fun measureCache() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheSize.value =
                repository.cacheBytes() +
                    (SingletonImageLoader.get(context).diskCache?.size ?: 0) +
                    characterAssets.cacheBytes()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.clearCache()
                    characterAssets.clear()
                    SingletonImageLoader.get(context).diskCache?.clear()
                }
                SingletonImageLoader.get(context).memoryCache?.clear()
            }
                .onSuccess {
                    mutableMessages.emit("临时缓存已清理")
                    measureCache()
                }
                .onFailure { mutableMessages.emit(it.friendlyMessage()) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            library.clearHistory()
            mutableMessages.emit("阅读足迹已清空")
        }
    }

    /** 详情出栈会取消页面协程，最后一次进度交给宿主作用域保存。 */
    fun recordProgress(card: ContentCard, index: Int, offset: Int) {
        viewModelScope.launch {
            if (preferences.settings.first().rememberHistory) library.record(card, index, offset)
        }
    }
}
