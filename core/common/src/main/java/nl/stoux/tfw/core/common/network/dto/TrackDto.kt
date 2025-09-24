package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LivesetTrackDto(
    val id: Long,
    val liveset_id: Long,
    val title: String,
    // Timestamp in seconds from start of liveset
    val timestamp: Int? = null,
    val order: Int,
)