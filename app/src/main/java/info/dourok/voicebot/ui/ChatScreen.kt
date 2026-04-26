package info.dourok.voicebot.ui

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

// ---------------------------------------------------------------------------
// ChatScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showChatMessages by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xiaozhi Assistant", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { showChatMessages = !showChatMessages }) {
                        Icon(
                            imageVector = if (showChatMessages) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Chat",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // ── Visualizer ──────────────────────────────────────────────────
            DynamicVisualizer(
                deviceState = uiState.deviceState,
                speakingAmplitude = uiState.speakingAmplitude
            )

            // ── Chat overlay ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showChatMessages,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    reverseLayout = true
                ) {
                    items(uiState.messages) { message ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (message.isUser) Color(0xFF1E88E5) else Color(0xFF424242),
                                modifier = Modifier.align(
                                    if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
                                )
                            ) {
                                Text(
                                    text = message.content as String,
                                    color = Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DynamicVisualizer — routes to the correct sub-composable based on state
// ---------------------------------------------------------------------------

@Composable
fun DynamicVisualizer(deviceState: DeviceState, speakingAmplitude: Float = 0f) {
    when (deviceState) {
        DeviceState.LISTENING -> ListeningVisualizer()
        DeviceState.SPEAKING  -> SpeakingVisualizer(externalAmplitude = speakingAmplitude)
        else                  -> StandbyVisualizer()
    }
}

// ---------------------------------------------------------------------------
// 1. STANDBY — aurora HSV sweep along a horizontal line
// ---------------------------------------------------------------------------

@Composable
fun StandbyVisualizer() {
    // hueOffset sweeps 0..360 continuously → full aurora cycle in ~4 s
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val hueOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue"
    )
    // Gentle vertical pulse: the line grows/shrinks slightly
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = size.height / 2f
        val lineHeight = 6.dp.toPx() * pulse
        val trackWidth = size.width * 0.82f
        val startX = (size.width - trackWidth) / 2f

        // Build a horizontal gradient that cycles through the HSV spectrum
        val steps = 32
        val colors = List(steps + 1) { i ->
            val hue = ((hueOffset + (360f / steps) * i) % 360f)
            Color.hsv(hue, 0.85f, 1f)
        }
        val brush = Brush.horizontalGradient(
            colors = colors,
            startX = startX,
            endX = startX + trackWidth
        )

        drawRect(
            brush = brush,
            topLeft = Offset(startX, centerY - lineHeight / 2f),
            size = Size(trackWidth, lineHeight)
        )

        // Soft glow: a wider, semi-transparent copy of the same gradient
        val glowBrush = Brush.horizontalGradient(
            colors = colors.map { it.copy(alpha = 0.18f) },
            startX = startX,
            endX = startX + trackWidth
        )
        drawRect(
            brush = glowBrush,
            topLeft = Offset(startX, centerY - lineHeight * 3f),
            size = Size(trackWidth, lineHeight * 6f)
        )
    }
}

// ---------------------------------------------------------------------------
// 2. LISTENING — red circle that pulses reacting to mic amplitude
// ---------------------------------------------------------------------------

@Composable
fun ListeningVisualizer() {
    // micAmplitude is updated from a background coroutine reading AudioRecord
    var micAmplitude by remember { mutableFloatStateOf(0f) }

    // Smoothed value to avoid jitter
    var smoothed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            recorder.startRecording()
            val buffer = ShortArray(bufferSize / 2)
            try {
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (j in 0 until read) sum += buffer[j].toDouble().pow(2.0)
                        val rms = sqrt(sum / read).toFloat()
                        // Normalise: typical speech peaks around 8000–12000 RMS on 16-bit
                        micAmplitude = (rms / 10000f).coerceIn(0f, 1f)
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    // Smooth on the UI coroutine (runs every ~16 ms, ~60 fps)
    LaunchedEffect(Unit) {
        while (true) {
            smoothed = smoothed + (micAmplitude - smoothed) * 0.25f
            delay(16L)
        }
    }

    // Base pulse animation (heartbeat-like) that exists even in silence
    val infiniteTransition = rememberInfiniteTransition(label = "listen")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "basePulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Mic energy expands the circle beyond the base pulse
        val micExpand = smoothed * 0.55f       // up to +55 % of baseRadius
        val baseRadius = minOf(size.width, size.height) * 0.13f
        val radius = baseRadius * (basePulse + micExpand)

        // Outer glow ring (softer, larger)
        drawCircle(
            color = Color.Red.copy(alpha = 0.15f + smoothed * 0.25f),
            radius = radius * 1.55f,
            center = Offset(cx, cy)
        )
        // Mid glow
        drawCircle(
            color = Color.Red.copy(alpha = 0.3f + smoothed * 0.2f),
            radius = radius * 1.2f,
            center = Offset(cx, cy)
        )
        // Solid core
        drawCircle(
            color = Color(0xFFFF1744),
            radius = radius,
            center = Offset(cx, cy)
        )
        // Bright highlight spot
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = radius * 0.38f,
            center = Offset(cx - radius * 0.22f, cy - radius * 0.22f)
        )
    }
}

