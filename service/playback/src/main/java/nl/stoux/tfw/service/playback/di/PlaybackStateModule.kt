package nl.stoux.tfw.service.playback.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.stoux.tfw.service.playback.service.resume.PlaybackStateRepository
import nl.stoux.tfw.service.playback.service.resume.PlaybackStateRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackStateBindModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackStateRepository(impl: PlaybackStateRepositoryImpl): PlaybackStateRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackStateProvideModule {
    @Provides
    @Singleton
    fun providePlaybackStateDataStore(
        @ApplicationContext appContext: Context
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("playback_state") }
    )
}