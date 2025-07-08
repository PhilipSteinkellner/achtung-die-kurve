package com.example.achtungdiekurve.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.game.GameViewModel
import com.example.achtungdiekurve.game.rememberAccelerometerSensorHandler

@Composable
fun CurveGameScreen(
    modifier: Modifier = Modifier,
    onReturnToMenu: () -> Unit,
    controlMode: ControlMode,
    gameViewModel: GameViewModel = viewModel()
) {
    val gameState by gameViewModel.gameState.collectAsState()

    val screenWidth = LocalWindowInfo.current.containerSize.width
    val screenHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current

    // Initialize game positions when screen dimensions are available and game is not running
    // This is called regardless of game mode, as both need initial positions
    LaunchedEffect(screenWidth, screenHeight) {
        if (screenWidth > 0 && screenHeight > 0) {
            gameViewModel.initializeGamePositions(
                with(density) { screenWidth.toFloat() },
                with(density) { screenHeight.toFloat() })
        }
    }

    // Start game loop when conditions are met
    LaunchedEffect(
        gameState.isRunning,
        gameState.isGameOver,
        gameState.localIsAlive,
        gameState.connectedEndpointId,
        gameState.isSinglePlayer
    ) {
        val canStartGameLoop =
            gameState.isRunning && !gameState.isGameOver && gameState.localIsAlive && (gameState.isSinglePlayer || gameState.connectedEndpointId != null)

        if (canStartGameLoop) {
            if (screenWidth > 0 && screenHeight > 0) {
                gameViewModel.startGameLoop(
                    with(density) { screenWidth.toFloat() },
                    with(density) { screenHeight.toFloat() })
            } else {
                Log.w("GameScreen", "Screen dimensions not yet available for game loop.")
            }
        }
    }

    if (controlMode == ControlMode.TILT) {
        rememberAccelerometerSensorHandler { tiltValue ->
            gameViewModel.setLocalTurning(tiltValue)
        }
    }

    DisposableEffect(LocalLifecycleOwner.current) {
        onDispose {
            // No explicit action needed here for ViewModel cleanup as it's lifecycle-managed.
            // gameViewModel.onCleared() is called by the ViewModel system when it's no longer needed.
        }
    }

    // Game UI
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw local player's trail
            for (i in 1 until gameState.localTrail.size) {
                val start = gameState.localTrail[i - 1]
                val end = gameState.localTrail[i]
                val color = when {
                    !gameState.localIsAlive -> Color.Gray
                    gameState.localIsBoosting == 1 -> Color.Magenta
                    else -> if (gameState.isHost) Color.Red else Color.Green
                }

                if (!start.isGap && !end.isGap) {
                    drawLine(
                        color = color,
                        start = start.position,
                        end = end.position,
                        strokeWidth = GameConstants.STROKE_WIDTH
                    )
                }
            }

            // Draw opponent's trail (only in multiplayer)
            if (!gameState.isSinglePlayer) {
                for (i in 1 until gameState.opponentTrail.size) {
                    val start = gameState.opponentTrail[i - 1]
                    val end = gameState.opponentTrail[i]
                    val opponentColor = when {
                        !gameState.opponentIsAlive -> Color.Gray
                        else -> if (gameState.isHost) Color.Green else Color.Red
                    }

                    if (!start.isGap && !end.isGap) {
                        drawLine(
                            color = opponentColor,
                            start = start.position,
                            end = end.position,
                            strokeWidth = GameConstants.STROKE_WIDTH
                        )
                    }
                }
            }
        }

        // Touch input for game controls (only for TAP mode)
        Box(modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (controlMode != ControlMode.TAP || !gameState.isRunning || gameState.isGameOver || !gameState.localIsAlive) return@detectTapGestures
                    // Determine turn direction based on which side of screen was pressed
                    gameViewModel.setLocalTurning(if (it.x < size.width / 2) -1f else 1f)
                    try {
                        awaitRelease() // Keep turning until finger is lifted
                    } finally {
                        gameViewModel.setLocalTurning(0f)
                    }
                })
            }
            .pointerInput(Unit) {
                var totalDragAmount = 0f
                detectVerticalDragGestures(onDragStart = { offset ->
                    // Reset on new drag
                    totalDragAmount = 0f
                }, onVerticalDrag = { change, dragAmount ->
                    totalDragAmount += dragAmount
                    change.consume()
                }, onDragEnd = {
                    if (!gameState.localIsAlive || gameState.localIsBoosting != 0) return@detectVerticalDragGestures

                    if (totalDragAmount < -GameConstants.SWIPE_THRESHOLD) {
                        gameViewModel.toggleBoost()
                    } else if (totalDragAmount > GameConstants.SWIPE_THRESHOLD) {
                        // TODO: activate slow mode
                    }
                    totalDragAmount = 0f // Reset for the next gesture
                }, onDragCancel = {
                    totalDragAmount = 0f
                })
            })


        // Game UI elements (Pause/Reset, Return to Menu)
        Column(
            modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                if (gameState.isGameOver) {
                    gameViewModel.resetGameRound()
                } else {
                    gameViewModel.toggleGameRunning()
                }
            }) {
                Text(if (gameState.isGameOver) "Reset" else if (gameState.isRunning) "Pause" else "Resume")
            }

            if (!gameState.isRunning || gameState.isGameOver) {
                Button(
                    onClick = onReturnToMenu, modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Menu")
                }
            }
        }

        // Connection status indicator (adjusted for single player)
        Text(
            text = if (gameState.isSinglePlayer) "Mode: Single Player" else "Role: ${if (gameState.isHost) "Host (Red)" else "Client (Green)"} | ${gameState.connectionStatus}",
            fontSize = 12.sp,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )

        // Game Over message
        if (gameState.isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = gameState.gameOverMessage,
                    color = Color.Red,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Boost status
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (gameState.localIsAlive) {
                when (gameState.localIsBoosting) {
                    0 -> Text(
                        text = "Boost Ready! (Swipe Up)",
                        color = Color.Green,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    1 -> Text(
                        text = "Boosting...",
                        color = Color.Magenta,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    2 -> Text(
                        text = "Boost ready in ${gameState.localBoostCooldownFrames / 60}s",
                        color = Color.Black,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}