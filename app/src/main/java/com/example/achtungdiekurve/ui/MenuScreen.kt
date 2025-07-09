package com.example.achtungdiekurve.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.achtungdiekurve.game.GameViewModel

@Composable
fun MenuScreen(
    onStartGame: (Boolean) -> Unit, // Pass true for single player, false for multiplayer
    onReturnToGame: () -> Unit,
    onSettingsClick: () -> Unit,
    gameViewModel: GameViewModel = viewModel()
) {
    val gameState by gameViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        if (gameState.showModeSelection) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Achtung, die Kurve!",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(
                    onClick = {
                        gameViewModel.selectSinglePlayerMode()
                        onStartGame(true)
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Single Player")
                }
                Button(
                    onClick = {
                        gameViewModel.selectMultiplayerMode()
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Multiplayer")
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onSettingsClick, modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Settings")
                }
            }
        } else if (gameState.multiplayerState.showSetupScreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Multiplayer Setup",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = gameState.multiplayerState.connectionStatus,
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (gameState.multiplayerState.connectionStatus !in listOf(
                        "Connecting...", "Waiting for players to join...", "Searching for games..."
                    )
                ) {
                    Button(
                        onClick = { gameViewModel.startHostingGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Host Game")
                    }

                    Button(
                        onClick = { gameViewModel.startJoiningGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Join Game")
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = gameState.multiplayerState.connectionStatus, textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { gameViewModel.resetGameModeSelection() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Menu")
                }
            }
        } else {
            onStartGame(false)
        }
    }
}