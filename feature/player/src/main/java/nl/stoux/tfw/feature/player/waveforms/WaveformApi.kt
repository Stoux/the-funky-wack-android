package nl.stoux.tfw.feature.player.waveforms

import retrofit2.http.GET
import retrofit2.http.Url

interface WaveformApi {
    @GET
    suspend fun getWaveform(@Url url: String): WaveformResponse
}