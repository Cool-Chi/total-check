package com.example.totalcheck.ui.main

import androidx.compose.material.icons.Icons
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

@Immutable
data class TagDefinition(
    val name: String,
    val iconKey: String = TagCatalog.DEFAULT_ICON_KEY
)

@Immutable
data class TagIconOption(
    val key: String,
    val icon: ImageVector
)

object TagCatalog {
    const val MAX_TAGS_PER_ITEM = 2
    const val DEFAULT_TAG_NAME = "重要"
    const val DEFAULT_ICON_KEY = "star"

    val DEFAULT_SEED_TAGS: List<TagDefinition> = listOf(
        TagDefinition(name = DEFAULT_TAG_NAME, iconKey = DEFAULT_ICON_KEY)
    )

    val PRESET_ICONS: List<TagIconOption> = listOf(
        TagIconOption(key = "star", icon = Icons.Rounded.Star),
        TagIconOption(key = "bolt", icon = Icons.Rounded.Bolt),
        TagIconOption(key = "bookmark", icon = Icons.Rounded.Bookmark),
        TagIconOption(key = "flag", icon = Icons.Rounded.Flag),
        TagIconOption(key = "offer", icon = Icons.Rounded.LocalOffer),
        TagIconOption(key = "work", icon = Icons.Rounded.Work),
        TagIconOption(key = "home", icon = Icons.Rounded.Home),
        TagIconOption(key = "cart", icon = Icons.Rounded.ShoppingCart),
        TagIconOption(key = "medical", icon = Icons.Rounded.MedicalServices),
        TagIconOption(key = "build", icon = Icons.Rounded.Build),
        TagIconOption(key = "luggage", icon = Icons.Rounded.Luggage),
        TagIconOption(key = "flight", icon = Icons.Rounded.Flight)
    )

    fun resolveTagIcon(iconKey: String): ImageVector {
        return PRESET_ICONS.firstOrNull { it.key == iconKey }?.icon ?: Icons.Rounded.LocalOffer
    }

    fun sanitizeTagName(raw: String): String {
        return raw.replace(",", " ")
            .replace("\n", " ")
            .trim()
    }

    fun parseItemTags(rawTags: String): List<String> {
        if (rawTags.isBlank()) return emptyList()
        return rawTags.split(',')
            .map { sanitizeTagName(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS_PER_ITEM)
    }

    fun serializeItemTags(tags: List<String>): String {
        return tags.map { sanitizeTagName(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS_PER_ITEM)
            .joinToString(",")
    }

    fun resolveItemTagDefinitions(
        rawTags: String,
        pool: List<TagDefinition>
    ): List<TagDefinition> {
        val names = parseItemTags(rawTags)
        if (names.isEmpty()) return emptyList()
        val byName = pool.associateBy { it.name }
        return names.map { name ->
            byName[name] ?: TagDefinition(
                name = name,
                iconKey = if (name == DEFAULT_TAG_NAME) DEFAULT_ICON_KEY else "offer"
            )
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

    fun deserializeTagPool(rawJson: String?): List<TagDefinition> {
        if (rawJson.isNullOrBlank()) return DEFAULT_SEED_TAGS
        val parsed = runCatching {
            val arr = JSONArray(rawJson)
            val result = ArrayList<TagDefinition>(arr.length())
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val cleanName = sanitizeTagName(obj.optString("name"))
                if (cleanName.isEmpty() || !seen.add(cleanName)) continue
                val iconKey = obj.optString("iconKey").ifBlank { DEFAULT_ICON_KEY }
                result.add(TagDefinition(name = cleanName, iconKey = iconKey))
            }
            result
        }.getOrNull()
        return if (parsed.isNullOrEmpty()) DEFAULT_SEED_TAGS else parsed
    }
}
