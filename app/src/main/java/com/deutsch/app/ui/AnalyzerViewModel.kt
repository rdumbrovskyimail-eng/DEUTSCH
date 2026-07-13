package com.deutsch.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutsch.app.data.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyzerViewModel @Inject constructor(
    private val repository: GeminiRepository
) : ViewModel() {

    private val apiKey = com.deutsch.app.BuildConfig.GEMINI_API_KEY

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Главный пайплайн анализатора
    val analysisReport: StateFlow<String> = _inputText
        .debounce(600) // Ждем 600мс после последнего нажатия клавиши
        .distinctUntilChanged()
        .onEach { _isAnalyzing.value = true }
        .flatMapLatest { text ->
            if (text.isBlank()) {
                _isAnalyzing.value = false
                flowOf("Напишите что-нибудь по-немецки, и я мгновенно это проанализирую...")
            } else {
                repository.analyzeTextStream(text, apiKey)
                    .catch { emit("Произошла непредвиденная ошибка при анализе.") }
                    .onEach { _isAnalyzing.value = false }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Напишите что-нибудь по-немецки, и я мгновенно это проанализирую..."
        )

    fun onTextChanged(newText: String) {
        _inputText.value = newText
        if (newText.isNotBlank()) {
            _isAnalyzing.value = true
        }
    }
    
    // Сюда мы передадим твою базу грамматики
    fun loadGrammarRules(rules: String) {
        repository.updateGrammarDatabase(rules)
    }
}