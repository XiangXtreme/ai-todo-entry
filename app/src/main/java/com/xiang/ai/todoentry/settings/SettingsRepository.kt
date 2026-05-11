package com.xiang.ai.todoentry.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("app_settings")

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val settings: Flow<AppSettings> = appContext.settingsDataStore.data.map { preferences ->
        AppSettings(
            llmBaseUrl = preferences[Keys.LLM_BASE_URL] ?: "https://api.deepseek.com",
            llmModel = preferences[Keys.LLM_MODEL] ?: "deepseek-v4-flash",
            defaultListId = preferences[Keys.DEFAULT_LIST_ID]
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        appContext.settingsDataStore.edit { preferences ->
            preferences[Keys.LLM_BASE_URL] = settings.llmBaseUrl.trim().trimEnd('/')
            preferences[Keys.LLM_MODEL] = settings.llmModel.trim()
            val defaultListId = settings.defaultListId
            if (defaultListId.isNullOrBlank()) {
                preferences.remove(Keys.DEFAULT_LIST_ID)
            } else {
                preferences[Keys.DEFAULT_LIST_ID] = defaultListId
            }
        }
    }

    fun getApiKey(): String? = securePrefs.getString(API_KEY, null)?.takeIf { it.isNotBlank() }

    fun saveApiKey(apiKey: String) {
        securePrefs.edit().putString(API_KEY, apiKey.trim()).apply()
    }

    fun clearApiKey() {
        securePrefs.edit().remove(API_KEY).apply()
    }

    private object Keys {
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val DEFAULT_LIST_ID = stringPreferencesKey("default_list_id")
    }

    private companion object {
        const val API_KEY = "llm_api_key"
    }
}
