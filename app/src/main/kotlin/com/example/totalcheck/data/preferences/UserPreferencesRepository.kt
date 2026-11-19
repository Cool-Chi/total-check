package com.example.totalcheck.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.totalcheck.data.widget.WidgetSyncManager
import com.example.totalcheck.ui.main.TagCatalog
import com.example.totalcheck.ui.main.TagDefinition
import com.example.totalcheck.ui.main.TagScope
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

    /** 全域標籤池（跨清單共用，預設內建實心五角星「重要」標籤） */
    val tagPool: Flow<List<TagDefinition>> = dataStore.data.map { prefs ->
        TagCatalog.deserializeTagPool(
            rawJson = prefs[KEY_TAG_POOL_JSON],
            scope = TagScope.GLOBAL,
            useDefaultSeedWhenMissing = true
        )
    }

    /** 清單標籤池（依 checklistId 隔離，僅在該清單內生效） */
    val checklistTagPools: Flow<Map<Long, List<TagDefinition>>> = dataStore.data.map { prefs ->
        TagCatalog.deserializeChecklistTagsMap(prefs[KEY_CHECKLIST_TAG_POOLS_JSON])
    }

    suspend fun addOrUpdateTag(tag: TagDefinition) {
        val cleanName = TagCatalog.sanitizeTagName(tag.name)
        if (cleanName.isEmpty()) return
        val normalized = tag.copy(
            name = cleanName,
            iconKey = tag.iconKey.ifBlank { TagCatalog.DEFAULT_ICON_KEY },
            scope = TagScope.GLOBAL
        )
        dataStore.edit { prefs ->
            val current = TagCatalog.deserializeTagPool(
                rawJson = prefs[KEY_TAG_POOL_JSON],
                scope = TagScope.GLOBAL,
                useDefaultSeedWhenMissing = true
            )
            val existingIdx = current.indexOfFirst { it.name == cleanName }
            val updated = if (existingIdx >= 0) {
                current.toMutableList().apply { set(existingIdx, normalized) }
            } else {
                current + normalized
            }
            prefs[KEY_TAG_POOL_JSON] = TagCatalog.serializeTagPool(updated)
        }
    }

    suspend fun removeTag(tagName: String): Pair<TagDefinition?, Int> {
        val cleanName = TagCatalog.sanitizeTagName(tagName)
        if (cleanName.isEmpty()) return null to -1
        var removedDef: TagDefinition? = null
        var removedIndex = -1
        dataStore.edit { prefs ->
            val current = TagCatalog.deserializeTagPool(
                rawJson = prefs[KEY_TAG_POOL_JSON],
                scope = TagScope.GLOBAL,
                useDefaultSeedWhenMissing = true
            )
            val idx = current.indexOfFirst { it.name == cleanName }
            if (idx >= 0) {
                removedDef = current[idx]
                removedIndex = idx
                val updated = current.toMutableList().apply { removeAt(idx) }
                prefs[KEY_TAG_POOL_JSON] = TagCatalog.serializeTagPool(updated)
            }
        }
        return removedDef to removedIndex
    }

    suspend fun insertTagAt(tag: TagDefinition, index: Int) {
        val cleanName = TagCatalog.sanitizeTagName(tag.name)
        if (cleanName.isEmpty()) return
        val normalized = tag.copy(
            name = cleanName,
            iconKey = tag.iconKey.ifBlank { TagCatalog.DEFAULT_ICON_KEY },
            scope = TagScope.GLOBAL
        )
        dataStore.edit { prefs ->
            val current = TagCatalog.deserializeTagPool(
                rawJson = prefs[KEY_TAG_POOL_JSON],
                scope = TagScope.GLOBAL,
                useDefaultSeedWhenMissing = true
            )
                .filterNot { it.name == cleanName }
                .toMutableList()
            val targetIdx = index.coerceIn(0, current.size)
            current.add(targetIdx, normalized)
            prefs[KEY_TAG_POOL_JSON] = TagCatalog.serializeTagPool(current)
        }
    }

    suspend fun addOrUpdateChecklistTag(checklistId: Long, tag: TagDefinition) {
        if (checklistId <= 0L) return
        val cleanName = TagCatalog.sanitizeTagName(tag.name)
        if (cleanName.isEmpty()) return
        val normalized = tag.copy(
            name = cleanName,
            iconKey = tag.iconKey.ifBlank { TagCatalog.DEFAULT_ICON_KEY },
            scope = TagScope.CHECKLIST
        )
        dataStore.edit { prefs ->
            val map = TagCatalog.deserializeChecklistTagsMap(prefs[KEY_CHECKLIST_TAG_POOLS_JSON])
                .toMutableMap()
            val current = map[checklistId].orEmpty()
            val existingIdx = current.indexOfFirst { it.name == cleanName }
            val updated = if (existingIdx >= 0) {
                current.toMutableList().apply { set(existingIdx, normalized) }
            } else {
                current + normalized
            }
            map[checklistId] = updated
            prefs[KEY_CHECKLIST_TAG_POOLS_JSON] = TagCatalog.serializeChecklistTagsMap(map)
        }
    }

    suspend fun removeChecklistTag(checklistId: Long, tagName: String): Pair<TagDefinition?, Int> {
        if (checklistId <= 0L) return null to -1
        val cleanName = TagCatalog.sanitizeTagName(tagName)
        if (cleanName.isEmpty()) return null to -1
        var removedDef: TagDefinition? = null
        var removedIndex = -1
        dataStore.edit { prefs ->
            val map = TagCatalog.deserializeChecklistTagsMap(prefs[KEY_CHECKLIST_TAG_POOLS_JSON])
                .toMutableMap()
            val current = map[checklistId].orEmpty()
            val idx = current.indexOfFirst { it.name == cleanName }
            if (idx >= 0) {
                removedDef = current[idx]
                removedIndex = idx
                val updated = current.toMutableList().apply { removeAt(idx) }
                if (updated.isEmpty()) {
                    map.remove(checklistId)
                } else {
                    map[checklistId] = updated
                }
                prefs[KEY_CHECKLIST_TAG_POOLS_JSON] = TagCatalog.serializeChecklistTagsMap(map)
            }
        }
        return removedDef to removedIndex
    }

    suspend fun insertChecklistTagAt(checklistId: Long, tag: TagDefinition, index: Int) {
        if (checklistId <= 0L) return
        val cleanName = TagCatalog.sanitizeTagName(tag.name)
        if (cleanName.isEmpty()) return
        val normalized = tag.copy(
            name = cleanName,
            iconKey = tag.iconKey.ifBlank { TagCatalog.DEFAULT_ICON_KEY },
            scope = TagScope.CHECKLIST
        )
        dataStore.edit { prefs ->
            val map = TagCatalog.deserializeChecklistTagsMap(prefs[KEY_CHECKLIST_TAG_POOLS_JSON])
                .toMutableMap()
            val current = map[checklistId].orEmpty()
                .filterNot { it.name == cleanName }
                .toMutableList()
            val targetIdx = index.coerceIn(0, current.size)
            current.add(targetIdx, normalized)
            map[checklistId] = current
            prefs[KEY_CHECKLIST_TAG_POOLS_JSON] = TagCatalog.serializeChecklistTagsMap(map)
        }
    }

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
        val KEY_TAG_POOL_JSON = stringPreferencesKey("tag_pool_json")
        val KEY_CHECKLIST_TAG_POOLS_JSON = stringPreferencesKey("checklist_tag_pools_json")

        fun keySelectedChecklist(appWidgetId: Int) =
            longPreferencesKey("widget_selected_checklist_$appWidgetId")

        fun keyShowUnchecked(appWidgetId: Int) =
            booleanPreferencesKey("widget_show_unchecked_$appWidgetId")

        fun keyResetConfirmUntil(appWidgetId: Int) =
            longPreferencesKey("widget_reset_confirm_until_$appWidgetId")
    }
}
