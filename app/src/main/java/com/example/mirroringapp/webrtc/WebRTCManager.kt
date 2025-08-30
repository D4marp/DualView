package com.example.mirroringapp.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.google.gson.Gson
import com.example.mirroringapp.models.SignalingMessage

class WebRTCManager(private val context: Context) {
    
    private val gson = Gson()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    
    private val _localVideoTrackState = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackState: StateFlow<VideoTrack?> = _localVideoTrackState
    
    private val _remoteVideoTrackState = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackState: StateFlow<VideoTrack?> = _remoteVideoTrackState
    
    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState?>(null)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState?> = _connectionState
    
    var onSignalingMessage: ((SignalingMessage) -> Unit)? = null
    
    init {
        initializePeerConnectionFactory()
    }
    
    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
    
    fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
                    Log.d("WebRTCManager", "Signaling state changed: $signalingState")
                }
                
                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
                    Log.d("WebRTCManager", "ICE connection state changed: $iceConnectionState")
                }
                
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.d("WebRTCManager", "Connection state changed: $newState")
                    _connectionState.value = newState
                }
                
                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    iceCandidate?.let {
                        val message = SignalingMessage("ice_candidate", gson.toJson(it))
                        onSignalingMessage?.invoke(message)
                    }
                }
                
                override fun onAddStream(mediaStream: MediaStream?) {
                    mediaStream?.videoTracks?.firstOrNull()?.let { videoTrack ->
                        remoteVideoTrack = videoTrack
                        _remoteVideoTrackState.value = videoTrack
                    }
                }
                
                override fun onRemoveStream(mediaStream: MediaStream?) {
                    Log.d("WebRTCManager", "Stream removed")
                }
                
                override fun onDataChannel(dataChannel: DataChannel?) {
                    Log.d("WebRTCManager", "Data channel received")
                }
                
                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {
                    Log.d("WebRTCManager", "ICE gathering state changed: $iceGatheringState")
                }
                
                override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>?) {
                    Log.d("WebRTCManager", "ICE candidates removed")
                }
                
                override fun onRenegotiationNeeded() {
                    Log.d("WebRTCManager", "Renegotiation needed")
                }
            }
        )
    }
    
    fun addVideoTrack(videoCapturer: VideoCapturer): VideoTrack? {
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglBase.create().eglBaseContext)
        val videoSource = peerConnectionFactory?.createVideoSource(videoCapturer.isScreencast)
        
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)
        
        localVideoTrack = peerConnectionFactory?.createVideoTrack("local_video", videoSource)
        _localVideoTrackState.value = localVideoTrack
        
        val mediaStream = peerConnectionFactory?.createLocalMediaStream("local_stream")
        mediaStream?.addTrack(localVideoTrack)
        peerConnection?.addStream(mediaStream)
        
        return localVideoTrack
    }
    
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            val message = SignalingMessage("offer", gson.toJson(it))
                            onSignalingMessage?.invoke(message)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCManager", "Create offer failed: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "Set local description failed: $error")
            }
        }, constraints)
    }
    
    fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            val message = SignalingMessage("answer", gson.toJson(it))
                            onSignalingMessage?.invoke(message)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCManager", "Create answer failed: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "Set local description failed: $error")
            }
        }, constraints)
    }
    
    fun handleSignalingMessage(message: SignalingMessage) {
        when (message.type) {
            "offer" -> {
                val offer = gson.fromJson(message.data, SessionDescription::class.java)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        createAnswer()
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, offer)
            }
            
            "answer" -> {
                val answer = gson.fromJson(message.data, SessionDescription::class.java)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, answer)
            }
            
            "ice_candidate" -> {
                val iceCandidate = gson.fromJson(message.data, IceCandidate::class.java)
                peerConnection?.addIceCandidate(iceCandidate)
            }
        }
    }
    
    fun dispose() {
        localVideoTrack?.dispose()
        remoteVideoTrack?.dispose()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
    }
}
