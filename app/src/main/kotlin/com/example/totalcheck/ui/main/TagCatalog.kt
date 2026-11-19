package com.example.totalcheck.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt as OutlinedBolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Build as OutlinedBuild
import androidx.compose.material.icons.outlined.Flight as OutlinedFlight
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.LocalOffer as OutlinedLocalOffer
import androidx.compose.material.icons.outlined.Luggage as OutlinedLuggage
import androidx.compose.material.icons.outlined.MedicalServices as OutlinedMedicalServices
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material.icons.outlined.ShoppingCart as OutlinedShoppingCart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

enum class TagScope {
    GLOBAL,
    CHECKLIST
}

@Immutable
data class TagDefinition(
    val name: String,
    val iconKey: String = TagCatalog.DEFAULT_ICON_KEY,
    val scope: TagScope = TagScope.GLOBAL
) {
    val storageToken: String
        get() = TagCatalog.encodeTagToken(name = name, scope = scope)
}

@Immutable
data class TagIconOption(
    val key: String,
    val solidIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    fun iconFor(scope: TagScope): ImageVector {
        return if (scope == TagScope.CHECKLIST) outlinedIcon else solidIcon
    }
}

object TagCatalog {
    const val MAX_TAGS_PER_ITEM = 2
    const val DEFAULT_TAG_NAME = "重要"
    const val DEFAULT_ICON_KEY = "star"
    const val CHECKLIST_TAG_PREFIX = "#list:"

    val DEFAULT_SEED_TAGS: List<TagDefinition> = listOf(
        TagDefinition(
            name = DEFAULT_TAG_NAME,
            iconKey = DEFAULT_ICON_KEY,
            scope = TagScope.GLOBAL
        )
    )

    val PRESET_ICONS: List<TagIconOption> = listOf(
        TagIconOption(
            key = "star",
            solidIcon = Icons.Rounded.Star,
            outlinedIcon = Icons.Outlined.StarOutline
        ),
        TagIconOption(
            key = "bolt",
            solidIcon = Icons.Rounded.Bolt,
            outlinedIcon = Icons.Outlined.OutlinedBolt
        ),
        TagIconOption(
            key = "bookmark",
            solidIcon = Icons.Rounded.Bookmark,
            outlinedIcon = Icons.Outlined.BookmarkBorder
        ),
        TagIconOption(
            key = "flag",
            solidIcon = Icons.Rounded.Flag,
            outlinedIcon = Icons.Outlined.OutlinedFlag
        ),
        TagIconOption(
            key = "offer",
            solidIcon = Icons.Rounded.LocalOffer,
            outlinedIcon = Icons.Outlined.OutlinedLocalOffer
        ),
        TagIconOption(
            key = "work",
            solidIcon = Icons.Rounded.Work,
            outlinedIcon = Icons.Outlined.WorkOutline
        ),
        TagIconOption(
            key = "home",
            solidIcon = Icons.Rounded.Home,
            outlinedIcon = Icons.Outlined.OutlinedHome
        ),
        TagIconOption(
            key = "cart",
            solidIcon = Icons.Rounded.ShoppingCart,
            outlinedIcon = Icons.Outlined.OutlinedShoppingCart
        ),
        TagIconOption(
            key = "medical",
            solidIcon = Icons.Rounded.MedicalServices,
            outlinedIcon = Icons.Outlined.OutlinedMedicalServices
        ),
        TagIconOption(
            key = "build",
            solidIcon = Icons.Rounded.Build,
            outlinedIcon = Icons.Outlined.OutlinedBuild
        ),
        TagIconOption(
            key = "luggage",
            solidIcon = Icons.Rounded.Luggage,
            outlinedIcon = Icons.Outlined.OutlinedLuggage
        ),
        TagIconOption(
            key = "flight",
            solidIcon = Icons.Rounded.Flight,
            outlinedIcon = Icons.Outlined.OutlinedFlight
        )
    )

    fun resolveTagIcon(
        iconKey: String,
        scope: TagScope = TagScope.GLOBAL
    ): ImageVector {
        val option = PRESET_ICONS.firstOrNull { it.key == iconKey }
        return if (option != null) {
            option.iconFor(scope)
        } else if (scope == TagScope.CHECKLIST) {
            Icons.Outlined.OutlinedLocalOffer
        } else {
            Icons.Rounded.LocalOffer
        }
    }

    fun sanitizeTagName(raw: String): String {
        return raw.replace(",", " ")
            .replace("\n", " ")
            .removePrefix(CHECKLIST_TAG_PREFIX)
            .trim()
    }

    fun isChecklistTagToken(token: String): Boolean {
        return token.trim().startsWith(CHECKLIST_TAG_PREFIX)
    }

    fun scopeFromToken(token: String): TagScope {
        return if (isChecklistTagToken(token)) TagScope.CHECKLIST else TagScope.GLOBAL
    }

    fun displayNameFromToken(token: String): String {
        return sanitizeTagName(token)
    }

