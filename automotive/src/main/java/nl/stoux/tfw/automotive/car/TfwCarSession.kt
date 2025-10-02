package nl.stoux.tfw.automotive.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import dagger.assisted.AssistedInject
import nl.stoux.tfw.automotive.di.MainBrowseScreenFactory


class TfwCarSession @AssistedInject constructor(
    private val mainScreenFactory: MainBrowseScreenFactory,
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return mainScreenFactory.create(carContext)
    }

}
