package com.example.totalcheck.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.totalcheck.data.widget.WidgetSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    private val appContext = context.applicationContext
    private val dataStore = appContext.userPreferencesDataStore

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
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_DYNAMIC_COLOR] = enabled
        }
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun readAllWidgetStates(appWidgetIds: IntArray): Map<Int, WidgetPreferences> {
        if (appWidgetIds.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return appWidgetIds.associateWith { id ->
            WidgetPreferences(
                selectedChecklistId = prefs[keySelectedChecklist(id)]?.takeIf { it > 0L },
                showUncheckedOnly = prefs[keyShowUnchecked(id)] ?: false,
                resetConfirmUntilMillis = prefs[keyResetConfirmUntil(id)] ?: 0L,
            )
        }
    }

    suspend fun setWidgetSelectedChecklistId(appWidgetId: Int, checklistId: Long?) {
        dataStore.edit { prefs ->
            val selectedKey = keySelectedChecklist(appWidgetId)
            if (checklistId != null && checklistId > 0L) {
                prefs[selectedKey] = checklistId
            } else {
                prefs.remove(selectedKey)
            }
            prefs[keyResetConfirmUntil(appWidgetId)] = 0L
        }
    }

    suspend fun setWidgetShowUncheckedOnly(appWidgetId: Int, enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[keyShowUnchecked(appWidgetId)] = enabled
        }
    }

    suspend fun setWidgetResetConfirmUntilMillis(appWidgetId: Int, untilMillis: Long) {
        dataStore.edit { prefs ->
            prefs[keyResetConfirmUntil(appWidgetId)] = untilMillis
        }
    }

    suspend fun clearWidgetPreferences(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        dataStore.edit { prefs ->
            for (id in appWidgetIds) {
                prefs.remove(keySelectedChecklist(id))
                prefs.remove(keyShowUnchecked(id))
                prefs.remove(keyResetConfirmUntil(id))
            }
        }
    }

    private companion object {
        val KEY_IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")

        fun keySelectedChecklist(appWidgetId: Int) =
            longPreferencesKey("widget_selected_checklist_$appWidgetId")

        fun keyShowUnchecked(appWidgetId: Int) =
            booleanPreferencesKey("widget_show_unchecked_$appWidgetId")

        fun keyResetConfirmUntil(appWidgetId: Int) =
            longPreferencesKey("widget_reset_confirm_until_$appWidgetId")
    }
}
