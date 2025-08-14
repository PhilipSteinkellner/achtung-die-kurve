package com.example.achtungdiekurve.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val nickname = repo.nicknameFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val controlMode = repo.controlModeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ControlMode.TAP
    )

    fun setNickname(name: String) {
        viewModelScope.launch { repo.saveNickname(name) }
    }

    fun setControlMode(mode: ControlMode) {
        viewModelScope.launch { repo.saveControlMode(mode) }
    }
}

class SettingsViewModelFactory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingsViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
