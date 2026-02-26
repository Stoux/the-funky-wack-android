package nl.stoux.tfw.tv.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.stoux.tfw.tv.data.TvSettingsRepository
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TvSettingsDataStore

@Module
@InstallIn(SingletonComponent::class)
object TvSettingsModule {

    @Provides
    @Singleton
    @TvSettingsDataStore
    fun provideTvSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("tv_settings") }
    )

    @Provides
    @Singleton
    fun provideTvSettingsRepository(
        @TvSettingsDataStore dataStore: DataStore<Preferences>
    ): TvSettingsRepository = TvSettingsRepository(dataStore)
}
