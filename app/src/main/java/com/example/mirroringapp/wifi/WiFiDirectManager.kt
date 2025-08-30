package com.example.mirroringapp.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.mirroringapp.models.DeviceInfo

class WiFiDirectManager(private val context: Context) {
    
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    
    private var channel: WifiP2pManager.Channel? = null
    
    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices
    
    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val receiver = WiFiDirectBroadcastReceiver()
    
    init {
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        registerReceiver()
    }
    
    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
    }
    
    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w("WiFiDirectManager", "Receiver not registered", e)
        }
    }
    
    fun startDiscovery() {
        try {
            wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirectManager", "Discovery initiated successfully")
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirectManager", "Discovery failed: $reasonCode")
                }
            })
        } catch (e: SecurityException) {
            Log.e("WiFiDirectManager", "Permission not granted for discovery", e)
        }
    }
    
    fun stopDiscovery() {
        try {
            wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirectManager", "Discovery stopped successfully")
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirectManager", "Stop discovery failed: $reasonCode")
                }
            })
        } catch (e: SecurityException) {
            Log.e("WiFiDirectManager", "Permission not granted for stopping discovery", e)
        }
    }
    
    fun connectToDevice(deviceAddress: String) {
        val config = WifiP2pConfig().apply {
            deviceAddress = deviceAddress
        }
        
        try {
            wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirectManager", "Connection initiated successfully")
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirectManager", "Connection failed: $reasonCode")
                }
            })
        } catch (e: SecurityException) {
            Log.e("WiFiDirectManager", "Permission not granted for connection", e)
        }
    }
    
    fun disconnect() {
        try {
            wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirectManager", "Group removed successfully")
                    _isConnected.value = false
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirectManager", "Remove group failed: $reasonCode")
                }
            })
        } catch (e: SecurityException) {
            Log.e("WiFiDirectManager", "Permission not granted for disconnect", e)
        }
    }
    
    private inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d("WiFiDirectManager", "WiFi P2P state changed: $state")
                }
                
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    try {
                        wifiP2pManager?.requestPeers(channel) { peers ->
                            val deviceList = peers.deviceList.map { device ->
                                DeviceInfo(
                                    deviceName = device.deviceName ?: "Unknown",
                                    deviceAddress = device.deviceAddress,
                                    status = device.status
                                )
                            }
                            _discoveredDevices.value = deviceList
                            Log.d("WiFiDirectManager", "Peers updated: ${deviceList.size} devices found")
                        }
                    } catch (e: SecurityException) {
                        Log.e("WiFiDirectManager", "Permission not granted for requesting peers", e)
                    }
                }
                
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    try {
                        wifiP2pManager?.requestConnectionInfo(channel) { info ->
                            _connectionInfo.value = info
                            _isConnected.value = info.groupFormed
                            Log.d("WiFiDirectManager", "Connection info updated: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}")
                        }
                    } catch (e: SecurityException) {
                        Log.e("WiFiDirectManager", "Permission not granted for connection info", e)
                    }
                }
                
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    Log.d("WiFiDirectManager", "This device changed: ${device?.deviceName}")
                }
            }
        }
    }
}
