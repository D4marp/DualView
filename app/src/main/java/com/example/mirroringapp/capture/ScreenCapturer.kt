package com.example.mirroringapp.capture

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import android.app.Activity

class ScreenCapturer(
    private val context: Context,
    private val mediaProjectionPermissionResultData: Intent,
    private val mediaProjectionPermissionResultCode: Int
) : VideoCapturer {
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var capturerObserver: CapturerObserver? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var surface: Surface? = null
    
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    private val displayMetrics = DisplayMetrics()
    private var width = 0
    private var height = 0
    
    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
    }
    
    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        context: Context?,
        capturerObserver: CapturerObserver?
    ) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }
    
    override fun startCapture(width: Int, height: Int, framerate: Int) {
        this.width = width
        this.height = height
        
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(
                mediaProjectionPermissionResultCode,
                mediaProjectionPermissionResultData
            )
            
            surface = Surface(surfaceTextureHelper?.surfaceTexture)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
            
            Log.d("ScreenCapturer", "Screen capture started: ${width}x${height}")
            
        } catch (e: Exception) {
            Log.e("ScreenCapturer", "Failed to start screen capture", e)
            capturerObserver?.onCapturerStarted(false)
            return
        }
        
        capturerObserver?.onCapturerStarted(true)
    }
    
    override fun stopCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            surface?.release()
            surface = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            Log.d("ScreenCapturer", "Screen capture stopped")
            
        } catch (e: Exception) {
            Log.e("ScreenCapturer", "Error stopping screen capture", e)
        }
    }
    
    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        // Stop and restart capture with new format
        stopCapture()
        startCapture(width, height, framerate)
    }
    
    override fun dispose() {
        stopCapture()
    }
    
    override fun isScreencast(): Boolean = true
}
