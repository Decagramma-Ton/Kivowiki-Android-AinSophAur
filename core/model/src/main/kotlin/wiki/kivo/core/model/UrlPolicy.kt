package wiki.kivo.core.model

import java.net.URI

/** 资源和导航分别规范化。任何账号凭据都不由 URL 策略添加。 */
object UrlPolicy {
    private val resourceHosts = setOf("static.kivo.wiki", "kivo.wiki")

    fun resource(raw: String?): String? = normalise(raw, "https://static.kivo.wiki/")

    fun link(raw: String?): String? = normalise(raw, "https://kivo.wiki/")

    fun isFirstPartyImage(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in resourceHosts
    }
        .getOrDefault(false)

    private fun normalise(raw: String?, base: String): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.length < 8192 } ?: return null
        if (text.any { it.code < 32 } || '\\' in text) return null
        val candidate =
            when {
                text.startsWith("//") -> "https:$text"
                text.startsWith("https://") -> text
                text.startsWith("http://static.kivo.wiki/") -> text.replaceFirst("http:", "https:")
                Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(text) -> return null
                Regex("^(?:kivo\\.wiki|x\\.com|www\\.[A-Za-z0-9.-]+)(?:/|$)")
                    .containsMatchIn(text) -> "https://$text"
                else -> base + text.removePrefix("/")
            }
        return runCatching {
            val uri = URI(candidate.replace(" ", "%20"))
            if (
                uri.scheme != "https" ||
                    uri.host.isNullOrBlank() ||
                    uri.rawUserInfo != null ||
                    uri.port !in listOf(-1, 443)
            )
                null
            else uri.toASCIIString()
        }
            .getOrNull()
    }

    /** 只有已验证的网页路由转为原生详情；未知 query/锚点保留在网页入口。 */
    fun entity(raw: String): EntityKey? = runCatching {
        val safe = link(raw) ?: return null
        val uri = URI(safe)
        if (uri.host != "kivo.wiki" || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.path.trim('/')
        EntityType.entries
            .filter { it.webPath.isNotEmpty() }
            .firstNotNullOfOrNull { type ->
                Regex("^${Regex.escape(type.webPath)}/([1-9][0-9]{0,8})$")
                    .matchEntire(path)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?.let { EntityKey(type, it) }
            }
    }
        .getOrNull()
}
