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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.achtungdiekurve.data.BoostState
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.data.PlayerUiState
import com.example.achtungdiekurve.game.GameViewModel
import com.example.achtungdiekurve.game.rememberAccelerometerSensorHandler

@Composable
fun CurveGameScreen(
    modifier: Modifier = Modifier,
    onReturnToMenu: () -> Unit,
    controlMode: ControlMode,
    gameViewModel: GameViewModel = viewModel()
) {
    val uiState by gameViewModel.uiState.collectAsState()
    val localPlayer = uiState.localPlayer
    val opponentPlayer = uiState.opponentPlayer
    val multiplayerState = uiState.multiplayerState

    val screenWidth = LocalWindowInfo.current.containerSize.width
    val screenHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current

    LaunchedEffect(screenWidth, screenHeight) {
        if (screenWidth > 0 && screenHeight > 0) {
            gameViewModel.initializeGamePositions(
                with(density) { screenWidth.toFloat() },
                with(density) { screenHeight.toFloat() })
        }
    }

    LaunchedEffect(
        uiState.isRunning,
        uiState.isGameOver,
        localPlayer.isAlive,
        multiplayerState.isMultiplayer
    ) {
        val canStartGameLoop = uiState.isRunning && !uiState.isGameOver && localPlayer.isAlive

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                controlMode,
                uiState.isRunning,
                uiState.isGameOver,
                localPlayer.isAlive,
                localPlayer.boostState
            ) {
                detectTapGestures(onPress = {
                    if (controlMode != ControlMode.TAP || !uiState.isRunning || uiState.isGameOver || !localPlayer.isAlive) return@detectTapGestures
                    gameViewModel.setLocalTurning(if (it.x < size.width / 2) -1f else 1f)
                    try {
                        awaitRelease()
                    } finally {
                        gameViewModel.setLocalTurning(0f)
                    }
                })
            }
            .pointerInput(localPlayer.isAlive, localPlayer.boostState) {
                var totalDragAmount = 0f
                detectVerticalDragGestures(onDragStart = { offset ->
                    // Reset on new drag
                    totalDragAmount = 0f
                }, onVerticalDrag = { change, dragAmount ->
                    totalDragAmount += dragAmount
                    change.consume()
                }, onDragEnd = {
                    if (!localPlayer.isAlive || localPlayer.boostState != BoostState.READY) return@detectVerticalDragGestures

                    if (totalDragAmount < -GameConstants.SWIPE_THRESHOLD) {
                        gameViewModel.toggleBoost()
                    } else if (totalDragAmount > GameConstants.SWIPE_THRESHOLD) {
                        // TODO: activate slow mode
                    }
                    totalDragAmount = 0f // Reset for the next gesture
                }, onDragCancel = {
                    totalDragAmount = 0f
                })
            }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPlayerTrail(localPlayer)
            if (multiplayerState.isMultiplayer) {
                drawPlayerTrail(opponentPlayer)
            }
        }

        GameHud(
            modifier = modifier,
            uiState = uiState,
            onPauseResumeClick = { gameViewModel.toggleGameRunning() },
            onResetClick = {
                gameViewModel.resetGameRound()
                gameViewModel.initializeGamePositions(
                    with(density) { screenWidth.toFloat() },
                    with(density) { screenHeight.toFloat() })
            },
            onReturnToMenu = onReturnToMenu
        )
    }
}

private fun DrawScope.drawPlayerTrail(playerState: PlayerUiState) {
    for (i in 1 until playerState.trail.size) {
        val start = playerState.trail[i - 1]
        val end = playerState.trail[i]
        if (!start.isGap && !end.isGap) {
            drawLine(
                color = playerState.color,
                start = start.position,
                end = end.position,
                strokeWidth = GameConstants.STROKE_WIDTH
            )
        }
    }
}

@Composable
fun GameHud(
    modifier: Modifier,
    uiState: com.example.achtungdiekurve.data.GameUiState,
    onPauseResumeClick: () -> Unit,
    onResetClick: () -> Unit,
    onReturnToMenu: () -> Unit
) {
    val multiplayerState = uiState.multiplayerState

    // Pause/Reset/Menu Buttons
    Column(
        modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { if (uiState.isGameOver) onResetClick() else onPauseResumeClick() }) {
            Text(if (uiState.isGameOver) "Reset" else if (uiState.isRunning) "Pause" else "Resume")
        }

        if (!uiState.isRunning || uiState.isGameOver) {
            Button(onClick = onReturnToMenu, modifier = Modifier.padding(top = 8.dp)) {
                Text("Menu")
            }
        }
    }

    // Status Text
    Text(
        text = if (!multiplayerState.isMultiplayer) "Mode: Single Player"
        else "Role: ${if (multiplayerState.isHost) "Host (Red)" else "Client (Green)"} | ${multiplayerState.connectionStatus}",
        fontSize = 12.sp,
        color = Color.Black,
        modifier = Modifier.padding(8.dp)
    )

    // Game Over Message
    if (uiState.isGameOver) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.gameOverMessage,
                color = Color.Red,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    // Boost Status
    if (uiState.localPlayer.isAlive) {
        BoostStatus(
            boostState = uiState.localPlayer.boostState,
            cooldownFrames = uiState.localPlayer.boostCooldownFrames
        )
    }
}

@Composable
fun BoostStatus(boostState: BoostState, cooldownFrames: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        val (text, color) = when (boostState) {
            BoostState.READY -> "Boost Ready! (Swipe Up)" to Color.Green
            BoostState.BOOSTING -> "Boosting..." to Color.Magenta
            BoostState.COOLDOWN -> "Boost ready in ${cooldownFrames / 60}s" to Color.Black
        }
        Text(
            text = text,
            color = color,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}