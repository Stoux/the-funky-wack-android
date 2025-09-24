package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.Serializable

/**
 * Mirrors the LivesetFilesByQuality type from the API (quality → URL)
 */
@Serializable
data class AudioQualityDto(
    val lq: String? = null,
    val hq: String? = null,
    val lossless: String? = null,
)

// Optional alias for clarity if needed elsewhere
typealias LivesetFilesByQualityDtoAlias = AudioQualityDto