package com.leejang.sleeptandard.Prefs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomSituationItem(
    val id: String,
    val label: String
)

class CustomSituationPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("custom_situation_prefs", Context.MODE_PRIVATE)

    private val KEY_ITEMS_JSON = "items_json"

    fun load(): List<CustomSituationItem> {
        val raw = prefs.getString(KEY_ITEMS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CustomSituationItem(
                            id = obj.getString("id"),
                            label = obj.getString("label")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(items: List<CustomSituationItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS_JSON, arr.toString()).apply()
    }

    fun add(label: String): CustomSituationItem {
        val newItem = CustomSituationItem(
            id = "custom_${System.currentTimeMillis()}",
            label = label
        )
        val updated = load() + newItem
        save(updated)
        return newItem
    }

    fun clear() {
        prefs.edit().remove(KEY_ITEMS_JSON).apply()
    }
}