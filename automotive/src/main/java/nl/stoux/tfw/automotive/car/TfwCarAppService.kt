package nl.stoux.tfw.automotive.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
import nl.stoux.tfw.automotive.di.TfwCarSessionFactory
import javax.inject.Inject


/**
 * Entry point for the Android for Cars templated app.
 * This service presents templated screens while delegating playback to the shared MediaPlaybackService.
 */
@AndroidEntryPoint
class TfwCarAppService : CarAppService() {

    @Inject lateinit var carSessionFactory: TfwCarSessionFactory

    override fun createHostValidator(): HostValidator {
        // Allow all hosts during development. Restrict for production.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return carSessionFactory.create()
    }

}
