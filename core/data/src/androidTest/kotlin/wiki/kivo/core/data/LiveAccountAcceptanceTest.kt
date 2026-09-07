package wiki.kivo.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import wiki.kivo.core.data.account.*

/** 仅在操作者显式提供运行时参数时执行真实账号验收。凭据和权限正文不写入源码、截图或日志。 */
@RunWith(AndroidJUnit4::class)
class LiveAccountAcceptanceTest {
    @Test
    fun authorizedLoginPrivateReadRestoreAndLogout() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val username = arguments.getString("liveAccount")
        val password = arguments.getString("livePassword")
        assumeTrue("未提供专用测试账号，跳过真实登录", !username.isNullOrBlank() && !password.isNullOrBlank())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = DataModule.json()
        val client = DataModule.http()
        val service = DataModule.service(client, json)
        val vault = SessionVault(context, json)
        val account = AccountRepository(service, vault, ServerClock())
        var restored: AccountRepository? = null
        try {
            account.login(username!!, password!!)
            assertTrue("原生登录未完成：" + account.state.value.message, account.state.value.user != null)
            val article = account.readArticle(33)
            assertTrue("权限正文应仅驻留内存", article.privateContent)
            assertTrue("权限正文应非空", article.body.isNotBlank())
            assertTrue("示例正文应保留站点提示块语法", article.body.contains("!!! note"))
            restored = AccountRepository(service, vault, ServerClock())
            restored.restore()
            assertNotNull("加密会话应可恢复", restored.state.value.user)
            assertFalse("恢复后必须完成服务器验证", restored.state.value.checking)
            restored.logout()
            assertNull(restored.state.value.user)
            assertNull("退出必须清理加密会话", vault.read())
        } finally {
            if (restored?.state?.value?.user != null) restored.logout()
            account.logout()
            vault.clear()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
