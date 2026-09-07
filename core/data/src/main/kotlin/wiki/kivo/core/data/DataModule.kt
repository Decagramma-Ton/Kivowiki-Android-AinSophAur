package wiki.kivo.core.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import wiki.kivo.core.data.local.KivoDatabase
import wiki.kivo.core.data.network.KivoService
import wiki.kivo.core.data.network.SameOriginRedirects

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun http(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 8
                    maxRequestsPerHost = 4
                }
            )
            .retryOnConnectionFailure(false)
            // 关闭无条件重定向，交给下面的同源策略处理 Go 路由的尾部斜杠。
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(SameOriginRedirects())
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", "KivoArchive-Android/0.4.1")
                        .build()
                )
            }
            .build()

    @Provides
    @Singleton
    fun service(client: OkHttpClient, json: Json): KivoService =
        Retrofit.Builder()
            .baseUrl("https://api.kivo.wiki/api/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KivoService::class.java)

    @Provides
    @Singleton
    @Named("publicRead")
    fun publicService(client: OkHttpClient, json: Json): KivoService =
        service(client.newBuilder().retryOnConnectionFailure(true).build(), json)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KivoDatabase =
        Room.databaseBuilder(context, KivoDatabase::class.java, "kivo.db").build()
}
