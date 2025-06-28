package com.example.achtungdiekurve.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuScreen(onStartClick: () -> Unit, onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Achtung, die Kurve!",
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(onClick = onStartClick) {
                Text("Start Game")
            }
            Button(onClick = onSettingsClick) {
                Text("Settings")
            }
        }
    }
}