// ---------------------------------------------------------------------------
// 3. SPEAKING — KITT-style cyan bars (procedural audio-like animation)
// ---------------------------------------------------------------------------

/**
 * The bars use a combination of:
 *  - a slow sine "carrier" wave that moves across bars (KITT scan effect)
 *  - fast per-bar noise seeded by time (simulates audio energy)
 *  - a global "energy" envelope that breathes
 *
 * If you want REAL audio-out amplitude, expose a StateFlow<Float> from
 * OpusStreamPlayer and pass it here as [externalAmplitude] (0f..1f).
 */
@Composable
fun SpeakingVisualizer(externalAmplitude: Float = -1f) {
    val barCount = 20
    // timeMs drives all procedural animation
    var timeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            timeMs = System.currentTimeMillis()
            delay(16L)   // ~60 fps
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawKittBars(
            barCount = barCount,
            timeMs = timeMs,
            externalAmplitude = externalAmplitude
        )
    }
}

private fun DrawScope.drawKittBars(
    barCount: Int,
    timeMs: Long,
    externalAmplitude: Float
) {
    val t = timeMs / 1000f   // seconds

    // ── Layout ──────────────────────────────────────────────────────────────
    val totalSpacing = size.width * 0.06f
    val totalBarWidth = size.width - totalSpacing
    val gap = totalSpacing / (barCount - 1).coerceAtLeast(1)
    val barW = (totalBarWidth / barCount) - gap
    val centerY = size.height / 2f
    val maxH = size.height * 0.42f

    // ── Global energy envelope ──────────────────────────────────────────────
    // If we have a real amplitude feed, use it; otherwise synthesise one.
    val globalEnergy: Float = if (externalAmplitude >= 0f) {
        externalAmplitude.coerceIn(0f, 1f)
    } else {
        // Layered sines → sounds organic, never fully silent
        val e = 0.45f +
                0.25f * sin(t * 2.1f).toFloat() +
                0.15f * sin(t * 5.3f + 1.1f).toFloat() +
                0.15f * sin(t * 0.7f + 2.4f).toFloat()
        e.coerceIn(0.1f, 1f)
    }

    // ── KITT scan wave ───────────────────────────────────────────────────────
    // A cosine peak that bounces left↔right across bars
    val scanPos = (sin(t * 1.4f).toDouble() + 1.0) / 2.0   // 0..1

    for (i in 0 until barCount) {
        val x = i * (barW + gap) + totalSpacing / 2f

        // Distance from scan peak (normalised 0..1)
        val barPos = i.toDouble() / (barCount - 1)
        val dist = abs(barPos - scanPos).toFloat()
        val scanBoost = exp(-dist * dist * 18f)   // Gaussian, tight

        // Per-bar pseudo-noise: each bar has its own frequency mix
        val noise = 0.5f +
                0.3f * sin(t * (3.1f + i * 0.37f)).toFloat() +
                0.2f * sin(t * (7.3f + i * 0.19f) + i).toFloat()

        val heightFactor = (noise * 0.55f + scanBoost * 0.45f) * globalEnergy
        val h = (maxH * heightFactor).coerceAtLeast(4.dp.toPx())

        // ── Colour: cyan core, white-hot tips at high energy ────────────────
        val brightness = heightFactor
        val barColor = lerpColor(
            Color(0xFF00BCD4),    // cool cyan
            Color(0xFFE0FFFF),   // near-white
            brightness.coerceIn(0f, 1f)
        )

        // Glow (draw behind the bar)
        drawRect(
            color = barColor.copy(alpha = 0.18f),
            topLeft = Offset(x - barW * 0.4f, centerY - h * 0.6f),
            size = Size(barW * 1.8f, h * 1.2f)
        )

        // Main bar
        drawRect(
            color = barColor,
            topLeft = Offset(x, centerY - h / 2f),
            size = Size(barW, h)
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
