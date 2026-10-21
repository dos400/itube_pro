package uz.donfinity.itubepro.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Some phone makers (Samsung, Xiaomi, Huawei, ...) kill background apps aggressively, even
 * apps with a foreground service. Exempting the app from battery optimisation makes
 * long screen-off playback much more reliable on those phones.
 *
 * See https://dontkillmyapp.com for vendor-specific extra steps.
 */
class BatteryOptimization @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun isIgnoringOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * The system "Allow app to always run in background?" dialog. Google Play limits this
     * to certain app types (hence the lint check we suppress); a sideloaded app may use it.
     * Needs the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission.
     */
    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    /** The full list in system settings, used to undo the exemption or as a fallback. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
