package info.dourok.voicebot.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.dourok.voicebot.AudioRecorder
import info.dourok.voicebot.NavigationEvents
import info.dourok.voicebot.OpusDecoder
import info.dourok.voicebot.OpusEncoder
import info.dourok.voicebot.OpusStreamPlayer
import info.dourok.voicebot.data.SettingsRepository
import info.dourok.voicebot.data.model.DeviceInfo
import info.dourok.voicebot.data.model.TransportType
import info.dourok.voicebot.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @NavigationEvents private val navigationEvents: MutableSharedFlow<String>,
    deviceInfo: DeviceInfo,
    settings: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val protocol: Protocol = when (settings.transportType) {
        TransportType.MQTT -> MqttProtocol(context, settings.mqttConfig!!)
        TransportType.WebSockets -> WebsocketProtocol(deviceInfo, settings.webSocketUrl!!, "test-token")
    }

    val display = Display()
    var encoder: OpusEncoder? = null
    var decoder: OpusDecoder? = null
    var recorder: AudioRecorder? = null
    var player: OpusStreamPlayer? = null
    var aborted: Boolean = false
    var keepListening: Boolean = true

    val deviceStateFlow = MutableStateFlow(DeviceState.IDLE)

    // ── Amplitude from the player, flattened to a single flow ───────────────
    // When player is null (not yet created) we emit 0f.
    // switchMap re-subscribes automatically once player is assigned.
    private val _playerAmplitude = MutableStateFlow(0f)

    // ── Combined UI state ────────────────────────────────────────────────────
    val uiState: StateFlow<ChatUiState> = combine(
        display.chatFlow,
        deviceStateFlow,
        _playerAmplitude
    ) { messages, state, amplitude ->
        ChatUiState(messages, state, amplitude)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    var deviceState: DeviceState
        get() = deviceStateFlow.value
        set(value) { deviceStateFlow.value = value }

    init {
        deviceState = DeviceState.STARTING
        viewModelScope.launch {
            protocol.start()
            deviceState = DeviceState.CONNECTING
            if (protocol.openAudioChannel()) {
                protocol.sendStartListening(ListeningMode.AUTO_STOP)
                withContext(Dispatchers.IO) {
                    launch {
                        val sampleRate = 24000
                        val channels = 1
                        val frameSizeMs = 60
                        player = OpusStreamPlayer(sampleRate, channels, frameSizeMs)
                        decoder = OpusDecoder(sampleRate, channels, frameSizeMs)

                        // ── Forward player amplitude to the UI state ─────────
                        launch {
                            player!!.amplitudeFlow.collect { amp ->
                                _playerAmplitude.value = amp
                            }
                        }

                        player?.start(protocol.incomingAudioFlow.map {
                            deviceState = DeviceState.SPEAKING
                            decoder?.decode(it)
                        })
                    }
                }
            } else {
                Log.e("WS", "Failed to open audio channel")
            }

            delay(1000)

            launch {
                val sampleRate = 16000
                val channels = 1
                val frameSizeMs = 60
                encoder = OpusEncoder(sampleRate, channels, frameSizeMs)
                recorder = AudioRecorder(sampleRate, channels, frameSizeMs)
                val audioFlow = recorder?.startRecording()
                val opusFlow = audioFlow?.map { encoder?.encode(it) }
                deviceState = DeviceState.LISTENING
                opusFlow?.collect { it?.let { protocol.sendAudio(it) } }
            }

            launch {
                protocol.incomingJsonFlow.collect { json ->
                    val type = json.optString("type")
                    when (type) {
                        "tts" -> {
                            when (json.optString("state")) {
                                "start" -> schedule {
                                    aborted = false
                                    if (deviceState == DeviceState.IDLE || deviceState == DeviceState.LISTENING) {
                                        deviceState = DeviceState.SPEAKING
                                    }
                                }
                                "stop" -> schedule {
                                    if (deviceState == DeviceState.SPEAKING) {
                                        player?.waitForPlaybackCompletion()
                                        if (keepListening) {
                                            protocol.sendStartListening(ListeningMode.AUTO_STOP)
                                            deviceState = DeviceState.LISTENING
                                        } else {
                                            deviceState = DeviceState.IDLE
                                        }
                                    }
                                }
                                "sentence_start" -> {
                                    val text = json.optString("text")
                                    if (text.isNotEmpty()) schedule { display.setChatMessage("assistant", text) }
                                }
                            }
                        }
                        "stt" -> {
                            val text = json.optString("text")
                            if (text.isNotEmpty()) schedule { display.setChatMessage("user", text) }
                        }
                        "llm" -> {
                            val emotion = json.optString("emotion")
                            if (emotion.isNotEmpty()) schedule { display.setEmotion(emotion) }
                        }
                    }
                }
            }
        }
    }

    fun toggleChatState() { /* your existing code */ }
    fun startListening() { /* your existing code */ }
    fun abortSpeaking(reason: AbortReason) { /* your existing code */ }
    private fun schedule(task: suspend () -> Unit) { viewModelScope.launch { task() } }
    fun stopListening() { /* your existing code */ }

    override fun onCleared() {
        protocol.dispose()
        encoder?.release()
        decoder?.release()
        player?.stop()
        recorder?.stopRecording()
        super.onCleared()
    }
}

// ── Data classes ─────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val deviceState: DeviceState = DeviceState.IDLE,
    val speakingAmplitude: Float = 0f          // ← nuovo campo
)

enum class DeviceState {
    UNKNOWN, STARTING, WIFI_CONFIGURING, IDLE, CONNECTING, LISTENING, SPEAKING, UPGRADING, ACTIVATING, FATAL_ERROR
}

class Display {
    val chatFlow = MutableStateFlow<List<Message>>(listOf())
    val emotionFlow = MutableStateFlow<String>("neutral")
    fun setChatMessage(sender: String, message: String) {
        chatFlow.value = chatFlow.value + Message(sender, message)
    }
    fun setEmotion(emotion: String) { emotionFlow.value = emotion }
}

val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

data class Message(
    val sender: String = "",
    val message: String = "",
    val nowInString: String = df.format(System.currentTimeMillis())
) {
    val isUser: Boolean get() = sender == "user"
    val content: String get() = message
}
