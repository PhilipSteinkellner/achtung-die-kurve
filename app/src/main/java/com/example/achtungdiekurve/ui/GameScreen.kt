package com.example.achtungdiekurve.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.achtungdiekurve.data.BoostState
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.data.GameUiState
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
    val screenWidthPx = uiState.screenWidthPx
    val screenHeightPx = uiState.screenHeightPx

    val showScoreSetup by gameViewModel.shouldShowScoreSetup.collectAsState()
    val showGameOver by gameViewModel.shouldShowGameOver.collectAsState()

    val density = LocalDensity.current
    val localWindow = LocalWindowInfo.current

    LaunchedEffect(
        LocalWindowInfo.current.containerSize.width, LocalWindowInfo.current.containerSize.height
    ) {
        val currentScreenWidthPx = with(density) { localWindow.containerSize.width.toFloat() }
        val currentScreenHeightPx = with(density) { localWindow.containerSize.height.toFloat() }

        if (currentScreenWidthPx > 0 && currentScreenHeightPx > 0) {
            gameViewModel.initializeGamePositions(currentScreenWidthPx, currentScreenHeightPx)
        }
    }


    if (controlMode == ControlMode.TILT) {
        rememberAccelerometerSensorHandler { tiltValue ->
            gameViewModel.setLocalTurning(tiltValue)
        }
    }

    // Check if we should show the score setup screen before match
    //  val showScoreSetup = multiplayerState.isHost &&
            //        !uiState.isRunning &&
            //        !uiState.isGameOver &&
            //        uiState.localPlayer.score == 0 &&
            //        uiState.opponentPlayer.score == 0

    // Check if we should show the score setup screen after match
    //  val showScoreSetupAfterMatch = multiplayerState.isHost &&
    //        uiState.isMatchOver &&
    //        !uiState.isRunning

    // Check if one or the other condition is true.
    //  val shouldShowScoreSetup = showScoreSetup || showScoreSetupAfterMatch


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
            .pointerInput(localPlayer.isAlive, localPlayer.boostState, uiState.isRunning) {
                var totalDragAmount = 0f
                detectVerticalDragGestures(onDragStart = { offset ->
                    totalDragAmount = 0f
                }, onVerticalDrag = { change, dragAmount ->
                    totalDragAmount += dragAmount
                    change.consume()
                }, onDragEnd = {
                    if (!uiState.isRunning || !localPlayer.isAlive || localPlayer.boostState != BoostState.READY) return@detectVerticalDragGestures

                    if (totalDragAmount < -GameConstants.SWIPE_THRESHOLD) {
                        gameViewModel.toggleBoost(BoostState.BOOSTING)
                    } else if (totalDragAmount > GameConstants.SWIPE_THRESHOLD) {
                        gameViewModel.toggleBoost(BoostState.BRAKING)
                    }
                    totalDragAmount = 0f
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
            showScoreSetup = showScoreSetup        )

        // Score Setup Overlay
        if (showScoreSetup) {
            GameOverlay(
                title = uiState.scoreSetupTitle,
                message = uiState.scoreSetupMessage
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { gameViewModel.decrementScoreToWin() }) {
                        Text("-", fontSize = 20.sp)
                    }

                    Text(
                        text = "${uiState.scoreToWin}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Button(onClick = { gameViewModel.incrementScoreToWin() }) {
                        Text("+", fontSize = 20.sp)
                    }
                }

                Text(
                    text = if (uiState.scoreToWin == 1) "round" else "rounds",
                    fontSize = 16.sp,
                    color = Color.Gray
                )

                Button(
                    onClick = { gameViewModel.onScoreSetupStartGame() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = if (uiState.isMatchOver) "Start New Match" else "Start Game",
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Game Over Overlay
        if (showGameOver) {
            GameOverlay(
                title = uiState.gameOverTitle,
                message = uiState.gameOverMessage
            ) {
                Button(onClick = { gameViewModel.onGameOverNewGame() }) {
                    Text(
                        text = if (uiState.isMatchOver) "New Game" else "Next Round",
                        fontSize = 18.sp
                    )
                }

                Button(
                    onClick = onReturnToMenu,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Back to Menu", fontSize = 18.sp)
                }
            }
        }
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
fun GameOverlay(
    title: String,
    message: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    fontSize = 18.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )

                content()
            }
        }
    }
}

@Composable
fun GameHud(
    modifier: Modifier,
    uiState: GameUiState,
    onPauseResumeClick: () -> Unit,
    showScoreSetup: Boolean
) {
    val multiplayerState = uiState.multiplayerState

    // Only show control buttons if overlays are not active
    if (!showScoreSetup && !uiState.isGameOver) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onPauseResumeClick) {
                Text(if (uiState.isRunning) "Pause" else "Resume")
            }
        }
    }

    // Connection status at the top-left
    if (!showScoreSetup) {
        Text(
            text = if (!multiplayerState.isMultiplayer) "Mode: Single Player"
            else "Role: ${if (multiplayerState.isHost) "Host" else "Client"} | ${multiplayerState.connectionStatus}",
            fontSize = 12.sp,
            color = Color.Black,
            modifier = Modifier.padding(8.dp)
        )
    }

    // Bottom-aligned Score Display with Boost Status
    if (!showScoreSetup) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Score Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .border(2.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Local Player Score
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(uiState.localPlayer.color, CircleShape)
                                    .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${uiState.localPlayer.score}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "You",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // VS Text
                        Text(
                            text = "VS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        // Opponent Score
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(uiState.opponentPlayer.color, CircleShape)
                                    .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${uiState.opponentPlayer.score}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = if (multiplayerState.isMultiplayer) "Opponent" else "AI",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // First to X wins text
                    Box(
                        modifier = Modifier
                            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "First to ${uiState.scoreToWin} wins!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            // Boost Status (only show if player is alive)
            if (uiState.localPlayer.isAlive) {
                val (text, color) = when (uiState.localPlayer.boostState) {
                    BoostState.READY -> "Boost Ready! (Swipe Up/Down)" to Color.Green
                    BoostState.BOOSTING -> "Boosting..." to Color.Magenta
                    BoostState.BRAKING -> "Braking..." to Color.Magenta
                    BoostState.COOLDOWN -> "Boost ready in ${uiState.localPlayer.boostCooldownFrames / (1000f / GameConstants.GAME_TICK_RATE_MS).toInt()}s" to Color.Black
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = text,
                        color = color,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

