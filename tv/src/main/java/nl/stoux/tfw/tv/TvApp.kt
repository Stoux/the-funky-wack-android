package nl.stoux.tfw.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.repository.EditionRepository
import javax.inject.Inject

@HiltAndroidApp
class TvApp : Application() {

    @Inject
    lateinit var editionRepository: EditionRepository

    override fun onCreate() {
        super.onCreate()

        // Refresh the list of editions on launch
        CoroutineScope(Dispatchers.IO).launch {
            editionRepository.refreshEditions()
        }
    }
}
