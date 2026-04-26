package info.dourok.voicebot.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
                deviceState      = uiState.deviceState,
                speakingAmplitude = uiState.speakingAmplitude,
                micAmplitude     = uiState.micAmplitude
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
// Router
// ---------------------------------------------------------------------------

@Composable
fun DynamicVisualizer(
    deviceState: DeviceState,
    speakingAmplitude: Float = 0f,
    micAmplitude: Float = 0f
) {
    when (deviceState) {
        DeviceState.LISTENING -> ListeningVisualizer(micAmplitude = micAmplitude)
        DeviceState.SPEAKING  -> SpeakingVisualizer(externalAmplitude = speakingAmplitude)
        else                  -> StandbyVisualizer()
    }
}

// ---------------------------------------------------------------------------
// 1. STANDBY — aurora HSV sweep
// ---------------------------------------------------------------------------

@Composable
fun StandbyVisualizer() {
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
        val steps = 32
        val colors = List(steps + 1) { i ->
            Color.hsv(((hueOffset + (360f / steps) * i) % 360f), 0.85f, 1f)
        }
        val brush = Brush.horizontalGradient(colors, startX, startX + trackWidth)
        drawRect(brush, Offset(startX, centerY - lineHeight / 2f), Size(trackWidth, lineHeight))
        val glowBrush = Brush.horizontalGradient(
            colors.map { it.copy(alpha = 0.18f) }, startX, startX + trackWidth
        )
        drawRect(glowBrush, Offset(startX, centerY - lineHeight * 3f), Size(trackWidth, lineHeight * 6f))
    }
}

// ---------------------------------------------------------------------------
// 2. LISTENING — cerchio rosso che reagisce al mic
//    L'ampiezza arriva dal ViewModel (no secondo AudioRecord!)
// ---------------------------------------------------------------------------

@Composable
fun ListeningVisualizer(micAmplitude: Float) {
    // Smooth del valore in entrata per evitare jitter
    var smoothed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            smoothed = smoothed + (micAmplitude - smoothed) * 0.25f
            delay(16L)
        }
    }

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
        val baseRadius = minOf(size.width, size.height) * 0.13f
        val radius = baseRadius * (basePulse + smoothed * 0.55f)

        drawCircle(
            color = Color.Red.copy(alpha = 0.15f + smoothed * 0.25f),
            radius = radius * 1.55f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.Red.copy(alpha = 0.3f + smoothed * 0.2f),
            radius = radius * 1.2f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFFFF1744),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = radius * 0.38f,
            center = Offset(cx - radius * 0.22f, cy - radius * 0.22f)
        )
    }
}

// ---------------------------------------------------------------------------
// 3. SPEAKING — barre ciano stile KITT
// ---------------------------------------------------------------------------

@Composable
fun SpeakingVisualizer(externalAmplitude: Float) {
    val barCount = 20
    var timeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            timeMs = System.currentTimeMillis()
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawKittBars(barCount, timeMs, externalAmplitude)
    }
}

private fun DrawScope.drawKittBars(barCount: Int, timeMs: Long, externalAmplitude: Float) {
    val t = timeMs / 1000f
    val totalSpacing = size.width * 0.06f
    val totalBarWidth = size.width - totalSpacing
    val gap = totalSpacing / (barCount - 1).coerceAtLeast(1)
    val barW = (totalBarWidth / barCount) - gap
    val centerY = size.height / 2f
    val maxH = size.height * 0.42f

    val globalEnergy: Float = if (externalAmplitude > 0f) {
        externalAmplitude.coerceIn(0f, 1f)
    } else {
        // Fallback procedurale quando l'ampiezza reale è 0
        val e = 0.45f +
                0.25f * sin(t * 2.1f).toFloat() +
                0.15f * sin(t * 5.3f + 1.1f).toFloat() +
                0.15f * sin(t * 0.7f + 2.4f).toFloat()
        e.coerceIn(0.1f, 1f)
    }

    val scanPos = (sin(t * 1.4f).toDouble() + 1.0) / 2.0

    for (i in 0 until barCount) {
        val x = i * (barW + gap) + totalSpacing / 2f
        val barPos = i.toDouble() / (barCount - 1)
        val dist = abs(barPos - scanPos).toFloat()
        val scanBoost = exp(-dist * dist * 18f)
        val noise = 0.5f +
                0.3f * sin(t * (3.1f + i * 0.37f)).toFloat() +
                0.2f * sin(t * (7.3f + i * 0.19f) + i).toFloat()
        val heightFactor = (noise * 0.55f + scanBoost * 0.45f) * globalEnergy
        val h = (maxH * heightFactor).coerceAtLeast(4.dp.toPx())
        val brightness = heightFactor.coerceIn(0f, 1f)
        val barColor = lerpColor(Color(0xFF00BCD4), Color(0xFFE0FFFF), brightness)

        drawRect(
            color = barColor.copy(alpha = 0.18f),
            topLeft = Offset(x - barW * 0.4f, centerY - h * 0.6f),
            size = Size(barW * 1.8f, h * 1.2f)
        )
        drawRect(
            color = barColor,
            topLeft = Offset(x, centerY - h / 2f),
            size = Size(barW, h)
        )
    }
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
