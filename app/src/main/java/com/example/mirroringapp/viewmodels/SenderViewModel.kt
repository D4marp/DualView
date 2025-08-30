package com.example.mirroringapp.viewmodels

import android.app.Application
import android.content.Intent
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mirroringapp.wifi.WiFiDirectManager
import com.example.mirroringapp.webrtc.WebRTCManager
import com.example.mirroringapp.signaling.SignalingServer
import com.example.mirroringapp.capture.ScreenCapturer
import com.example.mirroringapp.models.DeviceInfo
import com.example.mirroringapp.models.SignalingMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

class SenderViewModel(application: Application?) : AndroidViewModel(application!!) {
    
    private val wifiDirectManager = WiFiDirectManager(getApplication())
    private val webRTCManager = WebRTCManager(getApplication())
    private val signalingServer = SignalingServer()
    
    private var screenCapturer: ScreenCapturer? = null
    private var mediaProjectionData: Intent? = null
    private var mediaProjectionResultCode: Int = 0
    
    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices
    
    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing
    
    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState?>(null)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState?> = _connectionState
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    init {
        setupObservers()
        setupWebRTCCallbacks()
        setupSignalingCallbacks()
    }
    
    private fun setupObservers() {
        // WiFi Direct observers
        viewModelScope.launch {
            wifiDirectManager.discoveredDevices.collect { devices ->
                _discoveredDevices.value = devices
            }
        }
        
        viewModelScope.launch {
            wifiDirectManager.isConnected.collect { connected ->
                _isConnected.value = connected
                if (connected) {
                    wifiDirectManager.connectionInfo.value?.let { info ->
                        if (info.groupFormed && info.isGroupOwner) {
                            startSignalingServer()
                        }
                    }
                }
            }
        }
        
        // WebRTC connection state observer
        viewModelScope.launch {
            webRTCManager.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
    }
    
    private fun setupWebRTCCallbacks() {
        webRTCManager.onSignalingMessage = { message ->
            signalingServer.sendMessage(message)
        }
    }
    
    private fun setupSignalingCallbacks() {
        signalingServer.onMessageReceived = { message, clientId ->
            webRTCManager.handleSignalingMessage(message)
        }
    }
    
    fun startDiscovery() {
        wifiDirectManager.startDiscovery()
    }
    
    fun connectToDevice(deviceAddress: String) {
        wifiDirectManager.connectToDevice(deviceAddress)
    }
    
    fun startScreenCapture(data: Intent, resultCode: Int) {
        mediaProjectionData = data
        mediaProjectionResultCode = resultCode
        
        if (_isConnected.value) {
            initializeScreenSharing()
        }
    }
    
    private fun startSignalingServer() {
        signalingServer.start()
        Log.d("SenderViewModel", "Signaling server started")
    }
    
    private fun initializeScreenSharing() {
        mediaProjectionData?.let { data ->
            try {
                webRTCManager.createPeerConnection()
                
                screenCapturer = ScreenCapturer(
                    getApplication(),
                    data,
                    mediaProjectionResultCode
                )
                
                screenCapturer?.let { capturer ->
                    webRTCManager.addVideoTrack(capturer)
                    webRTCManager.createOffer()
                    _isSharing.value = true
                    Log.d("SenderViewModel", "Screen sharing started")
                }
                
            } catch (e: Exception) {
                Log.e("SenderViewModel", "Failed to start screen sharing", e)
            }
        }
    }
    
    fun stopScreenSharing() {
        _isSharing.value = false
        screenCapturer?.dispose()
        screenCapturer = null
        webRTCManager.dispose()
        signalingServer.stop()
        wifiDirectManager.disconnect()
        Log.d("SenderViewModel", "Screen sharing stopped")
    }
    
    override fun onCleared() {
        super.onCleared()
        stopScreenSharing()
        wifiDirectManager.unregisterReceiver()
    }
}
