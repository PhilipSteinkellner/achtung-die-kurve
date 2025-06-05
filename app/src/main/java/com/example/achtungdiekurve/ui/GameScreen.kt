package com.example.achtungdiekurve.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CurveGameScreen(modifier: Modifier, onReturnToMenu: () -> Unit) {
    rememberCoroutineScope()
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val screenHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current

    val trail = remember { mutableStateListOf(TrailSegment(Offset(500f, 1000f), isGap = false)) }
    var direction by remember { mutableFloatStateOf(0f) } // in radians
    var turning by remember { mutableFloatStateOf(0f) }
    var isRunning by remember { mutableStateOf(true) }
    var isGameOver by remember { mutableStateOf(false) }

    var isBoosting by remember { mutableStateOf(false) }
    var boostFrames by remember { mutableIntStateOf(0) }
    val boostDuration = 60 // 60 frames ≈ 1 second
    val normalSpeed = 3f
    val boostedSpeed = 6f

    var boostCooldownFrames by remember { mutableIntStateOf(0) }
    val boostCooldownDuration = 300 // 300 frames ≈ 5 seconds

    val turnSpeed = 0.07f

    // Gaps logic
    var gapCounter by remember { mutableIntStateOf(0) }
    var isDrawing by remember { mutableStateOf(true) }

    // Reset logic
    fun resetGame() {
        trail.clear()
        trail.add(TrailSegment(Offset(500f, 1000f), isGap = false))
        direction = 0f
        turning = 0f
        isRunning = true
        isGameOver = false
        gapCounter = 0
        isBoosting = false
        boostFrames = 0
    }

    // Game loop
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        with(density) {
            val widthPx = screenWidth
            val heightPx = screenHeight

            while (isRunning && !isGameOver) {
                delay(16L)
                direction += turning * turnSpeed
                val last = trail.last().position
                val currentSpeed = if (isBoosting) boostedSpeed else normalSpeed
                val next = Offset(
                    last.x + cos(direction) * currentSpeed, last.y + sin(direction) * currentSpeed
                )

                // Check for collision
                if (next.x < 0 || next.x > widthPx || next.y < 0 || next.y > heightPx) {
                    isGameOver = true
                    isRunning = false
                    continue
                }

                // Check for collision with trail
                val collisionRadius = 6f
                for (i in 1 until trail.size - 2) { // exclude the last few recent points
                    val segStart = trail[i - 1]
                    val segEnd = trail[i]

                    if (segStart.isGap || segEnd.isGap) continue

                    val dist = distanceFromPointToSegment(next, segStart.position, segEnd.position)
                    if (dist < collisionRadius) {
                        isGameOver = true
                        isRunning = false
                        break
                    }
                }

                gapCounter++
                val drawDuration = 200
                val gapDuration = 10

                if (isDrawing && gapCounter >= drawDuration) {
                    isDrawing = false
                    gapCounter = 0
                } else if (!isDrawing && gapCounter >= gapDuration) {
                    isDrawing = true
                    gapCounter = 0
                }

                if (isBoosting) {
                    boostFrames++
                    if (boostFrames >= boostDuration) {
                        isBoosting = false
                        boostFrames = 0
                    }
                }

                if (boostCooldownFrames > 0) {
                    boostCooldownFrames--
                }

                trail.add(TrailSegment(next, isGap = !isDrawing))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawing area
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 1 until trail.size) {
                val start = trail[i - 1]
                val end = trail[i]
                val color = when {
                    isGameOver -> Color.Gray
                    isBoosting -> Color.Magenta
                    else -> Color.Red
                }

                if (!start.isGap && !end.isGap) {
                    drawLine(
                        color = color, start = start.position, end = end.position, strokeWidth = 6f
                    )
                }
            }

        }

        // Touch input
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (!isRunning || isGameOver) return@detectTapGestures
                        turning = if (it.x < size.width / 2) -1f else 1f
                        try {
                            awaitRelease()
                        } finally {
                            turning = 0f
                        }
                    }, onDoubleTap = {
                        if (!isBoosting && boostCooldownFrames == 0) {
                            isBoosting = true
                            boostFrames = 0
                            boostCooldownFrames = boostCooldownDuration
                        }
                    })
                })

        Column(
            modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                if (isGameOver) resetGame()
                else isRunning = !isRunning
            }) {
                Text(if (isGameOver) "Reset" else if (isRunning) "Pause" else "Resume")
            }

            if (!isRunning) {
                Button(
                    onClick = onReturnToMenu, modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Return to Menu")
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isGameOver) {
                Text(
                    text = "Game Over!",
                    color = Color.Red,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (boostCooldownFrames > 0) {
                Text(
                    text = "Boost ready in ${boostCooldownFrames / 60}s",
                    color = Color.Black,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    text = "Boost Ready!",
                    color = Color.Green,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

data class TrailSegment(val position: Offset, val isGap: Boolean)

fun distanceFromPointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val abLengthSquared = ab.getDistanceSquared()
    if (abLengthSquared == 0f) return (p - a).getDistance()

    val t = ((ap dot ab) / abLengthSquared).coerceIn(0f, 1f)
    val projection = a + ab * t
    return (p - projection).getDistance()
}

infix fun Offset.dot(other: Offset): Float = this.x * other.x + this.y * other.y

@Composable
fun CurveApp(modifier: Modifier) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "menu") {
        composable("menu") {
            MenuScreen(onStartClick = { navController.navigate("game") })
        }
        composable("game") {
            CurveGameScreen(
                modifier = modifier,
                onReturnToMenu = { navController.popBackStack("menu", inclusive = false) })
        }
    }

}

@Composable
fun MenuScreen(onStartClick: () -> Unit) {
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
        }
    }
}