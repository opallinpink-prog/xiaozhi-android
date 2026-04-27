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
import androidx.compose.ui.geometry.CornerRadius
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
            DynamicVisualizer(
                deviceState       = uiState.deviceState,
                speakingAmplitude = uiState.speakingAmplitude,
                micAmplitude      = uiState.micAmplitude
            )
            AnimatedVisibility(visible = showChatMessages, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    reverseLayout  = true
                ) {
                    items(uiState.messages) { message ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Surface(
                                shape    = MaterialTheme.shapes.medium,
                                color    = if (message.isUser) Color(0xFF1E88E5) else Color(0xFF424242),
                                modifier = Modifier.align(if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart)
                            ) {
                                Text(text = message.content, color = Color.White, modifier = Modifier.padding(12.dp))
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
fun DynamicVisualizer(deviceState: DeviceState, speakingAmplitude: Float = 0f, micAmplitude: Float = 0f) {
    when (deviceState) {
        DeviceState.LISTENING -> ListeningVisualizer(micAmplitude)
        DeviceState.SPEAKING  -> SpeakingVisualizer(speakingAmplitude)
        else                  -> StandbyVisualizer()
    }
}

// ---------------------------------------------------------------------------
// Glow helpers — multi-layer radial alpha, zero allocazioni per frame
// ---------------------------------------------------------------------------

/**
 * Glow cerchio: disegna N anelli concentrici con alpha che decade esponenzialmente.
 * Nessun setShadowLayer, nessun Paint allocato per frame → veloce e visivamente morbido.
 */
private fun DrawScope.glowCircle(color: Color, center: Offset, radius: Float, layers: Int = 6) {
    for (i in layers downTo 1) {
        val fraction = i.toFloat() / layers           // 1.0 → 0.17
        val scale    = 1f + (1f - fraction) * 1.4f   // raggio da 1x a 2.4x
        val alpha    = fraction * fraction * 0.35f    // caduta quadratica
        drawCircle(color.copy(alpha = alpha), radius * scale, center)
    }
    drawCircle(color, radius, center) // core solido
}

/**
 * Glow barra: disegna N rettangoli arrotondati concentrici con alpha decrescente.
 */
private fun DrawScope.glowBar(
    color:  Color,
    x:      Float,
    centerY: Float,
    w:      Float,
    h:      Float,
    layers: Int = 5
) {
    val cr = CornerRadius(3f)
    for (i in layers downTo 1) {
        val fraction = i.toFloat() / layers
        val expand   = (1f - fraction) * w * 1.2f
        val alpha    = fraction * fraction * 0.30f
        drawRoundRect(
            color        = color.copy(alpha = alpha),
            topLeft      = Offset(x - expand / 2f, centerY - (h + expand) / 2f),
            size         = Size(w + expand, h + expand),
            cornerRadius = cr
        )
    }
    drawRoundRect(color = color, topLeft = Offset(x, centerY - h / 2f), size = Size(w, h), cornerRadius = cr)
}

// ---------------------------------------------------------------------------
// 1. STANDBY — aurora HSV sweep
// ---------------------------------------------------------------------------

@Composable
fun StandbyVisualizer() {
    val tr = rememberInfiniteTransition(label = "aurora")
    val hue by tr.animateFloat(
        initialValue  = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "hue"
    )
    val pulse by tr.animateFloat(
        initialValue  = 0.7f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cy     = size.height / 2f
        val lineH  = 5.dp.toPx() * pulse
        val trackW = size.width * 0.88f
        val startX = (size.width - trackW) / 2f
        val N      = 48
        val colors = List(N + 1) { i -> Color.hsv(((hue + 360f / N * i) % 360f), 0.9f, 1f) }

        // Alone largo
        drawRect(
            brush   = Brush.horizontalGradient(colors.map { it.copy(alpha = 0.08f) }, startX, startX + trackW),
            topLeft = Offset(startX, cy - lineH * 6f), size = Size(trackW, lineH * 12f)
        )
        // Alone medio
        drawRect(
            brush   = Brush.horizontalGradient(colors.map { it.copy(alpha = 0.22f) }, startX, startX + trackW),
            topLeft = Offset(startX, cy - lineH * 2.5f), size = Size(trackW, lineH * 5f)
        )
        // Linea solida
        drawRect(
            brush   = Brush.horizontalGradient(colors, startX, startX + trackW),
            topLeft = Offset(startX, cy - lineH / 2f), size = Size(trackW, lineH)
        )
    }
}

// ---------------------------------------------------------------------------
// 2. LISTENING — cerchio rosso con glow morbido reattivo al mic
// ---------------------------------------------------------------------------

@Composable
fun ListeningVisualizer(micAmplitude: Float) {
    var smoothed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { smoothed += (micAmplitude - smoothed) * 0.2f; delay(16) } }

    val tr = rememberInfiniteTransition(label = "listen")
    val basePulse by tr.animateFloat(
        initialValue  = 0.88f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx     = size.width  / 2f
        val cy     = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.14f * (basePulse + smoothed * 0.6f)
        glowCircle(Color(0xFFFF1744), Offset(cx, cy), radius, layers = 7)
        // Highlight speculare
        drawCircle(Color.White.copy(alpha = 0.22f), radius * 0.35f, Offset(cx - radius * 0.2f, cy - radius * 0.2f))
    }
}

// ---------------------------------------------------------------------------
// 3. SPEAKING — barre ciano stile KITT con glow morbido
// ---------------------------------------------------------------------------

@Composable
fun SpeakingVisualizer(externalAmplitude: Float) {
    var smoothedAmp by remember { mutableFloatStateOf(0f) }
    var timeMs      by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            timeMs      = System.currentTimeMillis()
            smoothedAmp += (externalAmplitude - smoothedAmp) * 0.18f
            delay(16)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) { drawKittBars(timeMs, smoothedAmp) }
}

private fun DrawScope.drawKittBars(timeMs: Long, smoothedAmp: Float) {
    val t        = timeMs / 1000f
    val barCount = 20
    val totalPad = size.width * 0.06f
    val gap      = totalPad / (barCount - 1).coerceAtLeast(1)
    val barW     = (size.width - totalPad) / barCount - gap
    val centerY  = size.height / 2f
    val maxH     = size.height * 0.44f

    // Energia globale: reale se arriva, procedurale come fallback
    val energy = if (smoothedAmp > 0.01f) smoothedAmp.coerceIn(0.1f, 1f)
    else (0.40f + 0.28f * sin(t * 2.1f) + 0.16f * sin(t * 5.3f + 1.1f) + 0.16f * sin(t * 0.7f + 2.4f))
            .toFloat().coerceIn(0.15f, 1f)

    val scanPos = (sin(t * 1.5f) + 1.0) / 2.0  // 0..1, rimbalza L↔R

    for (i in 0 until barCount) {
        val x      = totalPad / 2f + i * (barW + gap)
        val barPos = i.toDouble() / (barCount - 1)
        val scan   = exp(-(barPos - scanPos).pow(2.0) * 16.0).toFloat()
        val noise  = 0.5f + 0.3f * sin(t * (3f + i * 0.4f)) + 0.2f * sin(t * (7.1f + i * 0.2f) + i * 0.5f)
        val hf     = ((noise * 0.5f + scan * 0.5f) * energy).coerceIn(0f, 1f)
        val h      = (maxH * hf).coerceAtLeast(3.dp.toPx())
        val color  = lerpColor(Color(0xFF00BCD4), Color(0xFFCCFFFE), hf)

        glowBar(color, x, centerY, barW, h, layers = 5)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
