package com.example.achtungdiekurve.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore instance for the whole app
val Context.settingsDataStore by preferencesDataStore(name = "settings")

private object SettingsKeys {
    val NICKNAME = stringPreferencesKey("nickname")
    val CONTROL_MODE = stringPreferencesKey("control_mode")
}

class SettingsRepository(private val context: Context) {

    val nicknameFlow: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[SettingsKeys.NICKNAME] ?: "" }

    val controlModeFlow: Flow<ControlMode> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.CONTROL_MODE]?.let { ControlMode.valueOf(it) } ?: ControlMode.TAP
    }

    suspend fun saveNickname(nickname: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.NICKNAME] = nickname
        }
    }

    suspend fun saveControlMode(mode: ControlMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.CONTROL_MODE] = mode.name
        }
    }
}
