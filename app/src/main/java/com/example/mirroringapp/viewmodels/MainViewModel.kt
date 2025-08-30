package com.example.mirroringapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mirroringapp.wifi.WiFiDirectManager
import com.example.mirroringapp.models.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val wifiDirectManager = WiFiDirectManager(application)
    
    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices
    
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering
    
    init {
        // Observe WiFi Direct discovered devices
        viewModelScope.launch {
            wifiDirectManager.discoveredDevices.collect { devices ->
                _discoveredDevices.value = devices
            }
        }
    }
    
    fun startDiscovery() {
        _isDiscovering.value = true
        wifiDirectManager.startDiscovery()
        
        // Stop discovery after 30 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(30000)
            stopDiscovery()
        }
    }
    
    fun stopDiscovery() {
        _isDiscovering.value = false
        wifiDirectManager.stopDiscovery()
    }
    
    override fun onCleared() {
        super.onCleared()
        wifiDirectManager.unregisterReceiver()
    }
}
