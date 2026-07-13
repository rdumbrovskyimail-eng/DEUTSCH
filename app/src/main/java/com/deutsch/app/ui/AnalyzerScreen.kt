package com.deutsch.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deutsch.app.R
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    viewModel: AnalyzerViewModel = hiltViewModel()
) {
    val inputText by viewModel.inputText.collectAsState()
    val reportText by viewModel.analysisReport.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.surface_dark))
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        // Окно 1: Ввод текста пользователем
        Text(
            text = "Ваш немецкий текст:",
            color = colorResource(id = R.color.text_secondary),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = viewModel::onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f) // Занимает 35% экрана
                .clip(RoundedCornerShape(16.dp)),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = colorResource(id = R.color.surface_controls),
                unfocusedBorderColor = colorResource(id = R.color.input_stroke_color),
                focusedBorderColor = colorResource(id = R.color.accent_blue)
            ),
            placeholder = { Text("Например: Ich habe ein Auto gekauft...") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Индикатор загрузки / Статус
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Мгновенный анализ (A1-B2):",
                color = colorResource(id = R.color.accent_blue),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            AnimatedVisibility(
                visible = isAnalyzing,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colorResource(id = R.color.accent_blue),
                    strokeWidth = 2.dp
                )
            }
        }

        // Окно 2: Отчет Анализатора
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f), // Занимает 65% экрана
            shape = RoundedCornerShape(16.dp),
            color = colorResource(id = R.color.surface_elevated),
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val scrollState = rememberScrollState()

                // Используем MarkdownText для красивого форматирования
                MarkdownText(
                    markdown = reportText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    color = Color(0xFF3A3423), // text_primary из твоей темы
                    fontSize = 16.sp
                )
            }
        }
    }
}