package info.dourok.voicebot

import android.Manifest
import android.app.Activity
import android.content.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import info.dourok.voicebot.ui.ActivationScreen
import info.dourok.voicebot.ui.ChatScreen
import info.dourok.voicebot.ui.ServerFormScreen
import info.dourok.voicebot.ui.theme.VoicebotclientandroidTheme
import info.dourok.voicebot.viewmodel.ChatViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                0
            )
        }
        
        enableEdgeToEdge()
        setContent {
            VoicebotclientandroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
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
    val entryPoint = EntryPointAccessors.fromActivity(activity, NavigationEntryPoint::class.java)
    val navigationEvents = entryPoint.getNavigationEvents()

    LaunchedEffect(navController) {
        navigationEvents.collect { route ->
            navController.navigate(route)
        }
    }

    NavHost(navController = navController, startDestination = "form") {
        composable("form") { ServerFormScreen() }
        composable("activation") { ActivationScreen() }
        composable("chat") { 
            // Correctly passing the injected ViewModel to ChatScreen
            val chatViewModel: ChatViewModel = hiltViewModel()
            ChatScreen(viewModel = chatViewModel) 
        }
    }
}
