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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.achtungdiekurve.data.ControlMode

@Composable
fun SettingsScreen(
    selectedMode: ControlMode, onSelectMode: (ControlMode) -> Unit, onReturnToMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Choose Control Mode", fontSize = 24.sp, modifier = Modifier.padding(bottom = 24.dp))

        Button(
            onClick = { onSelectMode(ControlMode.TAP) }, colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == ControlMode.TAP) Color.Green else Color.Gray
            ), modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Tap to Turn")
        }

        Button(
            onClick = { onSelectMode(ControlMode.TILT) }, colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == ControlMode.TILT) Color.Green else Color.Gray
            ), modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Tilt to Turn")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onReturnToMenu) {
            Text("Menu")
        }
    }
}

