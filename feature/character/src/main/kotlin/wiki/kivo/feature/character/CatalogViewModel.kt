package wiki.kivo.feature.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import wiki.kivo.core.data.CharacterRepository
import wiki.kivo.core.data.local.SettingsRepository
import wiki.kivo.core.model.*

@HiltViewModel
class CatalogViewModel
@Inject
constructor(
    val repository: CharacterRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val restored = runCatching {
        Json.decodeFromString<CharacterQuery>(saved["catalog_query"] ?: "{}")
    }
        .getOrDefault(CharacterQuery())
    val query = MutableStateFlow(restored)
    val state = MutableStateFlow(LoadState<Page<CatalogEntry>>())
    val schools = MutableStateFlow(LoadState<List<NamedReference>>())
    var page = 0
        private set

    private var request: Job? = null
    private var debounce: Job? = null
    private var generation = 0

    init {
        load()
        loadSchools()
    }

    fun loadSchools() {
        viewModelScope.launch { repository.schools().collect { schools.value = it } }
    }

    fun compact(value: Boolean) {
        viewModelScope.launch { settings.setFlag("compact_catalog", value) }
    }

    fun search(value: String) {
        if (value.length > 120) return
        update(query.value.copy(search = value), delayed = true)
    }

    /** 查询一变立即使旧响应失效；400ms 防抖只延迟新请求，不延迟取消。 */
    fun update(next: CharacterQuery, delayed: Boolean = false) {
        if (next == query.value) return
        query.value = next
        saved["catalog_query"] = Json.encodeToString(next)
        generation++
        request?.cancel()
        debounce?.cancel()
        page = 0
        state.value = LoadState()
        debounce = viewModelScope.launch {
            if (delayed) delay(400)
            load()
        }
    }

    fun load(refresh: Boolean = false) {
        if (request?.isActive == true) return
        val token = generation
        val next = if (refresh) 1 else page + 1
        val previous = state.value.value
        val old = if (refresh) emptyList() else previous?.entries.orEmpty()
        request = viewModelScope.launch {
            repository.catalog(query.value, next, refresh).collect { result ->
                if (token != generation) return@collect
                val combined =
                    result.value?.let {
                        it.copy(entries = (old + it.entries).distinctBy { e -> e.student.id })
                    }
                state.value = result.copy(value = combined ?: previous)
                if (result.value != null) page = next
            }
        }
    }

    fun more() = page > 0 && page < (state.value.value?.maxPage ?: 1)
}
