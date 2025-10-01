package nl.stoux.tfw.feature.player.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.stoux.tfw.feature.player.waveforms.WaveformApi
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WaveformModule {

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): WaveformApi = retrofit.create(WaveformApi::class.java)

}
