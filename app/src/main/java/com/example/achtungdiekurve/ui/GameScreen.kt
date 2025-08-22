package com.example.achtungdiekurve.ui

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.achtungdiekurve.R
import com.example.achtungdiekurve.data.ControlMode
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.data.GameState
import com.example.achtungdiekurve.data.MatchState
import com.example.achtungdiekurve.data.PlayerState
import com.example.achtungdiekurve.data.SpecialMoveState
import com.example.achtungdiekurve.game.GameViewModel
import com.example.achtungdiekurve.game.rememberAccelerometerSensorHandler
import com.example.achtungdiekurve.settings.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun CurveGameScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel,
    gameViewModel: GameViewModel = viewModel(),
    navController: NavController
) {
    val gameState by gameViewModel.gameState.collectAsState()
    val controlMode by settingsViewModel.controlMode.collectAsState()
    val nickname by settingsViewModel.nickname.collectAsState()
    val localWindowInfo = LocalWindowInfo.current

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.confetti)
    )

    if (gameState.multiplayerState.isHost) {
        LaunchedEffect(
            LocalWindowInfo.current.containerSize.width,
            LocalWindowInfo.current.containerSize.height
        ) {
            val currentScreenWidthPx =
                with(LocalDensity) { localWindowInfo.containerSize.width.toFloat() }
            val currentScreenHeightPx =
                with(LocalDensity) { localWindowInfo.containerSize.height.toFloat() }

            if (currentScreenWidthPx > 0 && currentScreenHeightPx > 0) {
                gameViewModel.initializeGamePositions(
                    currentScreenWidthPx - GameConstants.WALL_MARGIN,
                    currentScreenHeightPx - GameConstants.WALL_MARGIN
                )
            }
        }
    }

    if (gameState.multiplayerState.connectionStatus == "Disconnected") {
        gameViewModel.resetGameModeSelection()
        navController.popBackStack("menu", inclusive = false)
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
                gameState.isRunning,
                gameState.matchState,
                gameState.localPlayer.isAlive,
                gameState.localPlayer.boostState
            ) {
                detectTapGestures(onPress = {
                    if (controlMode != ControlMode.TAP || !gameState.isRunning || !gameState.localPlayer.isAlive) return@detectTapGestures

                    gameViewModel.setLocalTurning(if (it.x < size.width / 2) -1f else 1f)
                    try {
                        awaitRelease()
                    } finally {
                        gameViewModel.setLocalTurning(0f)
                    }
                })
            }
            .pointerInput(
                gameState.localPlayer.isAlive, gameState.localPlayer.boostState, gameState.isRunning
            ) {
                var totalDragAmount = 0f
                detectVerticalDragGestures(onDragStart = { offset ->
                    totalDragAmount = 0f
                }, onVerticalDrag = { change, dragAmount ->
                    totalDragAmount += dragAmount
                    change.consume()
                }, onDragEnd = {
                    if (!gameState.isRunning || !gameState.localPlayer.isAlive || gameState.localPlayer.boostState != SpecialMoveState.READY) return@detectVerticalDragGestures

                    if (totalDragAmount < -GameConstants.SWIPE_THRESHOLD) {
                        gameViewModel.toggleBoost(SpecialMoveState.BOOST)
                    } else if (totalDragAmount > GameConstants.SWIPE_THRESHOLD) {
                        gameViewModel.toggleBoost(SpecialMoveState.SLOW)
                    }
                    totalDragAmount = 0f
                }, onDragCancel = {
                    totalDragAmount = 0f
                })
            }) {
        Canvas(modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                if (gameState.collisionAnimation != null) {
                    // The anchor point (collision point) in pixels.
                    val anchorX = gameState.zoomCenter.x
                    val anchorY = gameState.zoomCenter.y

                    // Set the pivot for scaling to be the anchor point.
                    // transformOrigin requires fractions of the total size.
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (size.width > 0) anchorX / size.width else 0.5f,
                        pivotFractionY = if (size.height > 0) anchorY / size.height else 0.5f
                    )

                    // Apply the scale. Because the origin is set, the anchor point will remain
                    // stationary, and the canvas will expand/contract around it.
                    scaleX = gameState.zoomScale
                    scaleY = gameState.zoomScale
                }
            }


        ) {
            // background (whole canvas)
            drawRect(
                color = Color.Black, size = size
            )

            // White inner area
            drawRect(
                color = Color.White, topLeft = Offset(
                    (size.width - gameState.screenWidthPx) / 2f,
                    (size.height - gameState.screenHeightPx) / 2f
                ), size = Size(
                    gameState.screenWidthPx, gameState.screenHeightPx
                )
            )
            drawPlayerTrail(gameState.localPlayer)
            gameState.localPlayer.lastCollision?.let { collision ->
           //     val impactPoint = gameState.localPlayer.trail.last().position
                    //drawImpactNormal(impactPoint, collision.surfaceNormal)

            }

            gameState.opponents.forEach { player ->
                drawPlayerTrail(player)
                player.lastCollision?.let { collision ->
               //     val impactPoint = player.trail.last().position
                //  drawImpactNormal(impactPoint, collision.surfaceNormal)
                }
            }



        }

        gameState.collisionAnimation?.let { animation ->
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.confetti)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = 1,
                speed = 1.5f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Apply same zoom transformation to the animation
                        if (gameState.collisionAnimation != null) {
                            val anchorX = gameState.zoomCenter.x
                            val anchorY = gameState.zoomCenter.y

                            transformOrigin = TransformOrigin(
                                pivotFractionX = if (size.width > 0) anchorX / size.width else 0.5f,
                                pivotFractionY = if (size.height > 0) anchorY / size.height else 0.5f
                            )

                            scaleX = gameState.zoomScale
                            scaleY = gameState.zoomScale
                        }

                    }
            ) {
                val lottieSize = 200.dp
                val lottieSizePx = with(LocalDensity.current) { lottieSize.toPx() }


                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .size(lottieSize)
                        .offset(
                            x = with(LocalDensity.current) { (animation.position.x - lottieSizePx / 2).toDp() },
                            y = with(LocalDensity.current) { (animation.position.y - lottieSizePx / 2).toDp() }
                        )
                )
            }
        }


        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = if (!gameState.multiplayerState.isMultiplayer) "Mode: Single Player"
                else "Role: ${if (gameState.multiplayerState.isHost) "Host" else "Client"}",
                fontSize = 12.sp,
                color = Color.Black,
                modifier = Modifier.padding(8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (gameState.multiplayerState.isHost) {
                Button(
                    modifier = Modifier
                        .padding(8.dp)
                        .alpha(0.5f),
                    onClick = { gameViewModel.toggleGameRunning() }) {
                    Text(if (gameState.isRunning) "Pause" else "Resume")
                }
            }

            if (!gameState.isRunning && !gameState.multiplayerState.isHost) Button(
                modifier = Modifier.padding(8.dp),
                onClick = { navController.navigate("menu");gameViewModel.resetGameModeSelection() }) {
                Text(
                    text = "Menu"
                )
            }
        }

        if (gameState.matchState != MatchState.RUNNING) {
            GameOverlay(
                gameState, gameViewModel, navController = navController, nickname = nickname
            )
        }


        // Boost Status
        if (gameState.localPlayer.isAlive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val (text, color) = when (gameState.localPlayer.boostState) {
                    SpecialMoveState.READY -> "Special Move Ready! (Swipe Up/Down)" to Color.Green
                    SpecialMoveState.BOOST -> "Boost Mode..." to Color.Magenta
                    SpecialMoveState.SLOW -> "Slow Mode..." to Color.Magenta
                    SpecialMoveState.COOLDOWN -> "Cooldown ${gameState.localPlayer.boostCooldownFrames / (1000f / GameConstants.GAME_TICK_RATE_MS).roundToInt()}s" to Color.Black
                }

                Text(
                    text = text,
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .alpha(0.5f),
                    textAlign = TextAlign.Center
                )

            }

        }

    }
}


