package com.example.mirroringapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mirroringapp.ui.theme.MirroringAppTheme
import com.example.mirroringapp.viewmodels.ReceiverViewModel
import org.webrtc.SurfaceViewRenderer
import org.webrtc.EglBase

class ReceiverActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MirroringAppTheme {
                val viewModel: ReceiverViewModel = viewModel()
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReceiverScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiverScreen(
    viewModel: ReceiverViewModel,
    modifier: Modifier = Modifier
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen Receiver",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Video display area
        if (isReceiving && remoteVideoTrack != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AndroidView(
                    factory = { context ->
                        SurfaceViewRenderer(context).apply {
                            init(EglBase.create().eglBaseContext, null)
                            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            setEnableHardwareScaler(false)
                            setMirror(false)
                        }
                    },
                    update = { surfaceView ->
                        remoteVideoTrack?.addSink(surfaceView)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnected) "Waiting for screen share..." else "Not connected",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        // Status card
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
                            text = "Receiving: ${if (isReceiving) "Active" else "Inactive"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    if (isReceiving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.startDiscovery() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Find Senders")
            }
            
            if (isReceiving) {
                Button(
                    onClick = { viewModel.stopReceiving() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
        }
        
        Divider()
        
        Text(
            text = "Available Senders",
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
                text = "No devices found. Make sure the sender device is discoverable.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReceiverScreenPreview() {
    MirroringAppTheme {
        ReceiverScreen(
            viewModel = ReceiverViewModel(application = null)
        )
    }
}
