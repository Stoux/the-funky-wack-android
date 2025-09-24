package nl.stoux.tfw.core.common.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.stoux.tfw.core.common.database.AppDatabase
import nl.stoux.tfw.core.common.database.dao.EditionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tfw.db")
            .fallbackToDestructiveMigration() // MVP: safe while schema stabilizes // TODO: Remove this
            .build()

    @Provides
    fun provideEditionDao(db: AppDatabase): EditionDao = db.editionDao()
}
