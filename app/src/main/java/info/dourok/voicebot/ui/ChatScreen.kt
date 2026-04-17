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
import androidx.compose.runtime.getValue // REQUIRED: Fixes "Property delegate" error
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
    // collectAsState() needs the 'getValue' import to use 'by'
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
            // Visualizer background
            KittVisualizer(deviceState = uiState.deviceState)

            // Chat Overlay
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
                                modifier = Modifier.align(if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart)
                            ) {
                                Text(
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

    // Animate bars when talking/listening
    if (deviceState == DeviceState.SPEAKING || deviceState == DeviceState.LISTENING) {
        animatables.forEachIndexed { index, animatable ->
            LaunchedEffect(deviceState) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(300 + (index * 70)),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = size.height / 2
        if (deviceState == DeviceState.SPEAKING || deviceState == DeviceState.LISTENING) {
            val barWidth = 20.dp.toPx()
            val spacing = 12.dp.toPx()
            val startX = (size.width - ((barWidth + spacing) * barCount)) / 2
            for (i in 0 until barCount) {
                val h = (size.height * 0.4f) * animatables[i].value
                drawRect(Color.Red, Offset(startX + i * (barWidth + spacing), centerY - h / 2), Size(barWidth, h))
            }
        } else {
            val trackWidth = size.width * 0.8f
            val startX = (size.width - trackWidth) / 2
            drawRect(Color.DarkGray, Offset(startX, centerY - 5f), Size(trackWidth, 10f))
            drawRect(Color.Cyan, Offset(startX + (trackWidth - 100f) * scannerOffset, centerY - 5f), Size(100f, 10f))
        }
    }
}
