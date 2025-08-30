package com.example.mirroringapp.models

data class DeviceInfo(
    val deviceName: String,
    val deviceAddress: String,
    val isGroupOwner: Boolean = false,
    val status: Int = 0
)

data class SignalingMessage(
    val type: String,
    val data: String
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
