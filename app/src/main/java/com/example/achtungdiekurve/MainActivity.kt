package com.example.achtungdiekurve

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.game.GameViewModel
import com.example.achtungdiekurve.ui.CurveGameScreen
import com.example.achtungdiekurve.ui.MenuScreen
import com.example.achtungdiekurve.ui.SettingsScreen
import com.example.achtungdiekurve.ui.theme.AchtungDieKurveTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()

        setContent {
            AchtungDieKurveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CurveApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions.launch(missingPermissions.toTypedArray())
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }
}

@Composable
fun CurveApp(modifier: Modifier) {
    val navController = rememberNavController()
    var controlMode by rememberSaveable { mutableStateOf(ControlMode.TAP) }
    val gameViewModel: GameViewModel = viewModel() // Obtain ViewModel at a higher scope

    NavHost(navController, startDestination = "menu") {
        composable("menu") {
            MenuScreen(
                onStartGame = { isSinglePlayer ->
                    // The game mode is already set in the ViewModel by MenuScreen itself.
                    // We just navigate to the game screen.
                    navController.navigate("game")
                },
                onReturnToGame = {
                    navController.navigate("game") {
                        // Pop up to game to avoid multiple instances on back stack
                        popUpTo("game") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSettingsClick = { navController.navigate("settings") },
                gameViewModel = gameViewModel // Pass the ViewModel
            )
        }
        composable("game") {
            CurveGameScreen(
                modifier = modifier,
                onReturnToMenu = {
                    // When returning to menu, ensure the GameViewModel is reset
                    // to show mode selection again if navigating back to game.
                    gameViewModel.resetGameModeSelection()
                    navController.popBackStack("menu", inclusive = false)
                },
                controlMode = controlMode,
                gameViewModel = gameViewModel // Pass the ViewModel to the UI
            )
        }
        composable("settings") {
            SettingsScreen(selectedMode = controlMode, onSelectMode = { mode ->
                controlMode = mode
            }, onReturnToMenu = { navController.popBackStack("menu", inclusive = false) })
        }
    }
}