private fun DrawScope.drawPlayerTrail(playerState: PlayerState) {
    for (i in 1 until playerState.trail.size) {
        val start = playerState.trail[i - 1]
        val end = playerState.trail[i]
        if (!start.isGap && !end.isGap) {
            val color = when {
                !playerState.isAlive -> Color.Gray
                playerState.boostState == SpecialMoveState.BOOST -> Color.Magenta
                playerState.boostState == SpecialMoveState.SLOW -> Color.Magenta
                else -> playerState.color
            }

            drawLine(
                color = color,
                start = start.position,
                end = end.position,
                strokeWidth = GameConstants.STROKE_WIDTH
            )
        }
    }
}

private fun DrawScope.drawImpactNormal(
    impactPoint: Offset,
    surfaceNormal: Offset,
    length: Float = 850f, // length of green line
    color: Color = Color.Green
) {
    // scale normal vector by length
    val endPoint = impactPoint + surfaceNormal * length

    drawLine(
        color = color,
        start = impactPoint,
        end = endPoint,
        strokeWidth = 4f
    )
}

@Composable
fun GameOverlay(
    gameState: GameState,
    gameViewModel: GameViewModel,
    navController: NavController,
    nickname: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {

        if (gameState.multiplayerState.isHost) {

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
                    if (gameState.matchState != MatchState.MATCH_SETTINGS) Text(
                        text = if (gameState.matchState == MatchState.GAME_OVER) "Game Over" else "Round Over",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    if (!gameState.multiplayerState.isMultiplayer || gameState.matchState == MatchState.ROUND_OVER || gameState.matchState == MatchState.GAME_OVER) {
                        if (gameState.multiplayerState.isHost) {
                            Button(onClick = { gameViewModel.onGameOverNewGame() }) {
                                Text(
                                    text = if (gameState.matchState == MatchState.GAME_OVER) "New Game" else "Next Round",
                                    fontSize = 18.sp
                                )
                            }
                            Button(
                                onClick = {
                                    navController.navigate("menu")
                                    gameViewModel.resetGameModeSelection()
                                }) {
                                Text(
                                    text = "Menu", fontSize = 18.sp
                                )
                            }
                        }
                    } else if (gameState.matchState == MatchState.MATCH_SETTINGS) {
                        Text(
                            text = "Game Setup",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "First to win:",
                            fontSize = 18.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(onClick = { gameViewModel.decrementScoreToWin() }) {
                                Text("-", fontSize = 20.sp)
                            }

                            Text(
                                text = "${gameState.scoreToWin}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            Button(onClick = { gameViewModel.incrementScoreToWin() }) {
                                Text("+", fontSize = 20.sp)
                            }
                        }

                        Text(
                            text = if (gameState.scoreToWin == 1) "point" else "points",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )

                        Button(
                            onClick = { gameViewModel.onScoreSetupStartGame() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Start Game", fontSize = 18.sp
                            )
                        }

                    }
                }
            }
        }

        ScoreCard(
            gameState = gameState, nickname = nickname
        )

    }
}

@Composable
fun ScoreCard(
    gameState: GameState,
    nickname: String,
) {
    if (!gameState.isRunning) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp, 50.dp),
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                                    .background(gameState.localPlayer.color, CircleShape)
                                    .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${gameState.localPlayer.score}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = nickname.ifEmpty { "You" },
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        for (player in gameState.opponents) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(player.color, CircleShape)
                                        .border(
                                            2.dp, Color.Black.copy(alpha = 0.2f), CircleShape
                                        ), contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${player.score}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = player.name,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // First to X wins text
                    if (gameState.multiplayerState.isMultiplayer) Box(
                        modifier = Modifier
                            .background(
                                Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "First to ${gameState.scoreToWin} points!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

