package com.example.inventorymaster.utils

import android.content.Context
import com.example.inventorymaster.data.model.MappingTemplate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MappingTemplateManager {
    private const val PREFS_NAME = "excel_mapping_templates"
    private const val KEY_TEMPLATE_LIST = "template_id_list"
    private val gson = Gson()

    fun saveTemplate(context: Context, template: MappingTemplate) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(template)
        prefs.edit().putString("template_${template.id}", json).apply()

        val ids = getAllTemplateIds(prefs).toMutableList()
        if (!ids.contains(template.id)) {
            ids.add(template.id)
            prefs.edit().putString(KEY_TEMPLATE_LIST, gson.toJson(ids)).apply()
        }
    }

    fun getAllTemplates(context: Context): List<MappingTemplate> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = getAllTemplateIds(prefs)
        return ids.mapNotNull { id ->
            val json = prefs.getString("template_$id", null) ?: return@mapNotNull null
            try {
                gson.fromJson(json, MappingTemplate::class.java)
            } catch (_: Exception) { null }
        }
    }

    fun deleteTemplate(context: Context, templateId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("template_$templateId").apply()

        val ids = getAllTemplateIds(prefs).toMutableList()
        ids.remove(templateId)
        prefs.edit().putString(KEY_TEMPLATE_LIST, gson.toJson(ids)).apply()
    }

    fun getTemplateCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getAllTemplateIds(prefs).size
    }

    private fun getAllTemplateIds(prefs: android.content.SharedPreferences): List<String> {
        val json = prefs.getString(KEY_TEMPLATE_LIST, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
