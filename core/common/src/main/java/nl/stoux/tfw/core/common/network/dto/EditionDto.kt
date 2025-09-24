package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EditionDto(
    val id: Long,
    val number: String,
    val tag_line: String? = null,
    val date: String? = null,
    val notes: String? = null,
    val livesets: List<LivesetDto>? = null,
    val empty_note: String? = null,
    val timetabler_mode: Boolean,
    val poster_url: String? = null,
    val poster_srcset_urls: List<SrcsetDto>? = null,
)

@Serializable
data class SrcsetDto(
    val url: String,
    val width: Int,
)