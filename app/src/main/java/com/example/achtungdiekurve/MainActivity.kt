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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.achtungdiekurve.data.SettingsRepository
import com.example.achtungdiekurve.game.GameViewModel
import com.example.achtungdiekurve.settings.SettingsViewModel
import com.example.achtungdiekurve.settings.SettingsViewModelFactory
import com.example.achtungdiekurve.ui.CurveGameScreen
import com.example.achtungdiekurve.ui.MenuScreen
import com.example.achtungdiekurve.ui.MultiplayerSetupScreen
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
    val gameViewModel: GameViewModel = viewModel()
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(repo))


    NavHost(navController, startDestination = "menu") {
        composable("menu") {
            MenuScreen(
                navController = navController, gameViewModel = gameViewModel
            )
        }
        composable("multiplayer_setup") {
            MultiplayerSetupScreen(
                navController = navController,
                gameViewModel = gameViewModel,
                settingsViewModel = settingsViewModel,
            )
        }
        composable("game") {
            CurveGameScreen(
                modifier = modifier,
                settingsViewModel = settingsViewModel,
                gameViewModel = gameViewModel,
                navController = navController,
            )
        }
        composable("settings") {
            SettingsScreen(navController = navController, viewModel = settingsViewModel)
        }
    }
}