    fun encodeTagToken(name: String, scope: TagScope): String {
        val cleanName = sanitizeTagName(name)
        if (cleanName.isEmpty()) return ""
        return if (scope == TagScope.CHECKLIST) {
            "$CHECKLIST_TAG_PREFIX$cleanName"
        } else {
            cleanName
        }
    }

    fun sanitizeTagToken(rawToken: String): String {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return ""
        val scope = scopeFromToken(trimmed)
        val cleanName = sanitizeTagName(trimmed)
        return encodeTagToken(cleanName, scope)
    }

    fun parseItemTags(rawTags: String): List<String> {
        if (rawTags.isBlank()) return emptyList()
        return rawTags.split(',')
            .map { sanitizeTagToken(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS_PER_ITEM)
    }

    fun serializeItemTags(tokens: List<String>): String {
        return tokens.map { sanitizeTagToken(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS_PER_ITEM)
            .joinToString(",")
    }

    fun resolveSingleTagDefinition(
        rawToken: String,
        availablePool: List<TagDefinition>
    ): TagDefinition {
        val token = sanitizeTagToken(rawToken)
        val existing = availablePool.firstOrNull { it.storageToken == token }
        if (existing != null) return existing
        val scope = scopeFromToken(token)
        val cleanName = displayNameFromToken(token)
        return TagDefinition(
            name = cleanName,
            iconKey = if (scope == TagScope.GLOBAL && cleanName == DEFAULT_TAG_NAME) {
                DEFAULT_ICON_KEY
            } else {
                "offer"
            },
            scope = scope
        )
    }

    fun resolveItemTagDefinitions(
        rawTags: String,
        availablePool: List<TagDefinition>
    ): List<TagDefinition> {
        val tokens = parseItemTags(rawTags)
        if (tokens.isEmpty()) return emptyList()
        val byToken = availablePool.associateBy { it.storageToken }
        return tokens.map { token ->
            byToken[token] ?: resolveSingleTagDefinition(token, emptyList())
        }
    }

    fun serializeTagPool(pool: List<TagDefinition>): String {
        val arr = JSONArray()
        val seen = HashSet<String>()
        for (tag in pool) {
            val cleanName = sanitizeTagName(tag.name)
            if (cleanName.isEmpty() || !seen.add(cleanName)) continue
            val obj = JSONObject()
            obj.put("name", cleanName)
            obj.put("iconKey", tag.iconKey.ifBlank { DEFAULT_ICON_KEY })
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeTagPool(
        rawJson: String?,
        scope: TagScope = TagScope.GLOBAL,
        useDefaultSeedWhenMissing: Boolean = (scope == TagScope.GLOBAL)
    ): List<TagDefinition> {
        val fallback = if (useDefaultSeedWhenMissing) DEFAULT_SEED_TAGS else emptyList()
        if (rawJson == null) return fallback
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return fallback
        val parsed = runCatching {
            val arr = JSONArray(trimmed)
            val result = ArrayList<TagDefinition>(arr.length())
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val cleanName = sanitizeTagName(obj.optString("name"))
                if (cleanName.isEmpty() || !seen.add(cleanName)) continue
                val iconKey = obj.optString("iconKey").ifBlank { DEFAULT_ICON_KEY }
                result.add(
                    TagDefinition(
                        name = cleanName,
                        iconKey = iconKey,
                        scope = scope
                    )
                )
            }
            result
        }.getOrNull()
        return parsed ?: fallback
    }

    fun serializeChecklistTagsMap(poolsByChecklist: Map<Long, List<TagDefinition>>): String {
        val root = JSONObject()
        for ((checklistId, tags) in poolsByChecklist) {
            if (checklistId <= 0L || tags.isEmpty()) continue
            val arr = JSONArray()
            val seen = HashSet<String>()
            for (tag in tags) {
                val cleanName = sanitizeTagName(tag.name)
                if (cleanName.isEmpty() || !seen.add(cleanName)) continue
                val obj = JSONObject()
                obj.put("name", cleanName)
                obj.put("iconKey", tag.iconKey.ifBlank { DEFAULT_ICON_KEY })
                arr.put(obj)
            }
            if (arr.length() > 0) {
                root.put(checklistId.toString(), arr)
            }
        }
        return root.toString()
    }

    fun deserializeChecklistTagsMap(rawJson: String?): Map<Long, List<TagDefinition>> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(rawJson.trim())
            val result = LinkedHashMap<Long, List<TagDefinition>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val checklistId = key.toLongOrNull() ?: continue
                if (checklistId <= 0L) continue
                val arr = root.optJSONArray(key) ?: continue
                val list = ArrayList<TagDefinition>(arr.length())
                val seen = HashSet<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val cleanName = sanitizeTagName(obj.optString("name"))
                    if (cleanName.isEmpty() || !seen.add(cleanName)) continue
                    val iconKey = obj.optString("iconKey").ifBlank { DEFAULT_ICON_KEY }
                    list.add(
                        TagDefinition(
                            name = cleanName,
                            iconKey = iconKey,
                            scope = TagScope.CHECKLIST
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    result[checklistId] = list
                }
            }
            result
        }.getOrDefault(emptyMap())
    }
}
