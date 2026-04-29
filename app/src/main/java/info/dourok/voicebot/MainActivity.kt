package info.dourok.voicebot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import info.dourok.voicebot.ui.ActivationScreen
import info.dourok.voicebot.ui.ChatScreen
import info.dourok.voicebot.ui.ServerFormScreen
import info.dourok.voicebot.ui.theme.VoicebotclientandroidTheme
import info.dourok.voicebot.ui.ChatViewModel

private const val PREFS_NAME = "companionai_prefs"
private const val KEY_SERVER_TYPE = "server_type"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }

        enableEdgeToEdge()
        setContent {
            VoicebotclientandroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val activity = LocalContext.current as Activity
    val context = LocalContext.current

    val entryPoint = EntryPointAccessors.fromActivity(activity, NavigationEntryPoint::class.java)
    val navigationEvents = entryPoint.getNavigationEvents()

    LaunchedEffect(navController) {
        navigationEvents.collect { route ->
            navController.navigate(route)
        }
    }

    // Se esiste già una configurazione salvata, salta il form e vai alla chat
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val startDestination = if (prefs.contains(KEY_SERVER_TYPE)) "chat" else "form"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("form") { ServerFormScreen() }
        composable("activation") { ActivationScreen() }
        composable("chat") {
            val chatViewModel: ChatViewModel = hiltViewModel()
            ChatScreen(viewModel = chatViewModel)
        }
    }
}
