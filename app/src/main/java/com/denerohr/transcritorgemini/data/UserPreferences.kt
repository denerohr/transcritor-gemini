package com.denerohr.transcritorgemini.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val PROMPT_SUMMARY = stringPreferencesKey("prompt_summary")
        val PROMPT_REVIEW = stringPreferencesKey("prompt_review")

        const val DEFAULT_SUMMARY_PROMPT = "Resuma o seguinte texto transcrito em tópicos principais, destacando as informações mais importantes:"
        const val DEFAULT_REVIEW_PROMPT = "Revise o seguinte texto transcrito, corrigindo erros gramaticais e de pontuação, mas mantendo o tom original da fala:"
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    val promptSummary: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PROMPT_SUMMARY] ?: DEFAULT_SUMMARY_PROMPT
    }

    val promptReview: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PROMPT_REVIEW] ?: DEFAULT_REVIEW_PROMPT
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun savePrompts(summary: String, review: String) {
        context.dataStore.edit { preferences ->
            preferences[PROMPT_SUMMARY] = summary
            preferences[PROMPT_REVIEW] = review
        }
    }
}
