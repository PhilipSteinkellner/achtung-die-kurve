package com.example.achtungdiekurve.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.settings.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController, viewModel: SettingsViewModel
) {
    val nickname by viewModel.nickname.collectAsState()
    val selectedMode by viewModel.controlMode.collectAsState()

    var localNick by remember { mutableStateOf(nickname) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = localNick,
            onValueChange = { localNick = it },
            label = { Text("Enter Nickname") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        Text("Choose Control Mode", fontSize = 24.sp, modifier = Modifier.padding(bottom = 24.dp))

        Button(
            onClick = { viewModel.setControlMode(ControlMode.TAP) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == ControlMode.TAP) Color.Green else Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Tap to Turn")
        }

        Button(
            onClick = { viewModel.setControlMode(ControlMode.TILT) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == ControlMode.TILT) Color.Green else Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Tilt to Turn")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            viewModel.setNickname(localNick);navController.popBackStack(
            "menu", inclusive = false
        )
        }) {
            Text("Menu")
        }
    }
}

