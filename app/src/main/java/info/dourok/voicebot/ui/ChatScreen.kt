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
// 1. STANDBY — linea arcobaleno HSV pura, niente blur
// ---------------------------------------------------------------------------

@Composable
fun StandbyVisualizer() {
    val tr = rememberInfiniteTransition(label = "aurora")
    val hue by tr.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label         = "hue"
    )
    val pulse by tr.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cy     = size.height / 2f
        val lineH  = 5.dp.toPx() * pulse
        val trackW = size.width * 0.88f
        val startX = (size.width - trackW) / 2f
        val N      = 48
        val colors = List(N + 1) { i -> Color.hsv(((hue + 360f / N * i) % 360f), 1f, 1f) }

        drawRect(
            brush   = Brush.horizontalGradient(colors, startX, startX + trackW),
            topLeft = Offset(startX, cy - lineH / 2f),
            size    = Size(trackW, lineH)
        )
    }
}

// ---------------------------------------------------------------------------
// 2. LISTENING — tre pallini che pulsano in sequenza (• • •)
// ---------------------------------------------------------------------------

@Composable
fun ListeningVisualizer(micAmplitude: Float) {
    // Smooth dell'ampiezza mic per espandere leggermente i dot quando parli
    var smoothed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { smoothed += (micAmplitude - smoothed) * 0.25f; delay(16) }
    }

    // Tre animazioni sfasate di 200ms l'una → effetto "..." sequenziale
    val tr = rememberInfiniteTransition(label = "dots")
    val dot0 by tr.animateFloat(
        initialValue  = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis =   0, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "d0"
    )
    val dot1 by tr.animateFloat(
        initialValue  = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 200, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "d1"
    )
    val dot2 by tr.animateFloat(
        initialValue  = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "d2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx        = size.width  / 2f
        val cy        = size.height / 2f
        val baseR     = 18.dp.toPx()
        val micBoost  = smoothed * 0.5f          // il mic espande leggermente i dot
        val spacing   = 60.dp.toPx()
        val scales    = listOf(dot0, dot1, dot2)
        val offsets   = listOf(-spacing, 0f, spacing)

        offsets.forEachIndexed { i, dx ->
            val r     = baseR * (scales[i] + micBoost)
            val alpha = 0.5f + scales[i] * 0.5f  // varia da 0.5 a 1.0
            drawCircle(
                color  = Color(0xFFFF1744).copy(alpha = alpha),
                radius = r,
                center = Offset(cx + dx, cy)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. SPEAKING — 3 colonne LED stile KITT/equalizzatore vintage
//    Ogni colonna è fatta di N segmenti separati da gap, con gradiente
//    dal basso (rosso scuro) all'alto (ciano brillante).
//    L'altezza di ogni colonna varia con l'audio + scanner KITT.
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

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawLedColumns(timeMs, smoothedAmp)
    }
}

// Gradiente colori segmenti: dal basso (scuro) verso l'alto (brillante), come KITT
private val LED_COLORS = listOf(
    Color(0xFF003333),   // buio in fondo
    Color(0xFF006666),
    Color(0xFF009999),
    Color(0xFF00CCCC),
    Color(0xFF00FFFF),   // ciano brillante in cima
    Color(0xFFCCFFFF)    // quasi bianco sulle punte
)

private fun DrawScope.drawLedColumns(timeMs: Long, smoothedAmp: Float) {
    val t           = timeMs / 1000f
    val columnCount = 3
    val segmentRows = 24                        // quanti LED per colonna (altezza max)
    val colW        = size.width * 0.18f        // larghezza di ogni colonna
    val totalColW   = colW * columnCount
    val colSpacing  = (size.width - totalColW) / (columnCount + 1)
    val maxH        = size.height * 0.80f       // altezza totale della griglia LED
    val segH        = maxH / segmentRows
    val segGap      = segH * 0.15f             // gap tra segmenti
    val segNet      = segH - segGap            // altezza netta del singolo LED
    val topY        = (size.height - maxH) / 2f
    val cr          = CornerRadius(2f)

    // Energia globale
    val energy = if (smoothedAmp > 0.01f) smoothedAmp.coerceIn(0.1f, 1f)
    else (0.40f + 0.28f * sin(t * 2.1f) + 0.16f * sin(t * 5.3f + 1.1f) +
          0.16f * sin(t * 0.7f + 2.4f)).toFloat().coerceIn(0.15f, 1f)

    for (col in 0 until columnCount) {
        val colX = colSpacing + col * (colW + colSpacing)

        // Ogni colonna ha una fase leggermente diversa → effetto tridimensionale
        val phase  = col * (PI / 3.0).toFloat()
        val noise  = 0.5f + 0.3f * sin(t * 2.5f + phase) + 0.2f * sin(t * 6.1f + phase * 2f)
        val colH   = (segmentRows * noise.coerceIn(0.15f, 1f) * energy).toInt()
                        .coerceIn(1, segmentRows)

        for (row in 0 until segmentRows) {
            // row 0 = in basso, row (segmentRows-1) = in alto
            val segY = topY + (segmentRows - 1 - row) * segH + segGap / 2f

            val lit = row < colH   // questo LED è acceso?

            // Colore: interpolato dal gradiente in base alla posizione verticale
            val colorFraction = row.toFloat() / (segmentRows - 1)  // 0=basso, 1=alto
            val segColor = if (lit) {
                lerpColorList(LED_COLORS, colorFraction)
            } else {
                // LED spento: colore molto scuro (0.06 alpha del colore pieno)
                lerpColorList(LED_COLORS, colorFraction).copy(alpha = 0.06f)
            }

            drawRoundRect(
                color        = segColor,
                topLeft      = Offset(colX, segY),
                size         = Size(colW, segNet),
                cornerRadius = cr
            )
        }
    }
}

// Interpola un colore da una lista di colori (come una colormap)
private fun lerpColorList(colors: List<Color>, t: Float): Color {
    if (t <= 0f) return colors.first()
    if (t >= 1f) return colors.last()
    val scaled = t * (colors.size - 1)
    val idx    = scaled.toInt().coerceIn(0, colors.size - 2)
    val frac   = scaled - idx
    return lerpColor(colors[idx], colors[idx + 1], frac)
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
