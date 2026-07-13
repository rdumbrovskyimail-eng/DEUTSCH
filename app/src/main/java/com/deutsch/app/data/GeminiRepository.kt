package com.deutsch.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// --- DTO для безопасного парсинга JSON ---
@Serializable
data class GeminiRequest(val systemInstruction: SystemInstruction, val contents: List<Content>)
@Serializable
data class SystemInstruction(val parts: List<Part>)
@Serializable
data class Content(val role: String, val parts: List<Part>)
@Serializable
data class Part(val text: String)
@Serializable
data class GeminiResponse(val candidates: List<Candidate>? = null)
@Serializable
data class Candidate(val content: Content)

@Singleton
class GeminiRepository @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val grammarRulesA1B2: String by lazy {
        try {
            context.assets.open("grammar_rules.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения базы грамматики")
            "База грамматики недоступна."
        }
    }

    fun analyzeTextStream(inputText: String, apiKey: String): Flow<String> = callbackFlow {
        if (inputText.isBlank()) {
            trySend("Начните вводить текст на немецком...")
            close()
            return@callbackFlow
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:streamGenerateContent?alt=sse&key=$apiKey"

        val requestBody = GeminiRequest(
            systemInstruction = SystemInstruction(listOf(Part(grammarRulesA1B2))),
            contents = listOf(Content("user", listOf(Part(inputText))))
        )

        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    Timber.e(e, "Ошибка сети")
                    trySend("⚠️ Ошибка сети. Проверьте подключение.")
                }
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        trySend("⚠️ Ошибка API: ${response.code} ${response.message}")
                        close()
                        return
                    }

                    val source = response.body?.source() ?: return
                    var fullResponse = ""

                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6)
                                if (data == "[DONE]") break
                                
                                val chunkJson = json.decodeFromString<GeminiResponse>(data)
                                val textChunk = chunkJson.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                                fullResponse += textChunk
                                trySend(fullResponse)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Ошибка парсинга потока")
                    } finally {
                        close()
                    }
                }
            }
        })

        // При отмене Flow (пользователь продолжил печатать) - отменяем сетевой запрос
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)
}