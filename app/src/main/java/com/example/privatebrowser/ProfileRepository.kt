package com.example.privatebrowser

import android.content.Context
import java.util.UUID

data class BrowserProfile(val id: String, val name: String)

class ProfileRepository(context: Context) {
    private val preferences = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun profiles(): List<BrowserProfile> = preferences.getStringSet(KEY_PROFILES, emptySet())
        .orEmpty()
        .mapNotNull { encoded ->
            val parts = encoded.split(SEPARATOR, limit = 2)
            parts.takeIf { it.size == 2 }?.let { BrowserProfile(it[0], it[1]) }
        }
        .sortedBy { it.name.lowercase() }

    fun create(name: String): BrowserProfile {
        val profile = BrowserProfile(UUID.randomUUID().toString(), name.trim())
        val updated = profiles().mapTo(mutableSetOf()) { "${it.id}$SEPARATOR${it.name}" }
        updated += "${profile.id}$SEPARATOR${profile.name}"
        preferences.edit().putStringSet(KEY_PROFILES, updated).apply()
        return profile
    }

    fun lastUsedProfileId(): String? = preferences.getString(KEY_LAST_USED, null)

    fun markUsed(profile: BrowserProfile) {
        preferences.edit().putString(KEY_LAST_USED, profile.id).apply()
    }

    companion object {
        private const val KEY_PROFILES = "profile_list"
        private const val KEY_LAST_USED = "last_used_profile"
        private const val SEPARATOR = "\u001F"
    }
}
