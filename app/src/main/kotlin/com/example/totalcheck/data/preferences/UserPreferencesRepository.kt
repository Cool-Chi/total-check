package com.example.totalcheck.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

data class WidgetPreferences(
    val selectedChecklistId: Long? = null,
    val showUncheckedOnly: Boolean = false,
    val resetConfirmUntilMillis: Long = 0L
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

    val widgetPreferences: Flow<WidgetPreferences> = dataStore.data.map { prefs ->
        val rawChecklistId = prefs[KEY_WIDGET_SELECTED_CHECKLIST_ID]
        WidgetPreferences(
            selectedChecklistId = rawChecklistId?.takeIf { it > 0L },
            showUncheckedOnly = prefs[KEY_WIDGET_SHOW_UNCHECKED_ONLY] ?: false,
            resetConfirmUntilMillis = prefs[KEY_WIDGET_RESET_CONFIRM_UNTIL_MILLIS] ?: 0L
        )
    }

    val widgetSelectedChecklistId: Flow<Long?> = widgetPreferences.map { it.selectedChecklistId }

    val widgetShowUncheckedOnly: Flow<Boolean> = widgetPreferences.map { it.showUncheckedOnly }

    val widgetResetConfirmUntilMillis: Flow<Long> = widgetPreferences.map { it.resetConfirmUntilMillis }

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

    suspend fun setWidgetSelectedChecklistId(checklistId: Long?) {
        dataStore.edit { prefs ->
            if (checklistId != null && checklistId > 0L) {
                prefs[KEY_WIDGET_SELECTED_CHECKLIST_ID] = checklistId
            } else {
                prefs.remove(KEY_WIDGET_SELECTED_CHECKLIST_ID)
            }
            prefs[KEY_WIDGET_RESET_CONFIRM_UNTIL_MILLIS] = 0L
        }
    }

    suspend fun setWidgetShowUncheckedOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WIDGET_SHOW_UNCHECKED_ONLY] = enabled
        }
    }

    suspend fun toggleWidgetShowUncheckedOnly(): Boolean {
        var nextState = false
        dataStore.edit { prefs ->
            val current = prefs[KEY_WIDGET_SHOW_UNCHECKED_ONLY] ?: false
            nextState = !current
            prefs[KEY_WIDGET_SHOW_UNCHECKED_ONLY] = nextState
        }
        return nextState
    }

    suspend fun setWidgetResetConfirmUntilMillis(untilMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_WIDGET_RESET_CONFIRM_UNTIL_MILLIS] = untilMillis
        }
    }

    private companion object {
        val KEY_IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val KEY_WIDGET_SELECTED_CHECKLIST_ID = longPreferencesKey("widget_selected_checklist_id")
        val KEY_WIDGET_SHOW_UNCHECKED_ONLY = booleanPreferencesKey("widget_show_unchecked_only")
        val KEY_WIDGET_RESET_CONFIRM_UNTIL_MILLIS = longPreferencesKey("widget_reset_confirm_until_millis")
    }
}
