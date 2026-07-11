package com.autocaller.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autocaller.data.SettingsRepository
import com.autocaller.service.AutoCallerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val phoneNumber: StateFlow<String> = repository.phoneNumberFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val intervalMinutes: StateFlow<Int> = repository.intervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_INTERVAL)

    val isRunning: StateFlow<Boolean> = repository.isRunningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onPhoneNumberChange(number: String) {
        viewModelScope.launch { repository.savePhoneNumber(number) }
    }

    fun onIntervalChange(minutes: Int) {
        val clamped = minutes.coerceIn(1, 60)
        viewModelScope.launch { repository.saveIntervalMinutes(clamped) }
    }

    fun startCalling() {
        val number = phoneNumber.value
        if (number.isBlank()) {
            _errorMessage.value = "Please enter a phone number before starting."
            return
        }
        val ctx = getApplication<Application>()
        val intent = AutoCallerService.startIntent(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun stopCalling() {
        val ctx = getApplication<Application>()
        ctx.startService(AutoCallerService.stopIntent(ctx))
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
