package com.example.grama_wastetracker.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.Locale

object LocaleHelper {

    private const val PREFS = "AppPrefs"
    private const val KEY_LANG = "selected_language"

    fun getLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANG, "en") ?: "en"
    }

    fun setLocale(context: Context, language: String): Context {
        getPrefs(context).edit().putString(KEY_LANG, language).apply()
        return updateResources(context, language)
    }

    fun onAttach(context: Context): Context {
        val lang = getLanguage(context)
        return updateResources(context, lang)
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
