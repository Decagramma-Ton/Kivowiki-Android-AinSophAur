package wiki.kivo.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import wiki.kivo.core.data.ContentRepository
import wiki.kivo.core.data.network.friendlyMessage
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val page: Int = 0,
    val maxPage: Int = 1,
    val loading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val repository: ContentRepository) : ViewModel() {
    val state = MutableStateFlow(SearchUiState())
    private var job: Job? = null
    private val redirects = MutableSharedFlow<SearchResult>(extraBufferCapacity = 1)
    val redirect = redirects.asSharedFlow()

    /** 每次输入取消前一个请求；确认搜索才允许自动跳转，事件不重放以免返回时再跳。 */
    fun search(
        raw: String,
        confirm: Boolean = false,
        autoRedirect: Boolean = false,
        append: Boolean = false,
    ) {
        val query = raw.trim().take(120)
        if (append && (state.value.loading || state.value.page >= state.value.maxPage)) return
        job?.cancel()
        if (query.isBlank()) {
            state.value = SearchUiState()
            return
        }
        val previous = if (append) state.value else SearchUiState(query = query)
        state.value = previous.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                if (!confirm && !append) delay(350)
                val page = if (append) previous.page + 1 else 1
                val result = repository.search(query, page)
                ensureActive()
                state.value =
                    SearchUiState(
                        query,
                        (previous.results + result.entries).distinctBy { it.key },
                        page,
                        result.maxPage,
                    )
                // 只对完整的单页结果判定唯一性，避免后续页仍有同名资料时误跳。
                if (confirm && autoRedirect && result.maxPage == 1)
                    uniqueExactMatch(result.entries, query)?.let { redirects.emit(it) }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException)
                    state.value = previous.copy(error = cancelled.friendlyMessage())
                else throw cancelled
            } catch (error: Exception) {
                state.value = previous.copy(error = error.friendlyMessage())
            }
        }
    }
}

@Composable
fun SearchScreen(repository: ContentRepository, open: (SearchResult) -> Unit) {
    val vm: SearchViewModel =
        viewModel(factory = viewModelFactory { initializer { SearchViewModel(repository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val autoRedirect = LocalKivoSettings.current.searchAutoRedirect
    val currentOpen by rememberUpdatedState(open)
    LaunchedEffect(vm) { vm.redirect.collect { currentOpen(it) } }
    LaunchedEffect(Unit) { if (query.isNotBlank() && state.query.isBlank()) vm.search(query) }
    val submit = {
        keyboard?.hide()
        vm.search(query, confirm = true, autoRedirect = autoRedirect)
    }
    Column(Modifier.fillMaxSize().testTag("search_screen")) {
        OutlinedTextField(
            query,
            {
                query = it.take(120)
                vm.search(query)
            },
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("global_search_input"),
            singleLine = true,
            label = { Text("搜索古书馆") },
            placeholder = { Text("角色、物品、文章、音乐…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty())
                    IconButton(
                        onClick = {
                            query = ""
                            vm.search("")
                        }
                    ) {
                        Icon(Icons.Outlined.Close, "清空搜索")
                    }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "全站搜索",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = submit,
                enabled = query.isNotBlank(),
                modifier = Modifier.testTag("submit_search"),
            ) {
                Text("搜索")
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("search_results"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error ->
                item("error") { StatusNote(error, "重试") { vm.search(query, confirm = true) } }
            }
            if (state.results.isEmpty() && !state.loading && state.error == null)
                item("empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painterResource(wiki.kivo.core.designsystem.R.drawable.mascot_search),
                            null,
                            Modifier.size(128.dp),
                        )
                        Text(
                            if (query.isBlank()) "想找些什么，老师？" else "暂时没有找到相关资料",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (query.isBlank()) "从一个名字、一件物品或一段故事开始。" else "试试简称、别名，或换一个关键词。",
                            Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            items(state.results, key = { it.key }, contentType = { "result" }) { result ->
                KivoCard(
                    onClick = {
                        keyboard?.hide()
                        open(result)
                    }
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            result.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(result.title, style = MaterialTheme.typography.titleMedium)
                        if (result.summary.isNotBlank())
                            Text(
                                result.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                    }
                }
            }
            if (state.page < state.maxPage && state.results.isNotEmpty())
                item("more") {
                    OutlinedButton(
                        onClick = { vm.search(query, append = true) },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.loading) "读取中…" else "更多结果")
                    }
                }
        }
    }
}
