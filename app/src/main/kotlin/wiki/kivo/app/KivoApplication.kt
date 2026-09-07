package wiki.kivo.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class KivoApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var http: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(android.net.ConnectivityManager::class.java)
        // 一次进程级监听；只在网络首次通过验证或默认网络切换时唤醒可见失败组件。
        manager.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var usable: android.net.Network? = null

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    capabilities: android.net.NetworkCapabilities,
                ) {
                    if (
                        capabilities.hasCapability(
                            android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        ) && usable != network
                    ) {
                        usable = network
                        wiki.kivo.core.model.NetworkRecovery.connected()
                    }
                }

                override fun onLost(network: android.net.Network) {
                    if (usable == network) usable = null
                }
            }
        )
    }

    // 图片与 API 分开排队，但复用连接池。大量头像不能占满资料读取的四个槽位。
    private val imageHttp by lazy {
        http
            .newBuilder()
            .retryOnConnectionFailure(true)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 12
                    maxRequestsPerHost = 6
                }
            )
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttp })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, .18).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(wiki.kivo.core.model.CacheLimit.IMAGE_BYTES)
                    .build()
            }
            .build()
}
