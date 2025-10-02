package nl.stoux.tfw.automotive

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.stoux.tfw.automotive.car.ControllerHolder
import nl.stoux.tfw.core.common.repository.EditionRepository
import javax.inject.Inject

@HiltAndroidApp
class AutomotiveApp : Application() {

    @Inject lateinit var controllerHolder: ControllerHolder

    @Inject lateinit var editionRepository: EditionRepository


    override fun onCreate() {
        super.onCreate()

        // Start a Media controller session
        controllerHolder.init(this.applicationContext)

        // Refresh the list of editions
        CoroutineScope(Dispatchers.IO).launch {
            editionRepository.refreshEditions()
        }
    }

}
