package com.autocaller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "autocaller_settings"
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_PHONE_NUMBER = stringPreferencesKey("phone_number")
        private val KEY_INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        private val KEY_IS_RUNNING = booleanPreferencesKey("is_running")
        const val DEFAULT_INTERVAL = 5
    }

    val phoneNumberFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_PHONE_NUMBER] ?: "" }

    val intervalMinutesFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_INTERVAL_MINUTES] ?: DEFAULT_INTERVAL }

    val isRunningFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_IS_RUNNING] ?: false }

    suspend fun savePhoneNumber(number: String) {
        context.dataStore.edit { prefs -> prefs[KEY_PHONE_NUMBER] = number }
    }

    suspend fun saveIntervalMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_INTERVAL_MINUTES] = minutes }
    }

    suspend fun saveIsRunning(running: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_IS_RUNNING] = running }
    }
}
