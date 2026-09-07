package wiki.kivo.core.data.account

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import wiki.kivo.core.data.ServerClock
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** 单一会话源。认证流量与公共内容分离；离线不冒充令牌已通过服务器验证。 */
@Singleton
class AccountRepository
@Inject
constructor(
    private val service: KivoService,
    private val vault: SessionVault,
    private val clock: ServerClock,
) {
    private val mutable = MutableStateFlow(AccountState(checking = true))
    val state: StateFlow<AccountState> = mutable.asStateFlow()
    private val mutex = Mutex()
    private var refreshToken: String? = null
    private var accessToken: String? = null
    private var restored = false

    suspend fun restore() = mutex.withLock {
        if (restored) return@withLock
        restored = true
        try {
            val stored = vault.read()
            if (stored == null) {
                mutable.value = AccountState()
                return@withLock
            }
            refreshToken = stored.refresh
            mutable.value = AccountState(stored.profile, checking = true)
            accessToken = obtainAccess(stored.refresh)
            val user = fetchProfile(accessToken!!)
            vault.write(PersistedSession(stored.refresh, user))
            mutable.value = AccountState(user)
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException)
                throw failure
            if (isExpired(failure)) {
                clearLocal()
                mutable.value = AccountState(message = "登录已过期，请重新登录")
            } else if (refreshToken != null) {
                mutable.update { it.copy(checking = false, message = "已保留本机会话，联网后可重新验证") }
            } else {
                clearLocal()
                mutable.value = AccountState(message = "本机会话无法恢复，请重新登录")
            }
        }
    }

    suspend fun login(account: String, password: String) = mutex.withLock {
        if (mutable.value.busy) return@withLock
        if (account.isBlank() || password.isEmpty()) {
            mutable.update { it.copy(message = "请输入用户名或邮箱与密码") }
            return@withLock
        }
        mutable.value = AccountState(busy = true)
        var issued: String? = null
        try {
            withTimeout(60_000) {
                val challenge =
                    checkedPayload(service.challenge(buildJsonObject { put("difficulty", 4) }))
                clock.record(challenge.serverTime)
                val proof = ProofOfWork.solve(challenge.data.text("challenge"), clock::now)
                val result =
                    checkedPayload(
                        service.login(
                            buildJsonObject {
                                put("account", account.trim())
                                put("password", password)
                            },
                            proof,
                        )
                    )
                val refresh =
                    result.data.text("refresh_token").also {
                        require(it.isNotBlank()) { "登录返回缺少会话凭据" }
                    }
                issued = refresh
                val access = obtainAccess(refresh)
                val profile = fetchProfile(access)
                vault.write(PersistedSession(refresh, profile))
                refreshToken = refresh
                accessToken = access
                mutable.value = AccountState(profile, message = "欢迎回来，${profile.name}")
            }
        } catch (failure: Exception) {
            issued?.let { token ->
                withContext(NonCancellable) {
                    withTimeoutOrNull(3000) { runCatching { service.logout("Bearer $token") } }
                }
            }
            if (failure is CancellationException && failure !is TimeoutCancellationException) {
                mutable.value = AccountState()
                throw failure
            }
            mutable.value =
                AccountState(
                    message =
                        if (failure is ApiFailure && failure.httpCode == 401) "账号或密码不正确，请检查后重试"
                        else failure.friendlyMessage()
                )
        }
    }

    /** 无论远端是否在线，都原子删除本机会话；远端撤销失败有明确提示。 */
    suspend fun logout() = mutex.withLock {
        mutable.update { it.copy(busy = true) }
        var remoteConfirmed = refreshToken == null
        try {
            refreshToken?.let {
                checkedPayload(service.logout("Bearer $it"))
                remoteConfirmed = true
            }
        } catch (failure: Exception) {
            if (isExpired(failure)) remoteConfirmed = true
        } finally {
            withContext(NonCancellable) {
                clearLocal()
                mutable.value =
                    AccountState(message = if (remoteConfirmed) "已退出登录" else "本机已退出；网络未能确认服务端会话撤销")
            }
        }
    }

    suspend fun revalidate() = mutex.withLock {
        val refresh = refreshToken ?: return@withLock
        mutable.update { it.copy(checking = true) }
        try {
            val access = obtainAccess(refresh)
            val profile = fetchProfile(access)
            accessToken = access
            vault.write(PersistedSession(refresh, profile))
            mutable.value = AccountState(profile, message = "会话已验证")
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException)
                throw failure
            if (isExpired(failure)) {
                clearLocal()
                mutable.value = AccountState(message = "登录已过期，请重新登录")
            } else mutable.update { it.copy(checking = false, message = failure.friendlyMessage()) }
        }
    }

    fun dismissMessage() {
        mutable.update { it.copy(message = null) }
    }

    /** 私有正文只返回给当前阅读器，不经过公共缓存；互斥锁使刷新令牌和退出保持顺序一致。 */
    suspend fun readArticle(id: Int): ContentDetail = mutex.withLock {
        require(id > 0)
        val refresh = refreshToken ?: throw ApiFailure(401, null, "请登录有权限的古书馆账号")
        try {
            val access = accessToken ?: obtainAccess(refresh).also { accessToken = it }
            val payload =
                try {
                    checkedPayload(service.article(id, "Bearer $access"))
                } catch (failure: ApiFailure) {
                    if (!isExpired(failure)) throw failure
                    val renewed = obtainAccess(refresh).also { accessToken = it }
                    checkedPayload(service.article(id, "Bearer $renewed"))
                }
            wiki.kivo.core.data.ContentMapper.detail(
                    payload.data,
                    EntityKey(EntityType.ARTICLE, id),
                )
                .copy(privateContent = true)
        } catch (failure: ApiFailure) {
            if (isExpired(failure)) {
                clearLocal()
                mutable.value = AccountState(message = "登录已过期，请重新登录")
            }
            throw failure
        }
    }

    /** 选中状态依赖账号，禁止写进公共 Room 缓存。只读可刷新令牌，表态请求不做网络重试。 */
    suspend fun declarations(uuid: String): JsonObject = mutex.withLock {
        require(runCatching { java.util.UUID.fromString(uuid) }.isSuccess)
        if (refreshToken == null)
            return@withLock checkedPayload(service.declarations(uuid, null)).data
        authenticated { access -> service.declarations(uuid, access) }
    }

    suspend fun declare(uuid: String, icon: Int) = mutex.withLock {
        require(icon > 0 && runCatching { java.util.UUID.fromString(uuid) }.isSuccess)
        authenticated(allowEmptyData = true) { access ->
            service.declare(uuid, access, buildJsonObject { put("icon_id", icon) })
        }
    }

    /** 补充内容由页面预览后提交；复用账号刷新流程，网络结果不确定时绝不自动补发。 */
    suspend fun supplement(uuid: String, content: String) = mutex.withLock {
        require(
            content.isNotBlank() &&
                content.length <= 16_000 &&
                runCatching { java.util.UUID.fromString(uuid) }.isSuccess
        )
        authenticated(allowEmptyData = true) { access ->
            service.supplement(uuid, access, buildJsonObject { put("content", content) })
        }
    }

    private suspend fun authenticated(
        allowEmptyData: Boolean = false,
        request: suspend (String) -> retrofit2.Response<JsonObject>,
    ): JsonObject {
        val refresh = refreshToken ?: throw ApiFailure(401, null, "请先登录古书馆账号")
        try {
            val access = accessToken ?: obtainAccess(refresh).also { accessToken = it }
            return try {
                checkedPayload(request("Bearer $access"), allowEmptyData).data
            } catch (failure: ApiFailure) {
                // 只有明确的未授权响应可在刷新后重发；超时可能已经生效，交给用户刷新确认。
                if (!isExpired(failure)) throw failure
                val renewed = obtainAccess(refresh).also { accessToken = it }
                checkedPayload(request("Bearer $renewed"), allowEmptyData).data
            }
        } catch (failure: ApiFailure) {
            if (isExpired(failure)) {
                clearLocal()
                mutable.value = AccountState(message = "登录已过期，请重新登录")
            }
            throw failure
        }
    }

    private suspend fun clearLocal() {
        refreshToken = null
        accessToken = null
        vault.clear()
    }

    private suspend fun obtainAccess(refresh: String): String =
        checkedPayload(service.accessToken("Bearer $refresh")).data.text("access_token").also {
            require(it.isNotBlank())
        }

    private suspend fun fetchProfile(access: String): UserProfile {
        val p = checkedPayload(service.profile("Bearer $access")).data
        val id = p.text("id").ifBlank { p.text("user_id") }
        require(id.isNotBlank()) { "账号资料缺少编号" }
        return UserProfile(
            id,
            p.text("nickname")
                .ifBlank { p.text("nick_name") }
                .ifBlank { p.text("user_name") }
                .ifBlank { "老师" },
            UrlPolicy.resource(p.text("avatar")),
        )
    }

    private fun isExpired(failure: Exception): Boolean =
        failure is ApiFailure && (failure.httpCode == 401 || failure.businessCode == 4010)
}
