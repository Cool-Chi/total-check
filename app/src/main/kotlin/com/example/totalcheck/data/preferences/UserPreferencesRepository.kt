package com.example.totalcheck.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

data class ThemeConfig(
    val isDarkMode: Boolean,
    val useDynamicColor: Boolean
)

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.userPreferencesDataStore

    val themeConfig: Flow<ThemeConfig> = dataStore.data.map { prefs ->
        ThemeConfig(
            isDarkMode = prefs[KEY_IS_DARK_MODE] ?: true,
            useDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: false
        )
    }

    val isDarkMode: Flow<Boolean> = themeConfig.map { it.isDarkMode }

    val useDynamicColor: Flow<Boolean> = themeConfig.map { it.useDynamicColor }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_DARK_MODE] = enabled
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_DYNAMIC_COLOR] = enabled
        }
    }

    private companion object {
        val KEY_IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }
}
