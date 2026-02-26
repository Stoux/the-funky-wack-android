package nl.stoux.tfw.core.common.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.stoux.tfw.core.common.database.AppDatabase
import nl.stoux.tfw.core.common.database.Migrations
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.database.dao.LivesetDownloadDao
import nl.stoux.tfw.core.common.database.dao.ManualQueueDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tfw.db")
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(1, 2, 3) // Wipe DB for versions before downloads feature
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideEditionDao(db: AppDatabase): EditionDao = db.editionDao()

    @Provides
    fun provideManualQueueDao(db: AppDatabase): ManualQueueDao = db.manualQueueDao()

    @Provides
    fun provideLivesetDownloadDao(db: AppDatabase): LivesetDownloadDao = db.livesetDownloadDao()
}
