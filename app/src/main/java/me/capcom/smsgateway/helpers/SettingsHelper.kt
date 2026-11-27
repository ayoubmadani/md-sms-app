package me.capcom.smsgateway.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import me.capcom.smsgateway.receivers.BootReceiver
import java.util.Locale

class SettingsHelper(private val context: Context) {
    private val settings = PreferenceManager.getDefaultSharedPreferences(context)

    var autostart: Boolean
        get() = settings.getBoolean(PREF_KEY_AUTOSTART, true) // MD SMS: start on boot by default
        set(value) {
            // enable broadcast receiver
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            settings.edit { putBoolean(PREF_KEY_AUTOSTART, value) }
        }

    var language: String
        get() {
            return when (val stored = settings.getString(APP_LANGUAGE, "")) {
                null, "system" -> ""   // keep legacy "system" compatible
                else -> stored         // "" means "follow system"
            }
        }
        set(value) = settings.edit(true) {
            putString(APP_LANGUAGE, value)
        }

    companion object {
        private const val PREF_KEY_AUTOSTART = "autostart"

        private const val APP_LANGUAGE = "app.language"
    }
}