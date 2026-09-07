package wiki.kivo.feature.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import wiki.kivo.core.data.*
import wiki.kivo.core.data.local.*
import wiki.kivo.core.media.CharacterAssets
import wiki.kivo.core.model.*

@HiltViewModel
class OrganizationViewModel
@Inject
constructor(
    val repository: OrganizationRepository,
    val assets: CharacterAssets,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val catalog = MutableStateFlow(LoadState<List<OrganizationProfile>>())
    private var catalogJob: Job? = null
    private var nextPage = 1
    private var maxPage = 1
    val canLoadMore
        get() = nextPage <= maxPage

    /** 每页失败保留已加载条目；刷新成功第一页后才替换旧目录。 */
    fun loadCatalog(force: Boolean = false) {
        if (catalogJob?.isActive == true) return
        if (!force && catalog.value.value != null) return
        nextPage = 1
        maxPage = 1
        page(force)
    }

    fun page(force: Boolean = false) {
        if (catalogJob?.isActive == true || !canLoadMore) return
        catalogJob = viewModelScope.launch {
            catalog.value = catalog.value.copy(loading = true, error = null)
            repository.catalog(nextPage, force).collect { result ->
                val data = result.value
                if (data != null) {
                    val before = if (nextPage == 1) emptyList() else catalog.value.value.orEmpty()
                    catalog.value =
                        LoadState(
                            (before + data.entries).distinctBy { it.id },
                            result.loading,
                            result.fetchedAt,
                            result.fromCache,
                            result.error,
                        )
                    if (!result.loading && result.error == null) {
                        maxPage = data.maxPage
                        nextPage++
                    }
                } else
                    catalog.value =
                        catalog.value.copy(loading = result.loading, error = result.error)
            }
        }
    }

    fun record(card: ContentCard, position: Int, offset: Int) = viewModelScope.launch {
        if (settings.settings.first().rememberHistory) library.record(card, position, offset)
    }

    /** 组织目录独立记住显示偏好，不覆盖角色图鉴的视图选择。 */
    fun compact(value: Boolean) = viewModelScope.launch {
        settings.setFlag("compact_organization", value)
    }

    fun bookmark(card: ContentCard, saved: Boolean) = viewModelScope.launch {
        if (saved) library.removeBookmark(card.key) else library.addBookmark(card)
    }
}
