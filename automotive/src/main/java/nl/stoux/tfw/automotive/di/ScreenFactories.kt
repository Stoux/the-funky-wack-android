package nl.stoux.tfw.automotive.di

import androidx.car.app.CarContext
import dagger.assisted.AssistedFactory
import nl.stoux.tfw.automotive.screens.EditionLivesetListScreen
import nl.stoux.tfw.automotive.screens.MainBrowseScreen
import nl.stoux.tfw.automotive.screens.NowPlayingScreen
import nl.stoux.tfw.core.common.database.dao.EditionWithContent


@AssistedFactory
interface MainBrowseScreenFactory {
    fun create(carContext: CarContext): MainBrowseScreen
}

@AssistedFactory
interface EditionLivesetListScreenFactory {
    fun create(carContext: CarContext, edition: EditionWithContent): EditionLivesetListScreen
}

@AssistedFactory
interface NowPlayingScreenFactory {
    fun create(carContext: CarContext): NowPlayingScreen
}