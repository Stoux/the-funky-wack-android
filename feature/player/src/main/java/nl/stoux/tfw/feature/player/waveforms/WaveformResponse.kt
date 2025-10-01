package nl.stoux.tfw.feature.player.waveforms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WaveformResponse(
    val version: Int,
    val channels: Int,
    @SerialName("sample_rate") val sampleRate: Int,
    @SerialName("samples_per_pixel") val samplesPerPixel: Int,
    val bits: Int,
    val length: Int,
    val data: List<Int>,
)