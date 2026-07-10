package com.deutsch.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DeutschApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация Timber для логирования
        Timber.plant(Timber.DebugTree())
    }
}