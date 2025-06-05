package com.example.achtungdiekurve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.achtungdiekurve.ui.CurveApp
import com.example.achtungdiekurve.ui.theme.AchtungDieKurveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AchtungDieKurveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CurveApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

