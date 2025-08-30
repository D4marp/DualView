package com.example.mirroringapp.signaling

import android.util.Log
import com.google.gson.Gson
import com.example.mirroringapp.models.SignalingMessage
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class SignalingServer(private val port: Int = 8888) {
    
    private val gson = Gson()
    private var serverSocket: ServerSocket? = null
    private val clientSockets = ConcurrentHashMap<String, Socket>()
    private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    var onMessageReceived: ((SignalingMessage, String) -> Unit)? = null
    
    fun start() {
        if (isRunning) return
        
        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d("SignalingServer", "Server started on port $port")
                
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            val clientId = socket.remoteSocketAddress.toString()
                            clientSockets[clientId] = socket
                            Log.d("SignalingServer", "Client connected: $clientId")
                            
                            launch {
                                handleClient(socket, clientId)
                            }
                        }
                    } catch (e: SocketException) {
                        if (isRunning) {
                            Log.e("SignalingServer", "Socket error", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("SignalingServer", "Server error", e)
            }
        }
    }
    
    fun stop() {
        isRunning = false
        clientSockets.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                Log.w("SignalingServer", "Error closing client socket", e)
            }
        }
        clientSockets.clear()
        
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w("SignalingServer", "Error closing server socket", e)
        }
        
        serverScope.cancel()
        Log.d("SignalingServer", "Server stopped")
    }
    
    private suspend fun handleClient(socket: Socket, clientId: String) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            
            while (isRunning && !socket.isClosed) {
                val message = reader.readLine()
                if (message != null) {
                    try {
                        val signalingMessage = gson.fromJson(message, SignalingMessage::class.java)
                        withContext(Dispatchers.Main) {
                            onMessageReceived?.invoke(signalingMessage, clientId)
                        }
                    } catch (e: Exception) {
                        Log.e("SignalingServer", "Error parsing message", e)
                    }
                }
            }
        } catch (e: IOException) {
            Log.d("SignalingServer", "Client disconnected: $clientId")
        } finally {
            clientSockets.remove(clientId)
            try {
                socket.close()
            } catch (e: IOException) {
                Log.w("SignalingServer", "Error closing client socket", e)
            }
        }
    }
    
    fun sendMessage(message: SignalingMessage, targetClientId: String? = null) {
        val messageJson = gson.toJson(message)
        
        serverScope.launch {
            if (targetClientId != null) {
                // Send to specific client
                clientSockets[targetClientId]?.let { socket ->
                    sendToSocket(socket, messageJson)
                }
            } else {
                // Broadcast to all clients
                clientSockets.values.forEach { socket ->
                    sendToSocket(socket, messageJson)
                }
            }
        }
    }
    
    private fun sendToSocket(socket: Socket, message: String) {
        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(message)
        } catch (e: IOException) {
            Log.e("SignalingServer", "Error sending message", e)
        }
    }
}

class SignalingClient(private val serverAddress: String, private val port: Int = 8888) {
    
    private val gson = Gson()
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnected = false
    
    var onMessageReceived: ((SignalingMessage) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    
    fun connect() {
        clientScope.launch {
            try {
                socket = Socket(serverAddress, port)
                writer = PrintWriter(socket?.getOutputStream(), true)
                isConnected = true
                
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(true)
                }
                
                Log.d("SignalingClient", "Connected to server $serverAddress:$port")
                
                // Start listening for messages
                val reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                
                while (isConnected && socket?.isConnected == true) {
                    val message = reader.readLine()
                    if (message != null) {
                        try {
                            val signalingMessage = gson.fromJson(message, SignalingMessage::class.java)
                            withContext(Dispatchers.Main) {
                                onMessageReceived?.invoke(signalingMessage)
                            }
                        } catch (e: Exception) {
                            Log.e("SignalingClient", "Error parsing message", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("SignalingClient", "Connection error", e)
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(false)
                }
            }
        }
    }
    
    fun disconnect() {
        isConnected = false
        
        try {
            writer?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.w("SignalingClient", "Error closing connection", e)
        }
        
        clientScope.cancel()
        
        onConnectionStateChanged?.invoke(false)
        Log.d("SignalingClient", "Disconnected from server")
    }
    
    fun sendMessage(message: SignalingMessage) {
        clientScope.launch {
            try {
                val messageJson = gson.toJson(message)
                writer?.println(messageJson)
            } catch (e: Exception) {
                Log.e("SignalingClient", "Error sending message", e)
            }
        }
    }
}
