package com.example.achtungdiekurve.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("UnrememberedMutableState")
@Composable
fun CurveGameScreen(
    modifier: Modifier,
    onReturnToMenu: () -> Unit,
    serviceId: String = "com.example.achtungdiekurve"
) {
    val context = LocalContext.current
    val connectionsClient = remember { Nearby.getConnectionsClient(context) }   // Google's Nearby API client class
    rememberCoroutineScope()

    // Multiplayer connection states - these track whether we're advertising or discovering other devices

    var isAdvertising by mutableStateOf(false)
    var isDiscovering by mutableStateOf(false)


    val screenWidth = LocalWindowInfo.current.containerSize.width
    val screenHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current

    // Local player's trail and state
    val localTrail = remember { mutableStateListOf(TrailSegment(Offset(0f, 0f), isGap = false)) }
    var localDirection by remember { mutableFloatStateOf(0f) }
    var localTurning by remember { mutableFloatStateOf(0f) }
    var localIsDrawing by remember { mutableStateOf(true) }
    var localGapCounter by remember { mutableIntStateOf(0) }
    var localIsBoosting by remember { mutableIntStateOf(0) }
    var localBoostFrames by remember { mutableIntStateOf(0) }
    var localBoostCooldownFrames by remember { mutableIntStateOf(0) }
    var localIsAlive by remember { mutableStateOf(true) }

    // Opponent player's trail and state
    val opponentTrail = remember { mutableStateListOf<TrailSegment>() }
    var opponentIsAlive by remember { mutableStateOf(true) }

    // Game states
    var isRunning by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var gameOverMessage by remember { mutableStateOf("") }

    // Multiplayer states
    var showMultiplayerSetup by remember { mutableStateOf(true) }
    var isHost by remember { mutableStateOf(false) }
    var isConnecting by mutableStateOf(false)
    var connectionStatus by remember { mutableStateOf("Not Connected") }
    var connectedEndpointId by remember { mutableStateOf<String?>(null) }

    // Game constants
    val boostDuration = 60
    val normalSpeed = 3f
    val boostedSpeed = 6f
    val boostCooldownDuration = 300
    val turnSpeed = 0.07f
    val strokeWidth = 6f
    val drawDuration = 200
    val gapDuration = 10

    // Reset game state for a new round
    fun resetGame() {
        localTrail.clear()
        opponentTrail.clear()

        val initialX: Float
        val initialY: Float

        with(density) {
            val widthPx = screenWidth.toFloat()
            val heightPx = screenHeight.toFloat()

            // Host and client start on opposite sides so they don't immediately crash into each other
            if (isHost) {
                initialX = widthPx / 4f
                initialY = heightPx / 2f
                localDirection = 0f // Host starts moving right
            } else {
                initialX = 3 * widthPx / 4f
                initialY = heightPx / 2f
                localDirection = Math.PI.toFloat() // Client starts moving left
            }
        }


        // Reset all the game state variables to their starting values
        localTrail.add(TrailSegment(Offset(initialX, initialY), isGap = false))
        localTurning = 0f
        localIsDrawing = true
        localGapCounter = 0
        localIsBoosting = 0
        localBoostFrames = 0
        localBoostCooldownFrames = 0
        localIsAlive = true
        opponentIsAlive = true
        isRunning = true
        isGameOver = false
        gameOverMessage = ""
    }

    // Nearby Connections payload callback
    // We recive a string -> then interpret it as a command.

    val payloadCallback = remember {
        object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                if (payload.type == Payload.Type.BYTES) {
                    val message = String(payload.asBytes()!!)
                    println("Received game data from $endpointId: $message")

                    val parts = message.split(":")                              // Data is sent as a string - this splits it by colons
                    when (parts[0]) {
                        "position" -> {                                         // Other player sent their current position and state Format: "x,y,direction,isDrawing"
                            if (parts.size == 2) {
                                val positionData = parts[1].split(",")
                                if (positionData.size == 4) {
                                    try {
                                        val x = positionData[0].toFloat()
                                        val y = positionData[1].toFloat()
                                        val receivedIsDrawing = positionData[3].toBoolean()

                                        val newOpponentPos = Offset(x, y)
                                        // Add to opponent's trail with distance check - Prevent duplicates.
                                        if (opponentTrail.isNotEmpty() && (newOpponentPos - opponentTrail.last().position).getDistance() > 1f) {
                                            opponentTrail.add(TrailSegment(newOpponentPos, isGap = !receivedIsDrawing))
                                        } else if (opponentTrail.isEmpty()) {
                                            opponentTrail.add(TrailSegment(newOpponentPos, isGap = !receivedIsDrawing))
                                        }
                                    } catch (e: NumberFormatException) {
                                        println("Error parsing received position data: ${e.message}")
                                    }
                                }
                            }
                        }
                        "gameover" -> {
                            if (parts.size == 2) {
                                val crashedPlayer = parts[1]
                                when (crashedPlayer) {
                                    "local" -> {
                                        // Opponent crashed locally
                                        opponentIsAlive = false
                                        if (localIsAlive) {
                                            isGameOver = true
                                            isRunning = false
                                            gameOverMessage = "You Win! Opponent crashed!"
                                        }
                                    }
                                    "opponent" -> {
                                        // We crashed according to opponent
                                        localIsAlive = false
                                        isGameOver = true
                                        isRunning = false
                                        gameOverMessage = "You Lose! You crashed!"
                                    }
                                }
                            }
                        }
                        "resetGame" -> {
                            // Other player wants to start a new round
                            resetGame()
                        }
                    }
                }
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                // Handle payload transfer updates if needed
            }
        }
    }

    // Nearby Connections lifecycle callback
    val connectionLifecycleCallback = remember {
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                // Another device wants to connect
                connectionStatus = "Connection initiated with: ${info.endpointName}"
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener {
                        connectionStatus = "Accepting connection from: ${info.endpointName}"
                    }
                    .addOnFailureListener { e ->
                        connectionStatus = "Failed to accept connection: ${e.message}"
                    }
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                // Connection attempt finished - check if it worked
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        connectedEndpointId = endpointId
                        connectionStatus = "Connected!"
                        showMultiplayerSetup = false
                        isConnecting = false
                        stopAdvertisingAndDiscovery()
                        resetGame() // Reset game to start fresh after connection
                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        connectionStatus = "Connection rejected by other device."
                        isConnecting = false
                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        connectionStatus = "Connection error."
                        isConnecting = false
                    }
                    else -> {
                        connectionStatus = "Connection failed: ${result.status.statusCode}"
                        isConnecting = false
                    }
                }
            }

            private fun stopAdvertisingAndDiscovery() {
                // Clean up - if both are connected stop looking for/advertising to other devices
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                isAdvertising = false
                isDiscovering = false
            }

            override fun onDisconnected(endpointId: String) {
                // Other player disconnected - go back to setup screen
                connectedEndpointId = null
                connectionStatus = "Disconnected"
                isRunning = false
                showMultiplayerSetup = true
                resetGame()
            }
        }
    }

    // This handles discovering other devices when joining a game
    val endpointDiscoveryCallback = remember {
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // Found a host! Try to connect to them
                connectionStatus = "Found host: ${info.endpointName}, requesting connection..."
                connectionsClient.requestConnection(
                    "Player Device",                 // TODO There should be someway to customize this in the menus.
                    endpointId,
                    connectionLifecycleCallback
                )
                    .addOnSuccessListener {
                        connectionStatus = "Requested connection to: ${info.endpointName}"
                    }
                    .addOnFailureListener { e ->
                        connectionStatus = "Failed to request connection: ${e.message}"
                        isConnecting = false
                    }
            }

            override fun onEndpointLost(endpointId: String) {
                if (connectedEndpointId == null) {
                    connectionStatus = "Lost discovery of host endpoint"
                }
            }
        }
    }

    // Function to start hosting
    fun startHosting() {
        isConnecting = true
        isHost = true
        connectionStatus = "Starting to host..."

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startAdvertising(
            "Game Host",
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
            connectionStatus = "Waiting for players to join..."
        }.addOnFailureListener { exception ->
            connectionStatus = "Failed to start hosting: ${exception.message}"
            isConnecting = false
        }
    }

    // Function to start connecting to host
    fun startConnecting() {
        isConnecting = true
        isHost = false
        connectionStatus = "Searching for host..."

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            isDiscovering = true
            connectionStatus = "Searching for games..."
        }.addOnFailureListener { exception ->
            connectionStatus = "Failed to search: ${exception.message}"
            isConnecting = false
        }
    }

    // Function to send game data to other player
    fun sendGameData(data: String) {
        connectedEndpointId?.let { endpointId ->
            val payload = Payload.fromBytes(data.toByteArray())
            connectionsClient.sendPayload(endpointId, payload)
        }
    }

    // Cleanup on dispose- this is run to clear the connection screen. TODO Would be smart to later split the hosting/joining into a seperate class.
    DisposableEffect(Unit) {
        onDispose {
            connectionsClient.stopAllEndpoints()
            isAdvertising = false
            isDiscovering = false
            isConnecting = false
            isRunning = false
            connectedEndpointId = null
            connectionStatus = "Not Connected"
            localTrail.clear()
            opponentTrail.clear()
        }
    }

    // Game loop - Updates GameState
    LaunchedEffect(isRunning, connectedEndpointId, screenWidth, screenHeight) {
        //If any of the conditions are meet do not run the game.
        if (!isRunning || connectedEndpointId == null || screenWidth == 0 || screenHeight == 0 || !localIsAlive) {
            return@LaunchedEffect
        }

        with(density) {
            val widthPx = screenWidth.toFloat()
            val heightPx = screenHeight.toFloat()

            // Re-initialize local player's position if needed
            if (localTrail.isEmpty() || (localTrail.size == 1 && localTrail.first().position == Offset(0f, 0f))) {
                resetGame()
            }

            // Perform this every frame if the game is okay and player is alive.
            while (isRunning && !isGameOver && connectedEndpointId != null && localIsAlive) {
                delay(16L) // Roughly 60 FPS

                // Move local player's snake
                localDirection += localTurning * turnSpeed
                val last = localTrail.last().position
                val currentSpeed = if (localIsBoosting == 1) boostedSpeed else normalSpeed
                val next = Offset(
                    last.x + cos(localDirection) * currentSpeed,
                    last.y + sin(localDirection) * currentSpeed
                )

                // Collision detection for local player
                val collisionRadius = strokeWidth / 2f
                var crashed = false

                // Check boundary collision
                if (next.x < 0 || next.x > widthPx || next.y < 0 || next.y > heightPx) {
                    crashed = true
                }

                // Check self-collision (exclude last few segments)
                if (!crashed) {
                    for (i in 0 until localTrail.size - 2) {
                        val segStart = localTrail[i]
                        val segEnd = localTrail[i + 1]
                        if (segStart.isGap || segEnd.isGap) continue

                        if (distanceFromPointToSegment(next, segStart.position, segEnd.position) < collisionRadius) {
                            crashed = true
                            break
                        }
                    }
                }

                // Check collision with opponent's trail
                if (!crashed && opponentTrail.isNotEmpty()) {
                    for (i in 0 until opponentTrail.size - 1) {
                        val oppSegStart = opponentTrail[i]
                        val oppSegEnd = opponentTrail[i + 1]
                        if (oppSegStart.isGap || oppSegEnd.isGap) continue

                        if (distanceFromPointToSegment(next, oppSegStart.position, oppSegEnd.position) < collisionRadius) {
                            crashed = true
                            break
                        }
                    }
                }

                if (crashed) {
                    localIsAlive = false
                    sendGameData("gameover:local") // Tell opponent we crashed

                    // Check if opponent is also dead for draw condition
                    if (!opponentIsAlive) {
                        isGameOver = true
                        isRunning = false
                        gameOverMessage = "Draw! Both players crashed!"
                    } else {
                        isGameOver = true
                        isRunning = false
                        gameOverMessage = "You Lose! You crashed!"
                    }
                    continue
                }

                // Gap logic
                localGapCounter++
                if (localIsDrawing && localGapCounter >= drawDuration) {
                    localIsDrawing = false
                    localGapCounter = 0
                } else if (!localIsDrawing && localGapCounter >= gapDuration) {
                    localIsDrawing = true
                    localGapCounter = 0
                }

                // Boost logic
                if (localIsBoosting == 1) {
                    localBoostFrames++
                    if (localBoostFrames >= boostDuration) {
                        localIsBoosting = 2 // Enter cooldown
                        localBoostFrames = 0
                        localBoostCooldownFrames = boostCooldownDuration
                    }
                } else if (localIsBoosting == 2) {
                    localBoostCooldownFrames--
                    if (localBoostCooldownFrames <= 0) {
                        localIsBoosting = 0 // Ready for boost
                    }
                }

                // Add new segment to local trail
                localTrail.add(TrailSegment(next, isGap = !localIsDrawing))

                // Send position update to opponent
                sendGameData("position:${next.x},${next.y},${localDirection},${localIsDrawing}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Show multiplayer setup screen
        if (showMultiplayerSetup) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Multiplayer Setup",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = connectionStatus,
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (!isConnecting) {
                    Button(
                        onClick = { startHosting() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Host Game")
                    }

                    Button(
                        onClick = { startConnecting() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Join Game")
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = connectionStatus,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onReturnToMenu,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Menu")
                }
            }
        } else {
            // Game UI - show when connected
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw local player's trail
                for (i in 1 until localTrail.size) {
                    val start = localTrail[i - 1]
                    val end = localTrail[i]
                    val color = when {
                        !localIsAlive -> Color.Gray
                        localIsBoosting == 1 -> Color.Magenta
                        else -> if (isHost) Color.Red else Color.Green // TODO Customized Player Colors currently Red for host, green for client
                    }

                    if (!start.isGap && !end.isGap) {
                        drawLine(
                            color = color,
                            start = start.position,
                            end = end.position,
                            strokeWidth = strokeWidth
                        )
                    }
                }

                // Draw opponent's trail
                for (i in 1 until opponentTrail.size) {
                    val start = opponentTrail[i - 1]
                    val end = opponentTrail[i]
                    val opponentColor = when {
                        !opponentIsAlive -> Color.Gray
                        else -> if (isHost) Color.Green else Color.Red
                    }

                    if (!start.isGap && !end.isGap) {
                        drawLine(
                            color = opponentColor,
                            start = start.position,
                            end = end.position,
                            strokeWidth = strokeWidth
                        )
                    }
                }
            }

            // Touch input for game controls
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!isRunning || isGameOver || !localIsAlive) return@detectTapGestures
                                // Determine turn direction based on which side of screen was pressed
                                localTurning = if (it.x < size.width / 2) -1f else 1f
                                try {
                                    awaitRelease() // Keep turning until finger is lifted
                                } finally {
                                    localTurning = 0f
                                }
                            },
                            onDoubleTap = {
                                //Boost
                                if (!localIsAlive || localIsBoosting != 0) return@detectTapGestures
                                localIsBoosting = 1
                                localBoostFrames = 0
                            }
                        )
                    }
            )

            // Game UI elements
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = {
                    if (isGameOver) {
                        sendGameData("resetGame:")
                        resetGame()
                    } else {
                        isRunning = !isRunning
                    }
                }) {
                    Text(if (isGameOver) "Reset" else if (isRunning) "Pause" else "Resume")
                }

                if (!isRunning || isGameOver) {
                    Button(
                        onClick = onReturnToMenu,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Return to Menu")
                    }
                }
            }

            // Connection status indicator
            Text(
                text = "Role: ${if (isHost) "Host (Red)" else "Client (Green)"} | $connectionStatus",
                fontSize = 12.sp,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            // Game Over message TODO add a black box behind the text so it stands out more.
            if (isGameOver) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = gameOverMessage,
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
                if (localIsAlive) {
                    when (localIsBoosting) {
                        0 -> Text(
                            text = "Boost Ready! (Double tap)",
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
                            text = "Boost ready in ${localBoostCooldownFrames / 60}s",
                            color = Color.Black,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Data class to represent a single point in a player's trail
data class TrailSegment(val position: Offset, val isGap: Boolean)

// Helper function to calculate distance from a point to a line segment
fun distanceFromPointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val abLengthSquared = ab.getDistanceSquared()

    if (abLengthSquared == 0f) return (p - a).getDistance()

    val t = ((ap dot ab) / abLengthSquared).coerceIn(0f, 1f)
    val projection = a + ab * t
    return (p - projection).getDistance()
}

// Infix function for dot product of two Offsets
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