package nl.stoux.tfw.core.common.device

import android.content.Context
import android.content.pm.PackageManager

/** Maps the current install to one of the backend's device_type values. */
fun detectDeviceType(context: Context): String {
    val pm = context.packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return "car"
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return "tv"
    return if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "mobile"
}
