package com.example.privatebrowser

import android.content.Context

/** Extension packages/catalogue are shared; enabled state is isolated per profile. */
internal class ExtensionPreferences(context: Context, profileId: String) {
    private val preferences = context.getSharedPreferences(
        "extensions_${BrowserActivity.profileSuffix(profileId)}", Context.MODE_PRIVATE,
    )

    fun isEnabled(extensionId: String, defaultValue: Boolean = false): Boolean =
        preferences.getBoolean(extensionId, defaultValue)

    fun setEnabled(extensionId: String, enabled: Boolean) {
        preferences.edit().putBoolean(extensionId, enabled).apply()
    }
}
