package info.dourok.voicebot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class OpusStreamPlayer(
    private val sampleRate: Int,
    private val channels: Int,
    frameSizeMs: Int
) {
    companion object {
        private const val TAG = "OpusStreamPlayer"
    }

    private var audioTrack: AudioTrack
    private val playerScope = CoroutineScope(Dispatchers.IO + Job())
    private var isPlaying = false

    // ── RMS amplitude (0f..1f) exposed for the visualizer ──────────────────
    // Updated every time a PCM frame is written to AudioTrack.
    // Typical speech RMS on 16-bit PCM peaks around 6000–10000; we normalise
    // against 12000 so the visualizer gets a useful 0..1 range.
    private val _amplitudeFlow = MutableStateFlow(0f)
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    init {
        val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun start(pcmFlow: Flow<ByteArray?>) {
        if (!isPlaying) {
            isPlaying = true
            if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play()
            }

            playerScope.launch {
                pcmFlow.collect { pcmData ->
                    pcmData?.let { bytes ->
                        try {
                            audioTrack.write(bytes, 0, bytes.size)
                            // ── Compute RMS from the raw 16-bit PCM bytes ──
                            _amplitudeFlow.value = computeRms(bytes)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing to AudioTrack", e)
                        }
                    }
                }
                // Flow ended → silence the visualizer
                _amplitudeFlow.value = 0f
            }
        }
    }

    fun stop() {
        if (isPlaying) {
            isPlaying = false
            _amplitudeFlow.value = 0f
            if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack.stop()
            }
        }
    }

    fun release() {
        stop()
        audioTrack.release()
    }

    suspend fun waitForPlaybackCompletion() {
        var position = 0
        while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
               audioTrack.playbackHeadPosition != position
        ) {
            Log.i(TAG, "playState: ${audioTrack.playState}, head: ${audioTrack.playbackHeadPosition}")
            position = audioTrack.playbackHeadPosition
            delay(100)
        }
    }

    protected fun finalize() {
        release()
    }

    // ── Helper ──────────────────────────────────────────────────────────────
    // PCM is little-endian 16-bit signed samples; two bytes per sample.
    private fun computeRms(pcm: ByteArray): Float {
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return 0f
        var sum = 0.0
        for (i in 0 until sampleCount) {
            // Reconstruct signed 16-bit sample from two bytes (little-endian)
            val sample = (pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sum / sampleCount).toFloat()
        return (rms / 12000f).coerceIn(0f, 1f)
    }
}
