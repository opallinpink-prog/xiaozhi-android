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
// ChatScreen — visualizer e chat completamente separati
// ---------------------------------------------------------------------------

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    // Collect separati: ogni blocco si recompone indipendentemente
    val deviceState       by viewModel.deviceStateFlow.collectAsState()
    val speakingAmplitude by viewModel.playerAmplitude.collectAsState()
    val micAmplitude      by viewModel.micAmplitude.collectAsState()

    var showChatMessages by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Visualizer: isolato, non risente dei recompose della chat ────────
        DynamicVisualizer(
            deviceState       = deviceState,
            speakingAmplitude = speakingAmplitude,
            micAmplitude      = micAmplitude
        )

        // ── Toggle button in alto a destra ───────────────────────────────────
        IconButton(
            onClick  = { showChatMessages = !showChatMessages },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = if (showChatMessages) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = "Toggle Chat",
                tint = Color.White
            )
        }

        // ── Chat overlay ─────────────────────────────────────────────────────
        if (showChatMessages) {
            // Collect messaggi solo quando la chat è visibile
            val messages by viewModel.display.chatFlow.collectAsState()
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 16.dp),
                reverseLayout  = true
            ) {
                items(messages) { message ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Surface(
                            shape    = MaterialTheme.shapes.medium,
                            color    = if (message.isUser) Color(0xFF1E88E5) else Color(0xFF424242),
                            modifier = Modifier.align(
                                if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
                            )
                        ) {
                            Text(
                                text     = message.content,
                                color    = Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
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
    deviceState:       DeviceState,
    speakingAmplitude: Float = 0f,
    micAmplitude:      Float = 0f
) {
    when (deviceState) {
        DeviceState.LISTENING -> ListeningVisualizer(micAmplitude)
        DeviceState.SPEAKING  -> SpeakingVisualizer(speakingAmplitude)
        else                  -> StandbyVisualizer()
    }
}

// ---------------------------------------------------------------------------
// 1. STANDBY — linea arcobaleno HSV
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
    // Lettura fuori dal Canvas → triggera recompose
    val h = hue; val p = pulse

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cy     = size.height / 2f
        val lineH  = 5.dp.toPx() * p
        val trackW = size.width * 0.88f
        val startX = (size.width - trackW) / 2f
        val N      = 48
        val colors = List(N + 1) { i -> Color.hsv(((h + 360f / N * i) % 360f), 1f, 1f) }
        drawRect(
            brush   = Brush.horizontalGradient(colors, startX, startX + trackW),
            topLeft = Offset(startX, cy - lineH / 2f),
            size    = Size(trackW, lineH)
        )
    }
}

// ---------------------------------------------------------------------------
// 2. LISTENING — tre pallini che pulsano
// ---------------------------------------------------------------------------

@Composable
fun ListeningVisualizer(micAmplitude: Float) {
    val tr = rememberInfiniteTransition(label = "dots")
    val dot0 by tr.animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(600, delayMillis =   0, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d0")
    val dot1 by tr.animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(600, delayMillis = 200, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d1")
    val dot2 by tr.animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d2")

    // micAmplitude arriva gia' come State dal ViewModel, lettura diretta
    val d0 = dot0; val d1 = dot1; val d2 = dot2; val mic = micAmplitude

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx      = size.width  / 2f
        val cy      = size.height / 2f
        val baseR   = 18.dp.toPx()
        val spacing = 60.dp.toPx()
        listOf(d0 to -spacing, d1 to 0f, d2 to spacing).forEach { (scale, dx) ->
            drawCircle(
                color  = Color(0xFFFF1744).copy(alpha = 0.5f + scale * 0.5f),
                radius = baseR * (scale + mic * 0.5f),
                center = Offset(cx + dx, cy)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. SPEAKING — 3 colonne LED stile KITT
// ---------------------------------------------------------------------------

@Composable
fun SpeakingVisualizer(externalAmplitude: Float) {
    // InfiniteTransition garantisce recompose frame-by-frame gestito da Compose
    // Usiamo un float 0..1 che cicla in 4 secondi come "orologio"
    val tr = rememberInfiniteTransition(label = "kitt")
    val clock by tr.animateFloat(
        initialValue  = 0f,
        targetValue   = 4000f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label         = "clock"
    )

    // Smooth dell'ampiezza: aggiornato ogni recompose (ogni frame)
    var smoothedAmp by remember { mutableFloatStateOf(0f) }
    smoothedAmp += (externalAmplitude - smoothedAmp) * 0.18f

    // Lettura fuori dal Canvas per triggerare recompose
    val t   = clock / 1000f   // secondi 0..4, ciclico
    val amp = smoothedAmp

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawLedColumns(t, amp)
    }
}

private val LED_COLORS = listOf(
    Color(0xFF003333),
    Color(0xFF006666),
    Color(0xFF009999),
    Color(0xFF00CCCC),
    Color(0xFF00FFFF),
    Color(0xFFCCFFFF)
)

private fun DrawScope.drawLedColumns(timeMs: Float, smoothedAmp: Float) {
    val t           = timeMs  // già in secondi (0..4)
    val columnCount = 3
    val segmentRows = 24
    val colW        = size.width * 0.18f
    val totalColW   = colW * columnCount
    val colSpacing  = (size.width - totalColW) / (columnCount + 1)
    val maxH        = size.height * 0.80f
    val segH        = maxH / segmentRows
    val segGap      = segH * 0.15f
    val segNet      = segH - segGap
    val topY        = (size.height - maxH) / 2f
    val cr          = CornerRadius(2f)

    val energy = if (smoothedAmp > 0.01f) smoothedAmp.coerceIn(0.1f, 1f)
    else (0.40f + 0.28f * sin(t * 2.1f) + 0.16f * sin(t * 5.3f + 1.1f) +
          0.16f * sin(t * 0.7f + 2.4f)).toFloat().coerceIn(0.15f, 1f)

    for (col in 0 until columnCount) {
        val colX  = colSpacing + col * (colW + colSpacing)
        val phase = col * (PI / 3.0).toFloat()
        val noise = 0.5f + 0.3f * sin(t * 2.5f + phase) + 0.2f * sin(t * 6.1f + phase * 2f)
        val colH  = (segmentRows * noise.coerceIn(0.15f, 1f) * energy).toInt().coerceIn(1, segmentRows)

        for (row in 0 until segmentRows) {
            val segY    = topY + (segmentRows - 1 - row) * segH + segGap / 2f
            val lit     = row < colH
            val frac    = row.toFloat() / (segmentRows - 1)
            val segColor = lerpColorList(LED_COLORS, frac).let { if (lit) it else it.copy(alpha = 0.06f) }
            drawRoundRect(color = segColor, topLeft = Offset(colX, segY), size = Size(colW, segNet), cornerRadius = cr)
        }
    }
}

private fun lerpColorList(colors: List<Color>, t: Float): Color {
    if (t <= 0f) return colors.first()
    if (t >= 1f) return colors.last()
    val scaled = t * (colors.size - 1)
    val idx    = scaled.toInt().coerceIn(0, colors.size - 2)
    return lerpColor(colors[idx], colors[idx + 1], scaled - idx)
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
