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
import info.dourok.voicebot.state.DeviceState

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
                                // Corrected property check based on common VoiceBot models
                                color = if (message.isUser) Color(0xFF1E88E5) else Color(0xFF424242),
                                modifier = Modifier.align(if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart)
                            ) {
                                Text(
                                    // Corrected: Uses .content or .text depending on your Message class
                                    text = message.content,
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

@Composable
fun KittVisualizer(deviceState: DeviceState) {
    val infiniteTransition = rememberInfiniteTransition(label = "KITT")

    val scannerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Scanner"
    )

    val barCount = 10
    val animatables = remember { List(barCount) { Animatable(0.1f) } }

    if (deviceState == DeviceState.SPEAKING || deviceState == DeviceState.LISTENING) {
        animatables.forEachIndexed { index, animatable ->
            LaunchedEffect(deviceState) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 300 + (index * 70)),
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
            val barWidth = 20.dp.toPx()
            val spacing = 12.dp.toPx()
            val totalWidth = (barWidth + spacing) * barCount
            val startX = (width - totalWidth) / 2

            for (i in 0 until barCount) {
                val currentBarHeight = (height * 0.4f) * animatables[i].value
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(startX + i * (barWidth + spacing), centerY - currentBarHeight / 2),
                    size = Size(barWidth, currentBarHeight)
                )
            }
        } else {
            val trackWidth = width * 0.85f
            val trackHeight = 10.dp.toPx()
            val startX = (width - trackWidth) / 2
            
            drawRect(
                color = Color(0xFF212121),
                topLeft = Offset(startX, centerY - trackHeight / 2),
                size = Size(trackWidth, trackHeight)
            )

            val scannerWidth = trackWidth * 0.25f
            val currentX = startX + (trackWidth - scannerWidth) * scannerOffset
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(currentX, centerY - trackHeight / 2),
                size = Size(scannerWidth, trackHeight)
            )
        }
    }
}
