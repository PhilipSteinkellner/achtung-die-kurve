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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.achtungdiekurve.game.GameViewModel

@Composable
fun MenuScreen(
    gameViewModel: GameViewModel, navController: NavController
) {

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
                text = "Achtung, die Kurve!",
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = {
                    gameViewModel.selectSinglePlayerMode()
                    navController.navigate("game")
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Singleplayer")
            }
            Button(
                onClick = {
                    gameViewModel.selectMultiplayerMode()
                    navController.navigate("multiplayer_setup")
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Multiplayer")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { navController.navigate("settings") }, modifier = Modifier.fillMaxWidth()
            ) {
                Text("Settings")
            }
        }

    }
}