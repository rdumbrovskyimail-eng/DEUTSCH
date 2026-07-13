package com.deutsch.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutsch.app.data.GeminiRepository
import com.deutsch.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyzerViewModel @Inject constructor(
    private val repository: GeminiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Реактивно получаем ключ из настроек
    val apiKey: StateFlow<String> = settingsRepository.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Главный пайплайн анализатора
    val analysisReport: StateFlow<String> = _inputText
        .debounce(600)
        .distinctUntilChanged()
        .flatMapLatest { text ->
            _isAnalyzing.value = true
            val currentKey = apiKey.value
            
            if (currentKey.isBlank()) {
                _isAnalyzing.value = false
                flowOf("⚠️ **API-ключ не найден!**\n\nПожалуйста, нажмите на иконку шестеренки в правом верхнем углу и введите ваш ключ Gemini API.")
            } else if (text.isBlank()) {
                _isAnalyzing.value = false
                flowOf("Напишите что-нибудь по-немецки, и я мгновенно это проанализирую...")
            } else {
                flow {
                    emit("⏳ *Анализирую...*") // Сброс старого текста
                    repository.analyzeTextStream(text, currentKey).collect { chunk ->
                        emit(chunk)
                    }
                }
                .catch { emit("⚠️ Произошла ошибка сети или неверный API-ключ.") }
                .onCompletion { _isAnalyzing.value = false }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Напишите что-нибудь по-немецки, и я мгновенно это проанализирую..."
        )

    fun onTextChanged(newText: String) {
        _inputText.value = newText
        if (newText.isNotBlank() && apiKey.value.isNotBlank()) {
            _isAnalyzing.value = true
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveApiKey(key.trim())
        }
    }
}