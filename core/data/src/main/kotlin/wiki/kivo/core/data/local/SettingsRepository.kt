package wiki.kivo.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import wiki.kivo.core.model.*

private val Context.kivoSettings by preferencesDataStore("kivo_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val server = stringPreferencesKey("server")
    private val scale = floatPreferencesKey("reading_scale")

    private fun flag(name: String) = booleanPreferencesKey(name)

    val settings: Flow<AppSettings> =
        context.kivoSettings.data
            .catch {
                if (it is IOException) emit(emptyPreferences()) else throw it
            }
            .map { p ->
                AppSettings(
                    theme = ThemeMode.entries.find { it.name == p[theme] } ?: ThemeMode.SYSTEM,
                    server = GameServer.entries.find { it.name == p[server] } ?: GameServer.JP,
                    readingScale =
                        (p[scale] ?: 1f).takeIf { it.isFinite() }?.coerceIn(.85f, 1.5f) ?: 1f,
                    loadImages = p[flag("images")] ?: true,
                    externalImages = p[flag("external_images")] ?: true,
                    reducedMotion = p[flag("reduce_motion")] ?: false,
                    rememberHistory = p[flag("history")] ?: true,
                    showBirthdays = p[flag("birthdays")] ?: true,
                    showHistory = p[flag("anniversary")] ?: true,
                    startDestination =
                        StartDestination.entries.find {
                            it.name == p[stringPreferencesKey("start_destination")]
                        } ?: StartDestination.HOME,
                    translation =
                        TranslationMode.entries.find {
                            it.name == p[stringPreferencesKey("translation")]
                        } ?: TranslationMode.FAN,
                    levelMax = p[flag("level_max")] ?: true,
                    searchAutoRedirect = p[flag("search_auto_redirect")] ?: true,
                    festiveEffects = p[flag("festive_effects")] ?: true,
                    compactCatalog = p[flag("compact_catalog")] ?: false,
                    showCharacterBackground = p[flag("character_background")] ?: true,
                    swipeCategories = p[flag("swipe_categories")] ?: true,
                    compactOrganization = p[flag("compact_organization")] ?: false,
                    cacheLimit =
                        CacheLimit.entries.find {
                            it.name == p[stringPreferencesKey("cache_limit")]
                        } ?: CacheLimit.GB_1,
                    spineGpu = p[flag("spine_gpu")] ?: true,
                    spineFixBlend = p[flag("spine_fix_blend")] ?: true,
                    spineExperimental = p[flag("spine_experimental")] ?: false,
                )
            }
            .distinctUntilChanged()

    suspend fun setTheme(value: ThemeMode) {
        context.kivoSettings.edit { it[theme] = value.name }
    }

    suspend fun setCacheLimit(value: CacheLimit) {
        context.kivoSettings.edit { it[stringPreferencesKey("cache_limit")] = value.name }
    }

    suspend fun setStartDestination(value: StartDestination) {
        context.kivoSettings.edit { it[stringPreferencesKey("start_destination")] = value.name }
    }

    suspend fun setTranslation(value: TranslationMode) {
        context.kivoSettings.edit { it[stringPreferencesKey("translation")] = value.name }
    }

    suspend fun setServer(value: GameServer) {
        context.kivoSettings.edit { it[server] = value.name }
    }

    suspend fun setScale(value: Float) {
        require(value.isFinite())
        context.kivoSettings.edit { it[scale] = value.coerceIn(.85f, 1.5f) }
    }

    suspend fun setFlag(name: String, value: Boolean) {
        require(
            name in
                setOf(
                    "images",
                    "external_images",
                    "reduce_motion",
                    "history",
                    "birthdays",
                    "anniversary",
                    "level_max",
                    "search_auto_redirect",
                    "festive_effects",
                    "compact_catalog",
                    "compact_organization",
                    "character_background",
                    "swipe_categories",
                    "spine_gpu",
                    "spine_fix_blend",
                    "spine_experimental",
                )
        )
        context.kivoSettings.edit { it[flag(name)] = value }
    }
}
