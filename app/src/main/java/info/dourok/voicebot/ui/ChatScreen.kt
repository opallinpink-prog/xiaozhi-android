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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.dourok.voicebot.viewmodel.ChatViewModel
import info.dourok.voicebot.state.DeviceState // Assuming State enum location

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showChatMessages by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xiaozhi Assistant") },
                actions = {
                    IconButton(onClick = { showChatMessages = !showChatMessages }) {
                        Icon(
                            imageVector = if (showChatMessages) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Chat"
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
                .background(Color.Black) // KITT style background
        ) {
            // 1. Full Screen Visualizer (Bottom Layer)
            KittVisualizer(deviceState = uiState.deviceState)

            // 2. Chat Messages (Top Layer)
            AnimatedVisibility(
                visible = showChatMessages,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    reverseLayout = true // Keeping latest messages at bottom
                ) {
                    items(uiState.messages) { message ->
                        ChatMessageRow(message)
                    }
                }
            }
        }
    }
}

@Composable
fun KittVisualizer(deviceState: DeviceState) {
    val infiniteTransition = rememberInfiniteTransition(label = "KITT")

    // Scanner Animation (Idle)
    val scannerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Scanner"
    )

    // Voice Bars Animation (Speaking/Listening)
    val barCount = 12
    val animatables = remember { List(barCount) { Animatable(0.2f) } }

    if (deviceState == DeviceState.SPEAKING || deviceState == DeviceState.LISTENING) {
        animatables.forEachIndexed { index, animatable ->
            LaunchedEffect(deviceState) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 400 + (index * 50),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (deviceState == DeviceState.SPEAKING || deviceState == DeviceState.LISTENING) {
            // --- AUDIO VISUALIZER MODE ---
            val barWidth = 15.dp.toPx()
            val spacing = 10.dp.toPx()
            val totalWidth = (barWidth + spacing) * barCount
            val startX = (width - totalWidth) / 2

            for (i in 0 until barCount) {
                val barHeight = (height * 0.3f) * animatables[i].value
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(startX + i * (barWidth + spacing), centerY - barHeight / 2),
                    size = Size(barWidth, barHeight)
                )
            }
        } else {
            // --- KITT IDLE SCANNER MODE ---
            val trackWidth = width * 0.8f
            val trackHeight = 8.dp.toPx()
            val startX = (width - trackWidth) / 2
            
            // Draw background track
            drawRect(
                color = Color.DarkGray,
                topLeft = Offset(startX, centerY - trackHeight / 2),
                size = Size(trackWidth, trackHeight)
            )

            // Draw moving scanner head
            val scannerWidth = trackWidth * 0.2f
            val currentX = startX + (trackWidth - scannerWidth) * scannerOffset
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(currentX, centerY - trackHeight / 2),
                size = Size(scannerWidth, trackHeight)
            )
        }
    }
}
