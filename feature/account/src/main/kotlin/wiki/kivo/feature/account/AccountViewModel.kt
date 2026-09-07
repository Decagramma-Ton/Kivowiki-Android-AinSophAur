package wiki.kivo.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import wiki.kivo.core.data.account.AccountRepository
import wiki.kivo.core.data.local.LibraryRepository
import wiki.kivo.core.data.local.SettingsRepository
import wiki.kivo.core.model.*

@HiltViewModel
class AccountViewModel
@Inject
constructor(
    val account: AccountRepository,
    val settingsRepository: SettingsRepository,
    val library: LibraryRepository,
) : ViewModel() {
    val state = account.state
    val bookmarks =
        library.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history =
        library.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { account.restore() }
    }

    fun login(name: String, password: String) {
        viewModelScope.launch { account.login(name, password) }
    }

    fun logout() {
        viewModelScope.launch { account.logout() }
    }

    fun revalidate() {
        viewModelScope.launch { account.revalidate() }
    }

    fun theme(value: ThemeMode) {
        viewModelScope.launch { settingsRepository.setTheme(value) }
    }

    fun startDestination(value: StartDestination) {
        viewModelScope.launch { settingsRepository.setStartDestination(value) }
    }

    fun translation(value: TranslationMode) {
        viewModelScope.launch { settingsRepository.setTranslation(value) }
    }

    fun scale(value: Float) {
        viewModelScope.launch { settingsRepository.setScale(value) }
    }

    fun flag(name: String, value: Boolean) {
        viewModelScope.launch { settingsRepository.setFlag(name, value) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }
}
