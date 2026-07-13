package com.deutsch.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Инициализация DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val API_KEY = stringPreferencesKey("gemini_api_key")

    // Читаем ключ как Flow (реактивно)
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    // Сохраняем ключ
    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }
}