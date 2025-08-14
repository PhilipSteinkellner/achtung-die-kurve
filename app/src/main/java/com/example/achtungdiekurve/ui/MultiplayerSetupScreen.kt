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
import androidx.compose.material3.ButtonDefaults
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
import androidx.navigation.NavController
import com.example.achtungdiekurve.data.MatchState
import com.example.achtungdiekurve.game.GameViewModel
import com.example.achtungdiekurve.settings.SettingsViewModel

@Composable
fun MultiplayerSetupScreen(
    navController: NavController, gameViewModel: GameViewModel, settingsViewModel: SettingsViewModel
) {
    val gameState by gameViewModel.gameState.collectAsState()
    val nickname by settingsViewModel.nickname.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
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

            if (gameState.multiplayerState.searching) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (gameState.multiplayerState.isHost && gameState.multiplayerState.connectedEndpoints.isNotEmpty()) {
                Text(
                    text = "Connected endpoints: " + gameState.multiplayerState.connectedEndpoints,
                    textAlign = TextAlign.Center
                )
            }

            if (gameState.multiplayerState.isHost) {
                Button(
                    onClick = {
                        navController.navigate("game")
                        gameViewModel.changeMatchState(
                            MatchState.MATCH_SETTINGS
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green, contentColor = Color.Black
                    )
                ) {
                    Text("Start Game")
                }
            } else {
                if (gameState.matchState == MatchState.MATCH_SETTINGS) {
                    navController.navigate("game")
                }
            }

            Button(
                onClick = { gameViewModel.startHostingGame(nickname) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Host Game")
            }

            Button(
                onClick = { gameViewModel.startJoiningGame(nickname) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Join Game")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { gameViewModel.resetGameModeSelection();navController.navigate("menu") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Menu")
            }
        }
    }
}
