package com.example.mirroringapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mirroringapp.ui.theme.MirroringAppTheme
import com.example.mirroringapp.viewmodels.SenderViewModel

class SenderActivity : ComponentActivity() {
    
    private lateinit var viewModel: SenderViewModel
    
    private val screenCaptureResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                viewModel.startScreenCapture(data, result.resultCode)
            }
        } else {
            Log.w("SenderActivity", "Screen capture permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MirroringAppTheme {
                viewModel = viewModel()
                
                val context = LocalContext.current
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SenderScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                        onRequestScreenCapture = {
                            requestScreenCapturePermission()
                        },
                        onStartService = {
                            startScreenCaptureService()
                        }
                    )
                }
            }
        }
    }
    
    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureResult.launch(captureIntent)
    }
    
    private fun startScreenCaptureService() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        startForegroundService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScreenSharing()
    }
}

@Composable
fun SenderScreen(
    viewModel: SenderViewModel,
    modifier: Modifier = Modifier,
    onRequestScreenCapture: () -> Unit,
    onStartService: () -> Unit
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isSharing by viewModel.isSharing.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen Sender",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "WiFi Direct: ${if (isConnected) "Connected" else "Disconnected"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "WebRTC: ${connectionState?.name ?: "Not connected"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Sharing: ${if (isSharing) "Active" else "Inactive"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    if (isSharing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.startDiscovery() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Find Devices")
            }
            
            Button(
                onClick = {
                    onRequestScreenCapture()
                    onStartService()
                },
                enabled = !isSharing && isConnected,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Sharing")
            }
        }
        
        if (isSharing) {
            Button(
                onClick = { viewModel.stopScreenSharing() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Stop Sharing")
            }
        }
        
        Divider()
        
        Text(
            text = "Available Receivers",
            style = MaterialTheme.typography.headlineSmall
        )
        
        LazyColumn {
            items(discoveredDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = device.deviceName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = device.deviceAddress,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.connectToDevice(device.deviceAddress) },
                            enabled = !isConnected
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
        
        if (discoveredDevices.isEmpty()) {
            Text(
                text = "No devices found. Make sure the receiver device is discoverable.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SenderScreenPreview() {
    MirroringAppTheme {
        SenderScreen(
            viewModel = SenderViewModel(application = null),
            onRequestScreenCapture = {},
            onStartService = {}
        )
    }
}
