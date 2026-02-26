package nl.stoux.tfw.service.playback.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadDataSourceFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CacheDataSourceFactory

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    private const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "tfw_download_channel"

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): StandaloneDatabaseProvider = StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
    ): Cache {
        val downloadDirectory = File(context.filesDir, DOWNLOAD_CONTENT_DIRECTORY)
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs()
        }
        // NoOpCacheEvictor means we never auto-evict; user must manually delete downloads
        return SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    @DownloadDataSourceFactory
    fun provideDownloadDataSourceFactory(): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("TFW-Android/1.0")
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
    }

    @Provides
    @Singleton
    @CacheDataSourceFactory
    fun provideCacheDataSourceFactory(
        @DownloadCache cache: Cache,
        @DownloadDataSourceFactory upstreamFactory: DataSource.Factory,
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null) // Read-only for playback; writes happen via DownloadManager
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideDownloadExecutor(): Executor = Executors.newFixedThreadPool(2)

    @Provides
    @Singleton
    fun provideDownloadNotificationHelper(
        @ApplicationContext context: Context,
    ): DownloadNotificationHelper {
        return DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
        @DownloadCache cache: Cache,
        @DownloadDataSourceFactory dataSourceFactory: DataSource.Factory,
        executor: Executor,
    ): DownloadManager {
        return DownloadManager(
            context,
            databaseProvider,
            cache,
            dataSourceFactory,
            executor,
        )
    }
}
