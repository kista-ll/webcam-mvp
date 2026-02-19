package com.example.webrtccamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class MainActivity : AppCompatActivity() {
    private lateinit var signalingEditText: TextInputEditText
    private lateinit var roomEditText: TextInputEditText
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var iceStateTextView: TextView
    private lateinit var localRenderer: SurfaceViewRenderer

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            connect()
        } else {
            iceStateTextView.text = "iceConnectionState: CAMERA permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signalingEditText = findViewById(R.id.signalingEditText)
        roomEditText = findViewById(R.id.roomEditText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        iceStateTextView = findViewById(R.id.iceStateTextView)
        localRenderer = findViewById(R.id.localRenderer)

        connectButton.setOnClickListener {
            ensureCameraPermissionAndConnect()
        }
        disconnectButton.setOnClickListener {
            disconnect()
        }
    }

    private fun ensureCameraPermissionAndConnect() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            connect()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeWebRtcIfNeeded() {
        if (peerConnectionFactory != null) return

        eglBase = EglBase.create()
        localRenderer.init(eglBase!!.eglBaseContext, null)
        localRenderer.setMirror(true)

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        videoCapturer = createVideoCapturer()
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
        videoSource = peerConnectionFactory!!.createVideoSource(false)
        videoCapturer?.initialize(
            surfaceTextureHelper,
            applicationContext,
            videoSource?.capturerObserver
        )
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory!!.createVideoTrack("localVideoTrack", videoSource)
        localVideoTrack?.addSink(localRenderer)
    }

    private fun connect() {
        initializeWebRtcIfNeeded()
        createPeerConnection()

        val signalingUrl = buildSignalingUrl(
            signalingEditText.text?.toString().orEmpty(),
            roomEditText.text?.toString().orEmpty().ifBlank { "abc" }
        )

        val request = Request.Builder().url(signalingUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread {
                    iceStateTextView.text = "iceConnectionState: signaling connected"
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSignalingMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    iceStateTextView.text = "iceConnectionState: signaling closed"
                }
            }
        })
    }

    private fun createPeerConnection() {
        if (peerConnection != null) return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    iceStateTextView.text = "iceConnectionState: ${newState ?: "UNKNOWN"}"
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                sendIceCandidate(candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) = Unit
            override fun onCandidatePairChanged(event: CandidatePairChangeEvent?) = Unit
        })

        localVideoTrack?.let {
            peerConnection?.addTrack(it, listOf("localStream"))
        }
    }

    private fun handleSignalingMessage(text: String) {
        val root = JSONObject(text)
        val type = root.optString("type")
        val payload = root.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "offer" -> handleOffer(payload)
            "ice" -> handleRemoteIce(payload)
        }
    }

    private fun handleOffer(payload: JSONObject) {
        val sdp = payload.getString("sdp")
        val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), remoteDescription)

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                sendAnswer(sessionDescription)
            }
        }, MediaConstraints())
    }

    private fun handleRemoteIce(payload: JSONObject) {
        val candidate = payload.optString("candidate", null) ?: return
        val sdpMid = payload.optString("sdpMid", null)
        val sdpMLineIndex = payload.optInt("sdpMLineIndex", -1)
        if (sdpMLineIndex < 0) return
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    private fun sendAnswer(sessionDescription: SessionDescription) {
        val payload = JSONObject()
            .put("sdp", sessionDescription.description)
            .put("type", "answer")
        val root = JSONObject()
            .put("type", "answer")
            .put("payload", payload)
        webSocket?.send(root.toString())
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val payload = JSONObject()
            .put("candidate", candidate.sdp)
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
        val root = JSONObject()
            .put("type", "ice")
            .put("payload", payload)
        webSocket?.send(root.toString())
    }

    private fun buildSignalingUrl(rawUrl: String, room: String): String {
        if (rawUrl.isBlank()) return "ws://192.168.0.5:3001?room=$room"
        return if (rawUrl.contains("room=")) {
            rawUrl
        } else {
            val separator = if (rawUrl.contains("?")) "&" else "?"
            "$rawUrl${separator}room=$room"
        }
    }

    private fun disconnect() {
        webSocket?.close(1000, "disconnect")
        webSocket = null

        peerConnection?.close()
        peerConnection = null

        videoCapturer?.stopCaptureSafely()
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrack?.dispose()
        localVideoTrack = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        localRenderer.release()
        eglBase?.release()
        eglBase = null

        runOnUiThread {
            iceStateTextView.text = "iceConnectionState: disconnected"
        }
    }

    override fun onDestroy() {
        disconnect()
        okHttpClient.dispatcher.executorService.shutdown()
        super.onDestroy()
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(this)
        val deviceNames = enumerator.deviceNames

        deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let { frontCamera ->
            enumerator.createCapturer(frontCamera, null)?.let { return it }
        }

        deviceNames.firstOrNull()?.let { fallback ->
            enumerator.createCapturer(fallback, null)?.let { return it }
        }

        return null
    }
}

private fun VideoCapturer.stopCaptureSafely() {
    try {
        stopCapture()
    } catch (_: InterruptedException) {
    }
}

open class SimpleSdpObserver : org.webrtc.SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}
