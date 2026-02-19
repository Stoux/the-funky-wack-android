package nl.stoux.tfw.tv.navigation

import kotlinx.serialization.Serializable

sealed interface TvDestination {
    @Serializable
    data object Browse : TvDestination

    @Serializable
    data class EditionDetail(val editionId: Long) : TvDestination

    @Serializable
    data object NowPlaying : TvDestination
}
