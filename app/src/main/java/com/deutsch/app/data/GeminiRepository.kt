package com.deutsch.app.data

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
    private val client: OkHttpClient
) {
    // TODO: В будущем мы заменим этот блок на твою полную базу данных правил A1-B2
    private var grammarRulesA1B2 = "База правил загружается..."

    private val systemPrompt = """
        Ты — высококлассный, мгновенный анализатор грамматики немецкого языка (уровни A1-B2).
        Твоя задача — анализировать введенный пользователем текст в реальном времени.
        
        СТРУКТУРА ОТЧЕТА:
        1. ✅ **Соблюденные правила** (что сделано хорошо).
        2. ❌ **Ошибки и исправления** (с указанием конкретного правила).
        3. 💡 **Пояснения и нюансы** (тонкости, синонимы или советы).
        
        СПЕЦИАЛЬНОЕ УСЛОВИЕ:
        Пользователь вводит текст в реальном времени. Если он ввел только одну букву (например, "I" или "W"), не пиши об ошибке. Выдай полезные слова на эту букву для уровня A1-A2 или правила чтения этой буквы в немецком. Будь максимально полезен на каждом этапе ввода!
        
        Используй строгий и красивый Markdown.
        
        [БАЗА ЗНАНИЙ ГРАММАТИКИ ДЛЯ АНАЛИЗА (ВРЕМЕННАЯ)]
        $grammarRulesA1B2
    """.trimIndent()

    // Используем потоковый Endpoint для мгновенного появления текста
    fun analyzeTextStream(inputText: String, apiKey: String): Flow<String> = flow {
        if (inputText.isBlank()) {
            emit("Начните вводить текст на немецком...")
            return@flow
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:streamGenerateContent?alt=sse&key=$apiKey"

        val jsonBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) }))
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
                                emit(fullResponse) // Отправляем кусок текста в UI мгновенно
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing JSON chunk")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Network error")
            emit("⚠️ Ошибка сети. Проверьте подключение.")
        }
    }.flowOn(Dispatchers.IO)
    
    // Метод для будущей загрузки твоей базы
    fun updateGrammarDatabase(newRules: String) {
        this.grammarRulesA1B2 = newRules
    }
}