package nl.stoux.tfw.automotive

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AutomotiveApp : Application()
// Note: refreshEditions() is called by MediaPlaybackService.onCreate() via libraryManager.init()
