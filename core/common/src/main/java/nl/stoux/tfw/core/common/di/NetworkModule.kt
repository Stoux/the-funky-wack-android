package nl.stoux.tfw.core.common.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.device.DeviceIdManager
import nl.stoux.tfw.core.common.network.ApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authRepository: dagger.Lazy<AuthRepository>,
        deviceIdManager: DeviceIdManager,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        // Device interceptor: adds X-Client-ID and custom User-Agent
        val deviceInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-Client-ID", deviceIdManager.clientId)
                .header(
                    "User-Agent",
                    "TheFunkyWack-Android/$versionName (${Build.MODEL}; Android ${Build.VERSION.SDK_INT})"
                )
                .build()
            chain.proceed(request)
        }

        // Auth interceptor: adds Bearer token if logged in
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            val token = authRepository.get().token
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val response = chain.proceed(requestBuilder.build())
            // Handle 401: clear auth state directly
            if (response.code == 401) {
                authRepository.get().onUnauthorized()
            }
            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(deviceInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://tfw.stoux.nl/api/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(okHttpClient)
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
