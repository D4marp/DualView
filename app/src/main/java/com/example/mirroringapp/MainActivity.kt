package com.example.mirroringapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.mirroringapp.viewmodels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MirroringAppTheme {
                val viewModel: MainViewModel = viewModel()
                val context = LocalContext.current
                
                // Request necessary permissions
                val permissions = listOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE,
                    android.Manifest.permission.CHANGE_WIFI_STATE
                )
                
                val permissionsState = rememberMultiplePermissionsState(permissions = permissions)
                
                LaunchedEffect(Unit) {
                    if (!permissionsState.allPermissionsGranted) {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                }
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                        onNavigateToSender = {
                            startActivity(Intent(context, SenderActivity::class.java))
                        },
                        onNavigateToReceiver = {
                            startActivity(Intent(context, ReceiverActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSender: () -> Unit,
    onNavigateToReceiver: () -> Unit
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen Mirroring App",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Choose your role:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNavigateToSender,
                modifier = Modifier.weight(1f)
            ) {
                Text("Share Screen")
            }
            
            Button(
                onClick = onNavigateToReceiver,
                modifier = Modifier.weight(1f)
            ) {
                Text("Receive Screen")
            }
        }
        
        Divider()
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.headlineSmall
            )
            
            if (isDiscovering) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.startDiscovery() },
                enabled = !isDiscovering,
                modifier = Modifier.weight(1f)
            ) {
                Text("Discover")
            }
            
            Button(
                onClick = { viewModel.stopDiscovery() },
                enabled = isDiscovering,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }
        
        LazyColumn {
            items(discoveredDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = device.deviceAddress,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Status: ${getStatusText(device.status)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun getStatusText(status: Int): String {
    return when (status) {
        0 -> "Connected"
        1 -> "Invited"
        2 -> "Failed"
        3 -> "Available"
        4 -> "Unavailable"
        else -> "Unknown"
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MirroringAppTheme {
        MainScreen(
            viewModel = MainViewModel(),
            onNavigateToSender = {},
            onNavigateToReceiver = {}
        )
    }
}