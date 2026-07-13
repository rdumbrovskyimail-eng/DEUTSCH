package com.deutsch.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context // Контекст для доступа к assets
) {
    // Лениво читаем базу грамматики один раз при первом обращении
    private val grammarRulesA1B2: String by lazy {
        try {
            context.assets.open("grammar_rules.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения базы грамматики")
            "База грамматики недоступна."
        }
    }

    fun analyzeTextStream(inputText: String, apiKey: String): Flow<String> = flow {
        if (inputText.isBlank()) {
            emit("Начните вводить текст на немецком...")
            return@flow
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:streamGenerateContent?alt=sse&key=$apiKey"

        val jsonBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", grammarRulesA1B2) }))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply { put("text", inputText) }))
            }))
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("⚠️ Ошибка API: ${response.code} ${response.message}")
                    return@use
                }

                val source = response.body?.source() ?: return@use
                var fullResponse = ""

                // Читаем Server-Sent Events (SSE)
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        try {
                            val chunkJson = JSONObject(data)
                            val candidates = chunkJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val parts = candidates.getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                val textChunk = parts.getJSONObject(0).getString("text")
                                fullResponse += textChunk
                                emit(fullResponse) // Мгновенно отправляем кусок текста в UI
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Ошибка парсинга JSON")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Ошибка сети")
            emit("⚠️ Ошибка сети. Проверьте подключение.")
        }
    }.flowOn(Dispatchers.IO)
}