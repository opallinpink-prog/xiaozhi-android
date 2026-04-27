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
import kotlin.math.sqrt

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
        TransportType.MQTT       -> MqttProtocol(context, settings.mqttConfig!!)
        TransportType.WebSockets -> WebsocketProtocol(deviceInfo, settings.webSocketUrl!!, "test-token")
    }

    val display      = Display()
    var encoder: OpusEncoder?     = null
    var decoder: OpusDecoder?     = null
    var recorder: AudioRecorder?  = null
    var player: OpusStreamPlayer? = null
    var aborted     = false
    var keepListening = true

    val deviceStateFlow = MutableStateFlow(DeviceState.IDLE)

    // Amplitudes per i visualizer — StateFlow aggiornabili da qualsiasi thread
    val playerAmplitude = MutableStateFlow(0f)
    val micAmplitude    = MutableStateFlow(0f)

    val uiState: StateFlow<ChatUiState> = combine(
        display.chatFlow,
        deviceStateFlow,
        playerAmplitude,
        micAmplitude
    ) { messages, state, spkAmp, micAmp ->
        ChatUiState(messages, state, spkAmp, micAmp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    var deviceState: DeviceState
        get()  = deviceStateFlow.value
        set(v) { deviceStateFlow.value = v }

    init {
        deviceState = DeviceState.STARTING

        // ── Player + decoder (audio in uscita) ──────────────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            protocol.start()
            withContext(Dispatchers.Main) { deviceState = DeviceState.CONNECTING }

            if (protocol.openAudioChannel()) {
                protocol.sendStartListening(ListeningMode.AUTO_STOP)

                val sampleRate  = 24000
                val channels    = 1
                val frameSizeMs = 60
                player  = OpusStreamPlayer(sampleRate, channels, frameSizeMs)
                decoder = OpusDecoder(sampleRate, channels, frameSizeMs)

                // Collega amplitudeFlow del player al nostro StateFlow
                launch {
                    player!!.amplitudeFlow.collect { amp ->
                        playerAmplitude.value = amp
                    }
                }

                player?.start(protocol.incomingAudioFlow.map { opusBytes ->
                    withContext(Dispatchers.Main) { deviceState = DeviceState.SPEAKING }
                    decoder?.decode(opusBytes)
                })
            } else {
                Log.e(TAG, "Failed to open audio channel")
            }
        }

        // ── Recorder + encoder (audio in entrata) ───────────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            delay(1000)

            val sampleRate  = 16000
            val channels    = 1
            val frameSizeMs = 60
            encoder  = OpusEncoder(sampleRate, channels, frameSizeMs)
            recorder = AudioRecorder(sampleRate, channels, frameSizeMs)

            val audioFlow = recorder?.startRecording() ?: return@launch

            withContext(Dispatchers.Main) { deviceState = DeviceState.LISTENING }

            // Un unico collect: calcola RMS mic + encoda + invia, tutto in IO
            audioFlow.collect { pcm ->
                if (pcm != null) {
                    // RMS sul frame PCM grezzo → niente secondo AudioRecord!
                    val sampleCount = pcm.size / 2
                    if (sampleCount > 0) {
                        var sum = 0.0
                        for (i in 0 until sampleCount) {
                            val s = ((pcm[i * 2 + 1].toInt() shl 8) or
                                     (pcm[i * 2].toInt() and 0xFF)).toShort().toDouble()
                            sum += s * s
                        }
                        micAmplitude.value = (sqrt(sum / sampleCount).toFloat() / 10000f).coerceIn(0f, 1f)
                    }
                    encoder?.encode(pcm)?.let { protocol.sendAudio(it) }
                }
            }
            micAmplitude.value = 0f
        }

        // ── JSON dal server ──────────────────────────────────────────────────
        viewModelScope.launch {
            protocol.incomingJsonFlow.collect { json ->
                when (json.optString("type")) {
                    "tts" -> when (json.optString("state")) {
                        "start" -> {
                            aborted = false
                            if (deviceState == DeviceState.IDLE || deviceState == DeviceState.LISTENING)
                                deviceState = DeviceState.SPEAKING
                        }
                        "stop" -> {
                            if (deviceState == DeviceState.SPEAKING) {
                                withContext(Dispatchers.IO) { player?.waitForPlaybackCompletion() }
                                playerAmplitude.value = 0f
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
                            if (text.isNotEmpty()) display.setChatMessage("assistant", text)
                        }
                    }
                    "stt" -> {
                        val text = json.optString("text")
                        if (text.isNotEmpty()) display.setChatMessage("user", text)
                    }
                    "llm" -> {
                        val emotion = json.optString("emotion")
                        if (emotion.isNotEmpty()) display.setEmotion(emotion)
                    }
                }
            }
        }
    }

    fun toggleChatState()               { /* your existing code */ }
    fun startListening()                { /* your existing code */ }
    fun abortSpeaking(reason: AbortReason) { /* your existing code */ }
    fun stopListening()                 { /* your existing code */ }

    override fun onCleared() {
        protocol.dispose()
        encoder?.release()
        decoder?.release()
        player?.stop()
        recorder?.stopRecording()
        super.onCleared()
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages:          List<Message> = emptyList(),
    val deviceState:       DeviceState  = DeviceState.IDLE,
    val speakingAmplitude: Float        = 0f,
    val micAmplitude:      Float        = 0f
)

enum class DeviceState {
    UNKNOWN, STARTING, WIFI_CONFIGURING, IDLE, CONNECTING, LISTENING, SPEAKING, UPGRADING, ACTIVATING, FATAL_ERROR
}

class Display {
    val chatFlow    = MutableStateFlow<List<Message>>(emptyList())
    val emotionFlow = MutableStateFlow("neutral")
    fun setChatMessage(sender: String, message: String) {
        chatFlow.value = chatFlow.value + Message(sender, message)
    }
    fun setEmotion(emotion: String) { emotionFlow.value = emotion }
}

val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

data class Message(
    val sender:      String = "",
    val message:     String = "",
    val nowInString: String = df.format(System.currentTimeMillis())
) {
    val isUser:  Boolean get() = sender == "user"
    val content: String  get() = message
}
