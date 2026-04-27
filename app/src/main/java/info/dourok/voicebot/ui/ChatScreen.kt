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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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

            AnimatedVisibility(
                visible  = showChatMessages,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    reverseLayout  = true
                ) {
                    items(uiState.messages) { message ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape    = MaterialTheme.shapes.medium,
                                color    = if (message.isUser) Color(0xFF1E88E5) else Color(0xFF424242),
                                modifier = Modifier.align(
                                    if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
                                )
                            ) {
                                Text(
                                    text     = message.content as String,
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
        DeviceState.LISTENING -> ListeningVisualizer(micAmplitude = micAmplitude)
        DeviceState.SPEAKING  -> SpeakingVisualizer(externalAmplitude = speakingAmplitude)
        else                  -> StandbyVisualizer()
    }
}

// ---------------------------------------------------------------------------
// Glow helpers — usano setShadowLayer sul nativeCanvas di Android
// ---------------------------------------------------------------------------

/**
 * Disegna un rettangolo arrotondato con glow gaussiano morbido.
 * setShadowLayer è l'unico modo affidabile per avere blur reale in Compose Canvas.
 */
private fun DrawScope.drawGlowRect(
    color:        Color,
    topLeft:      Offset,
    rectSize:     Size,
    glowRadius:   Dp,
    glowAlpha:    Float = 0.55f,
    cornerRadius: Float = 4f
) {
    val glowColor = color.copy(alpha = glowAlpha).toArgb()
    val solidColor = color.toArgb()
    val cr = cornerRadius
    val l = topLeft.x;  val t = topLeft.y
    val r = l + rectSize.width;  val b = t + rectSize.height
    val blurPx = glowRadius.toPx()

    drawIntoCanvas { canvas ->
        // Paint glow (trasparente con shadow)
        val glowPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color  = android.graphics.Color.TRANSPARENT
            setShadowLayer(blurPx, 0f, 0f, glowColor)
        }
        // Paint solido (nessuna shadow)
        val solidPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color  = solidColor
        }
        val rect = android.graphics.RectF(l, t, r, b)
        canvas.nativeCanvas.drawRoundRect(rect, cr, cr, glowPaint)
        canvas.nativeCanvas.drawRoundRect(rect, cr, cr, solidPaint)
    }
}

/**
 * Disegna un cerchio con glow gaussiano morbido.
 */
private fun DrawScope.drawGlowCircle(
    color:      Color,
    center:     Offset,
    radius:     Float,
    glowRadius: Dp,
    glowAlpha:  Float = 0.5f
) {
    val glowColor  = color.copy(alpha = glowAlpha).toArgb()
    val solidColor = color.toArgb()
    val blurPx     = glowRadius.toPx()

    drawIntoCanvas { canvas ->
        val glowPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color  = android.graphics.Color.TRANSPARENT
            setShadowLayer(blurPx, 0f, 0f, glowColor)
        }
        val solidPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color  = solidColor
        }
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, glowPaint)
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, solidPaint)
    }
}

// ---------------------------------------------------------------------------
// 1. STANDBY — aurora HSV sweep con glow sfumato
// ---------------------------------------------------------------------------

@Composable
fun StandbyVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val hueOffset by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY    = size.height / 2f
        val lineH      = 5.dp.toPx() * pulse
        val trackWidth = size.width * 0.88f
        val startX     = (size.width - trackWidth) / 2f
        val steps      = 48

        val colors = List(steps + 1) { i ->
            Color.hsv(((hueOffset + (360f / steps) * i) % 360f), 0.9f, 1f)
        }

        // Glow largo (bassa opacità, altezza ampia)
        drawRect(
            brush   = Brush.horizontalGradient(colors.map { it.copy(alpha = 0.10f) }, startX, startX + trackWidth),
            topLeft = Offset(startX, centerY - lineH * 5f),
            size    = Size(trackWidth, lineH * 10f)
        )
        // Glow medio
        drawRect(
            brush   = Brush.horizontalGradient(colors.map { it.copy(alpha = 0.28f) }, startX, startX + trackWidth),
            topLeft = Offset(startX, centerY - lineH * 2.5f),
            size    = Size(trackWidth, lineH * 5f)
        )
        // Linea solida
        drawRect(
            brush   = Brush.horizontalGradient(colors, startX, startX + trackWidth),
            topLeft = Offset(startX, centerY - lineH / 2f),
            size    = Size(trackWidth, lineH)
        )
    }
}

