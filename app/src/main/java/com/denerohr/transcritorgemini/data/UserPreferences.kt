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
        val TRANSCRIPTION_MODE = stringPreferencesKey("transcription_mode")

        const val DEFAULT_SUMMARY_PROMPT = "Resuma o seguinte texto transcrito em tópicos principais, destacando as informações mais importantes:"
        const val DEFAULT_REVIEW_PROMPT = "Revise o seguinte texto transcrito, corrigindo erros gramaticais e de pontuação, mas mantendo o tom original da fala:"
        const val DEFAULT_TRANSCRIPTION_MODE = "SMART"
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { it[API_KEY] }
    val promptSummary: Flow<String> = context.dataStore.data.map { it[PROMPT_SUMMARY] ?: DEFAULT_SUMMARY_PROMPT }
    val promptReview: Flow<String> = context.dataStore.data.map { it[PROMPT_REVIEW] ?: DEFAULT_REVIEW_PROMPT }
    val transcriptionMode: Flow<String> = context.dataStore.data.map { it[TRANSCRIPTION_MODE] ?: DEFAULT_TRANSCRIPTION_MODE }

    suspend fun saveSettings(key: String, summary: String, review: String, mode: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
            preferences[PROMPT_SUMMARY] = summary
            preferences[PROMPT_REVIEW] = review
            preferences[TRANSCRIPTION_MODE] = mode
        }
    }
}
