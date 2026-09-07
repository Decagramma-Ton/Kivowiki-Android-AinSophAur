package wiki.kivo.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** 角色模块复用公共 API、缓存、并发及错误策略，搜索查询仅在内存和返回状态中保留。 */
@Singleton
class CharacterRepository
@Inject
constructor(private val content: ContentRepository, private val api: KivoApi) {
    fun catalog(
        query: CharacterQuery,
        page: Int,
        force: Boolean = false,
    ): Flow<LoadState<Page<CatalogEntry>>> {
        if (query.search.isBlank())
            return content.observe(
                "data/students",
                query.parameters(page),
                3600,
                force,
                CharacterMapper::catalog,
            )
        return flow {
                emit(LoadState<Page<CatalogEntry>>())
                try {
                    emit(
                        LoadState(
                            CharacterMapper.catalog(
                                api.get(api.url("data/students", query.parameters(page))).data
                            ),
                            loading = false,
                        )
                    )
                } catch (e: TimeoutCancellationException) {
                    emit(LoadState(loading = false, error = "请求超时，请重试"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(LoadState(loading = false, error = e.friendlyMessage()))
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun profile(id: Int, force: Boolean = false) =
        content.observe("data/students/$id", ttl = 86400, force = force) {
            CharacterMapper.profile(it, id)
        }

    fun school(id: Int) =
        content.observe("data/schools/$id", ttl = 86400, mapper = CharacterMapper::reference)

    fun relation(id: Int) =
        content.observe("data/relations/$id", ttl = 86400, mapper = CharacterMapper::reference)

    fun equipment(id: Int) =
        content.observe("data/equipments/$id", ttl = 86400, mapper = CharacterMapper::equipment)

    fun item(id: Int) =
        content.observe("data/items/$id", ttl = 86400, mapper = CharacterMapper::item)

    fun media(id: Int, spine: Boolean) =
        content.observe("data/${if(spine) "spines" else "models"}/$id", ttl = 86400) {
            CharacterMapper.media(it, spine)
        }

    fun supplements(uuid: String, page: Int = 1, force: Boolean = false) =
        content.observe(
            "interactive/supplementarys/$uuid",
            mapOf("page" to "$page", "page_size" to "20"),
            300,
            force,
            CharacterMapper::supplements,
        )

    fun reactionIcons() =
        content.observe("interactive/declares/icons", ttl = 86400, mapper = CharacterMapper::icons)

    /** 目录本身有分页；即使未来组织超过 100 个，也不能只拿第一页当完整选项。 */
    fun schools(force: Boolean = false): Flow<LoadState<List<NamedReference>>> =
        flow<LoadState<List<NamedReference>>> {
                emit(LoadState())
                val result = mutableListOf<NamedReference>()
                var page = 1
                var maxPage = 1
                do {
                    val current =
                        content
                            .observe(
                                "data/schools",
                                mapOf("page" to "$page", "page_size" to "100"),
                                86400,
                                force,
                            ) { data ->
                                Page(
                                    data.array("school").map {
                                        CharacterMapper.reference(it.asObject())
                                    },
                                    (data.number("max_page") ?: 1).toInt(),
                                )
                            }
                            .first { !it.loading }
                    val data = current.value
                    if (data == null) {
                        emit(
                            LoadState(
                                result.toList().takeIf { it.isNotEmpty() },
                                false,
                                error = current.error,
                            )
                        )
                        return@flow
                    }
                    result += data.entries
                    maxPage = data.maxPage.coerceAtLeast(1)
                    if (current.error != null) {
                        emit(LoadState(result.distinctBy { it.id }, false, error = current.error))
                        return@flow
                    }
                    page++
                } while (page <= maxPage)
                emit(LoadState(result.distinctBy { it.id }, false))
            }
            .flowOn(Dispatchers.IO)
}
