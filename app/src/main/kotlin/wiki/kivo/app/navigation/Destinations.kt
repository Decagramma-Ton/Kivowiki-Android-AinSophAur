package wiki.kivo.app.navigation

import androidx.navigation3.runtime.NavKey
import java.net.URI
import kotlinx.serialization.Serializable
import wiki.kivo.core.model.*

@Serializable data class Root(val tab: Int) : NavKey

@Serializable data class Detail(val entity: EntityKey, val characterTab: Int = 0) : NavKey

@Serializable data class Portal(val id: String) : NavKey

@Serializable data class Library(val kind: String) : NavKey

@Serializable data object Settings : NavKey

@Serializable data object Login : NavKey

@Serializable data object Bulletins : NavKey

@Serializable data object About : NavKey

@Serializable data object Diagnostics : NavKey

@Serializable data object Search : NavKey

@Serializable data class Community(val kind: String) : NavKey

/** 验收链接只接受已知实体 + 正整数，不接收任意网络目标或凭据。 */
fun parsePreviewLink(raw: String): EntityKey? = runCatching {
    val uri = URI(raw)
    if (
        uri.scheme != "kivoarchive" ||
            uri.host != "content" ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.query != null
    )
        return null
    val parts = uri.path.trim('/').split('/')
    if (parts.size != 2) return null
    val type = EntityType.entries.find { it.name.equals(parts[0], true) } ?: return null
    val id = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    EntityKey(type, id)
}
    .getOrNull()