// ---------------------------------------------------------------------------
// 2. LISTENING — cerchio rosso con glow gaussiano reale
// ---------------------------------------------------------------------------

@Composable
fun ListeningVisualizer(micAmplitude: Float) {
    var smoothed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            smoothed = smoothed + (micAmplitude - smoothed) * 0.2f
            delay(16L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "listen")
    val basePulse by infiniteTransition.animateFloat(
        initialValue  = 0.88f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "basePulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx         = size.width  / 2f
        val cy         = size.height / 2f
        val baseRadius = minOf(size.width, size.height) * 0.14f
        val radius     = baseRadius * (basePulse + smoothed * 0.6f)
        val center     = Offset(cx, cy)

        // Glow esterno morbido
        drawGlowCircle(Color(0xFFFF1744).copy(alpha = 0.2f + smoothed * 0.2f), center, radius * 1.6f, 36.dp, 0.3f)
        // Glow medio
        drawGlowCircle(Color(0xFFFF1744).copy(alpha = 0.5f + smoothed * 0.2f), center, radius * 1.15f, 20.dp, 0.6f)
        // Core con glow stretto e intenso
        drawGlowCircle(Color(0xFFFF1744), center, radius, 12.dp, 0.95f)
        // Highlight bianco
        drawCircle(
            color  = Color.White.copy(alpha = 0.28f),
            radius = radius * 0.36f,
            center = Offset(cx - radius * 0.2f, cy - radius * 0.2f)
        )
    }
}

// ---------------------------------------------------------------------------
// 3. SPEAKING — barre ciano stile KITT con glow gaussiano reale
// ---------------------------------------------------------------------------

@Composable
fun SpeakingVisualizer(externalAmplitude: Float) {
    var smoothedAmp by remember { mutableFloatStateOf(0f) }
    var timeMs      by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            timeMs      = System.currentTimeMillis()
            smoothedAmp = smoothedAmp + (externalAmplitude - smoothedAmp) * 0.18f
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawKittBars(timeMs, smoothedAmp)
    }
}

private fun DrawScope.drawKittBars(timeMs: Long, smoothedAmp: Float) {
    val t        = timeMs / 1000f
    val barCount = 20
    val totalPad = size.width * 0.06f
    val gap      = totalPad / (barCount - 1).coerceAtLeast(1)
    val barW     = (size.width - totalPad) / barCount - gap
    val centerY  = size.height / 2f
    val maxH     = size.height * 0.44f

    // Energia globale: reale se disponibile, altrimenti procedurale
    val globalEnergy = if (smoothedAmp > 0.01f) {
        smoothedAmp.coerceIn(0.1f, 1f)
    } else {
        (0.40f +
         0.28f * sin(t * 2.1f).toFloat() +
         0.16f * sin(t * 5.3f + 1.1f).toFloat() +
         0.16f * sin(t * 0.7f + 2.4f).toFloat()).coerceIn(0.15f, 1f)
    }

    // Picco gaussiano che scansiona L↔R (KITT)
    val scanPos = (sin(t * 1.5f).toDouble() + 1.0) / 2.0

    for (i in 0 until barCount) {
        val x      = totalPad / 2f + i * (barW + gap)
        val barPos = i.toDouble() / (barCount - 1)
        val scan   = exp(-((barPos - scanPos) * (barPos - scanPos) * 16.0)).toFloat()
        val noise  = 0.5f +
                     0.3f * sin(t * (3.0f + i * 0.4f)).toFloat() +
                     0.2f * sin(t * (7.1f + i * 0.2f) + i * 0.5f).toFloat()

        val hf       = (noise * 0.5f + scan * 0.5f) * globalEnergy
        val h        = (maxH * hf).coerceAtLeast(3.dp.toPx())
        val bright   = hf.coerceIn(0f, 1f)
        val barColor = lerpColor(Color(0xFF00BCD4), Color(0xFFCCFFFE), bright)

        drawGlowRect(
            color        = barColor,
            topLeft      = Offset(x, centerY - h / 2f),
            rectSize     = Size(barW, h),
            glowRadius   = (5f + bright * 14f).dp,
            glowAlpha    = 0.45f + bright * 0.4f,
            cornerRadius = 3f
        )
    }
}

// ---------------------------------------------------------------------------
// Helper colore
// ---------------------------------------------------------------------------

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
