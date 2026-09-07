package wiki.kivo.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import wiki.kivo.core.content.*
import wiki.kivo.core.data.*
import wiki.kivo.core.data.account.AccountRepository
import wiki.kivo.core.data.local.*
import wiki.kivo.core.data.network.friendlyMessage
import wiki.kivo.core.media.*
import wiki.kivo.core.model.*

@HiltViewModel
class CharacterViewModel
@Inject
constructor(
    val repository: CharacterRepository,
    val organizations: OrganizationRepository,
    val account: AccountRepository,
    val assets: CharacterAssets,
    val playback: CharacterPlayback,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state = MutableStateFlow(LoadState<CharacterProfile>())
    val blocks = MutableStateFlow<List<ContentBlock>>(emptyList())
    private val reactionControllers = mutableMapOf<Pair<String, String?>, ReactionController>()

    internal fun reactions(uuid: String, user: String?): ReactionController =
        reactionControllers.getOrPut(uuid to user) {
            ReactionController(repository, account, viewModelScope, uuid).also {
                if (uuid.isNotBlank()) it.load()
            }
        }

    private val supplementaryStates =
        mutableMapOf<Pair<String, Int>, StateFlow<LoadState<Page<Supplement>>>>()

    internal fun supplements(
        uuid: String,
        page: Int,
        force: Boolean = false,
    ): StateFlow<LoadState<Page<Supplement>>> {
        val key = uuid to page
        if (force || key !in supplementaryStates) {
            supplementaryStates[key] =
                repository
                    .supplements(uuid, page, force)
                    .stateIn(
                        viewModelScope,
                        SharingStarted.Eagerly,
                        supplementaryStates[key]?.value ?: LoadState(),
                    )
        }
        return supplementaryStates.getValue(key)
    }

    private var request: Job? = null
    private var id = 0

    fun load(value: Int, force: Boolean = false) {
        if (id == value && !force) return
        if (id != value) {
            state.value = LoadState()
            blocks.value = emptyList()
        }
        id = value
        request?.cancel()
        request = viewModelScope.launch {
            repository.profile(value, force).collectLatest { result ->
                state.value = result
                result.value?.let { profile ->
                    blocks.value =
                        withContext(Dispatchers.Default) {
                            ContentParser.parse(profile.more).readingBlocks()
                        }
                    if (settings.settings.first().rememberHistory)
                        library.record(profile.student.card(), 0, 0)
                }
            }
        }
    }

    fun bookmark(saved: Boolean) {
        state.value.value?.student?.card()?.let { card ->
            viewModelScope.launch {
                if (saved) library.removeBookmark(card.key) else library.addBookmark(card)
            }
        }
    }
}

/** 页面内的三个表态区互相独立；失败时先读取服务端状态，避免对 toggle 接口盲目重发。 */
internal class ReactionController(
    private val repository: CharacterRepository,
    private val account: AccountRepository,
    private val scope: CoroutineScope,
    private val uuid: String,
) {
    val state = MutableStateFlow(LoadState<List<Reaction>>())
    var uncertain = false
        private set

    private var job: Job? = null

    fun load() {
        if (job?.isActive == true) return
        job = scope.launch {
            state.value = state.value.copy(loading = true, error = null)
            try {
                val icons =
                    repository
                        .reactionIcons()
                        .first { !it.loading }
                        .let { it.value ?: throw java.io.IOException(it.error ?: "表情暂不可用") }
                state.value =
                    LoadState(CharacterMapper.reactions(account.declarations(uuid), icons), false)
                uncertain = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, error = e.friendlyMessage())
            }
        }
    }

    fun toggle(icon: Int) {
        if (job?.isActive == true || uncertain) return
        job = scope.launch {
            state.value = state.value.copy(loading = true, error = null)
            try {
                account.declare(uuid, icon)
                val icons = state.value.value.orEmpty()
                state.value =
                    LoadState(CharacterMapper.reactions(account.declarations(uuid), icons), false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uncertain = true
                state.value =
                    state.value.copy(
                        loading = false,
                        error = "${e.friendlyMessage()}。请刷新表态确认结果后再操作。",
                    )
            }
        }
    }
}
