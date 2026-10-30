package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.screens.ConfigScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.UpdateScreen
import com.example.ui.theme.MyApplicationTheme

enum class Screen {
    MAIN,
    SETTINGS,
    UPDATES
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val config by viewModel.config.collectAsStateWithLifecycle()

                // Keep screen on if requested in settings
                LaunchedEffect(config.keepScreenOn) {
                    if (config.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Request POST_NOTIFICATIONS on Android 13+ for Foreground Service
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { /* notification permission result handled */ }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                var currentScreen by remember { mutableStateOf(Screen.MAIN) }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen_transition",
                        modifier = Modifier.padding(innerPadding)
                    ) { targetScreen ->
                        when (targetScreen) {
                            Screen.MAIN -> {
                                MainScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                    onNavigateToUpdates = { currentScreen = Screen.UPDATES }
                                )
                            }
                            Screen.SETTINGS -> {
                                ConfigScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.MAIN }
                                )
                            }
                            Screen.UPDATES -> {
                                UpdateScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.MAIN }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
