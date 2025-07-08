package com.example.achtungdiekurve.game

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.data.GameState
import com.example.achtungdiekurve.data.PlayerState
import com.example.achtungdiekurve.data.TrailSegment
import com.example.achtungdiekurve.data.calculateNextPosition
import com.example.achtungdiekurve.data.distanceFromPointToSegment
import com.example.achtungdiekurve.multiplayer.ConnectionState
import com.example.achtungdiekurve.multiplayer.NearbyConnectionsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _gameState = MutableStateFlow(
        GameState(
            localTrail = emptyList(),
            localDirection = 0f,
            localTurning = 0f,
            localIsDrawing = true,
            localGapCounter = 0,
            localIsBoosting = 0,
            localBoostFrames = 0,
            localBoostCooldownFrames = 0,
            localIsAlive = true,
            opponentTrail = emptyList(),
            opponentIsAlive = true,
            isRunning = false,
            isGameOver = false,
            gameOverMessage = "",
            isHost = false,
            connectionStatus = "Not Connected",
            showMultiplayerSetup = false, // Set to false initially, only true if Multiplayer is chosen
            connectedEndpointId = null,
            isSinglePlayer = false, // Initialize as false
            showModeSelection = true // Start with mode selection
        )
    )
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val localPlayerState = PlayerState()
    private val opponentPlayerState = PlayerState()

    private var gameLoopJob: Job? = null
    private var _isHost: Boolean = false // Internal flag to track if we initiated as host

    private val nearbyConnectionsManager: NearbyConnectionsManager by lazy {
        NearbyConnectionsManager(
            context = getApplication<Application>().applicationContext,
            serviceId = "com.example.achtungdiekurve",
            onGameDataReceived = ::handleGameDataReceived,
            onConnected = ::onNearbyConnected,
            onDisconnected = ::onNearbyDisconnected
        )
    }

    init {
        // Observe connection state changes from NearbyConnectionsManager
        viewModelScope.launch {
            nearbyConnectionsManager.connectionState.collect { connectionState ->
                // Only update multiplayer-related status if not in single player mode
                if (!_gameState.value.isSinglePlayer) {
                    when (connectionState) {
                        is ConnectionState.Connected -> {
                            _gameState.update {
                                it.copy(
                                    connectionStatus = "Connected!",
                                    showMultiplayerSetup = false,
                                    connectedEndpointId = connectionState.endpointId
                                )
                            }
                            // Game reset is handled by onNearbyConnected now
                        }

                        is ConnectionState.Disconnected -> {
                            _gameState.update {
                                it.copy(
                                    connectionStatus = "Disconnected",
                                    showMultiplayerSetup = true,
                                    isRunning = false,
                                    connectedEndpointId = null
                                )
                            }
                            resetGame() // Reset game state fully on disconnect
                        }

                        is ConnectionState.Error -> {
                            _gameState.update {
                                it.copy(
                                    connectionStatus = "Connection Error: ${connectionState.message}",
                                    showMultiplayerSetup = true, // Show setup on error
                                    isRunning = false,
                                    connectedEndpointId = null
                                )
                            }
                        }

                        is ConnectionState.Status -> {
                            _gameState.update { it.copy(connectionStatus = connectionState.message) }
                        }

                        is ConnectionState.Advertising -> {
                            _gameState.update {
                                it.copy(
                                    connectionStatus = "Waiting for players to join...",
                                    showMultiplayerSetup = true
                                )
                            }
                        }

                        is ConnectionState.Discovering -> {
                            _gameState.update {
                                it.copy(
                                    connectionStatus = "Searching for games...",
                                    showMultiplayerSetup = true
                                )
                            }
                        }

                        is ConnectionState.Connecting -> {
                            _gameState.update {
                                it.copy(
                                    connectionStatus = "Connecting...",
                                    showMultiplayerSetup = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Mode Selection ---
    fun selectSinglePlayerMode() {
        _gameState.update {
            it.copy(
                isSinglePlayer = true,
                showModeSelection = false,
                showMultiplayerSetup = false,
                connectionStatus = "Single Player"
            )
        }
        resetGame()
        // No need to call startGameLoop directly here, it's handled by LaunchedEffect in UI
    }

    fun selectMultiplayerMode() {
        _gameState.update {
            it.copy(
                isSinglePlayer = false,
                showModeSelection = false,
                showMultiplayerSetup = true, // Show multiplayer setup
                connectionStatus = "Not Connected"
            )
        }
        resetGame()
    }

    // --- Nearby Connections Callbacks ---
    private fun onNearbyConnected(endpointId: String, isHost: Boolean) {
        _isHost = isHost
        _gameState.update {
            it.copy(
                isHost = isHost, showModeSelection = false // Ensure mode selection is off
            )
        }
        resetGame() // Start fresh game after connection
    }

    private fun onNearbyDisconnected() {
        // State update handled by flow collection in init block
    }

    // --- Game Controls ---
    fun setLocalTurning(turning: Float) {
        localPlayerState.turning = turning
    }

    fun toggleBoost() {
        if (!localPlayerState.isAlive || localPlayerState.isBoosting != 0) return
        localPlayerState.isBoosting = 1
        localPlayerState.boostFrames = 0
    }

    fun toggleGameRunning() {
        val newIsRunning = !_gameState.value.isRunning
        _gameState.update { it.copy(isRunning = newIsRunning) }

        // Send game running status to opponent if in multiplayer
        if (!_gameState.value.isSinglePlayer) {
            nearbyConnectionsManager.sendGameData("game_running_status:$newIsRunning")
        }

        val currentState = _gameState.value
        val canStartGame =
            currentState.isRunning && !currentState.isGameOver && currentState.localIsAlive && (currentState.isSinglePlayer || currentState.connectedEndpointId != null) // Allow start if single player OR connected

        if (canStartGame) {
            // Screen dimensions will be passed from UI, so we just trigger the loop
            // The LaunchedEffect in GameScreenUI will call startGameLoop with dimensions
        } else {
            stopGameLoop()
        }
    }

    fun resetGameRound() {
        if (!_gameState.value.isSinglePlayer) {
            nearbyConnectionsManager.sendGameData("resetGame:")
        }
        resetGame()
    }

    fun startHostingGame() {
        if (_gameState.value.isSinglePlayer) return // Prevent hosting in single player mode
        nearbyConnectionsManager.stopAllEndpoints() // Clean up any prior states
        nearbyConnectionsManager.startHosting()
        _gameState.update { it.copy(showMultiplayerSetup = true) }
    }

    fun startJoiningGame() {
        if (_gameState.value.isSinglePlayer) return // Prevent joining in single player mode
        nearbyConnectionsManager.stopAllEndpoints() // Clean up any prior states
        nearbyConnectionsManager.startDiscovery()
        _gameState.update { it.copy(showMultiplayerSetup = true) }
    }

    // --- Game Logic ---
    private fun resetGame() {
        localPlayerState.apply {
            trail.clear()
            turning = 0f
            isDrawing = true
            gapCounter = 0
            isBoosting = 0
            boostFrames = 0
            boostCooldownFrames = 0
            isAlive = true
        }
        opponentPlayerState.apply { // Always reset opponent state, just in case
            trail.clear()
            isAlive = true
        }

        _gameState.update {
            it.copy(
                localTrail = localPlayerState.trail.toList(),
                localDirection = 0f, // Will be set in startGameLoop or initializeGamePositions
                localTurning = 0f,
                localIsDrawing = true,
                localGapCounter = 0,
                localIsBoosting = 0,
                localBoostFrames = 0,
                localBoostCooldownFrames = 0,
                localIsAlive = true,
                opponentTrail = opponentPlayerState.trail.toList(),
                opponentIsAlive = true,
                isRunning = false, // Set to false initially, true when game loop starts
                isGameOver = false,
                gameOverMessage = ""
            )
        }
        stopGameLoop() // Ensure any existing loop is stopped
    }

    // This method is called once screen dimensions are known
    fun initializeGamePositions(screenWidthPx: Float, screenHeightPx: Float) {
        if (localPlayerState.trail.isEmpty()) {
            val initialX: Float
            val initialY: Float
            val initialDirection: Float

            if (_gameState.value.isSinglePlayer) {
                // For single player, start roughly in the middle, facing right
                initialX = screenWidthPx / 2f
                initialY = screenHeightPx / 2f
                initialDirection = 0f
            } else if (_isHost) {
                initialX = screenWidthPx / 4f
                initialY = screenHeightPx / 2f
                initialDirection = 0f // Host starts moving right
            } else {
                initialX = 3 * screenWidthPx / 4f
                initialY = screenHeightPx / 2f
                initialDirection = PI.toFloat() // Client starts moving left
            }
            localPlayerState.trail.add(TrailSegment(Offset(initialX, initialY), isGap = false))
            localPlayerState.direction = initialDirection
            _gameState.update {
                it.copy(
                    localTrail = localPlayerState.trail.toList(),
                    localDirection = initialDirection
                )
            }
        }
    }

    fun startGameLoop(screenWidthPx: Float, screenHeightPx: Float) {
        if (gameLoopJob?.isActive == true) return // Prevent multiple loops
        gameLoopJob = viewModelScope.launch {
            if (_gameState.value.localTrail.isEmpty()) {
                initializeGamePositions(screenWidthPx, screenHeightPx)
            }
            _gameState.update { it.copy(isRunning = true) }
            while (_gameState.value.isRunning && !_gameState.value.isGameOver && _gameState.value.localIsAlive && (_gameState.value.isSinglePlayer || _gameState.value.connectedEndpointId != null) // Condition updated
            ) {
                delay(GameConstants.GAME_TICK_RATE_MS)
                updateGame(screenWidthPx, screenHeightPx)
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        _gameState.update { it.copy(isRunning = false) }
    }

    private fun updateGame(screenWidthPx: Float, screenHeightPx: Float) {
        // Update local player's state
        localPlayerState.direction += localPlayerState.turning * GameConstants.TURN_SPEED
        val lastLocalPos = localPlayerState.trail.last().position
        val currentSpeed =
            if (localPlayerState.isBoosting == 1) GameConstants.BOOSTED_SPEED else GameConstants.NORMAL_SPEED
        val nextLocalPos =
            calculateNextPosition(lastLocalPos, localPlayerState.direction, currentSpeed)

        // Collision detection for local player
        val collisionRadius = GameConstants.STROKE_WIDTH * GameConstants.COLLISION_RADIUS_MULTIPLIER
        var crashed = false

        // Check boundary collision
        if (nextLocalPos.x < 0 || nextLocalPos.x > screenWidthPx || nextLocalPos.y < 0 || nextLocalPos.y > screenHeightPx) {
            crashed = true
        }

        // Check self-collision (always for single player)
        if (!crashed) {
            // Check self-collision (exclude last few segments to prevent immediate self-collision)
            val segmentsToCheck =
                if (localPlayerState.trail.size > 5) localPlayerState.trail.size - 5 else 0
            for (i in 0 until segmentsToCheck) {
                val segStart = localPlayerState.trail[i]
                val segEnd = localPlayerState.trail[i + 1]
                if (segStart.isGap || segEnd.isGap) continue

                if (distanceFromPointToSegment(
                        nextLocalPos,
                        segStart.position,
                        segEnd.position
                    ) < collisionRadius
                ) {
                    crashed = true
                    break
                }
            }
        }


        // Check collision with opponent's trail (only in multiplayer)
        if (!crashed && !_gameState.value.isSinglePlayer && opponentPlayerState.trail.isNotEmpty()) {
            for (i in 0 until opponentPlayerState.trail.size - 1) {
                val oppSegStart = opponentPlayerState.trail[i]
                val oppSegEnd = opponentPlayerState.trail[i + 1]
                if (oppSegStart.isGap || oppSegEnd.isGap) continue

                if (distanceFromPointToSegment(
                        nextLocalPos,
                        oppSegStart.position,
                        oppSegEnd.position
                    ) < collisionRadius
                ) {
                    crashed = true
                    break
                }
            }
        }

        if (crashed) {
            localPlayerState.isAlive = false
            if (!_gameState.value.isSinglePlayer) {
                nearbyConnectionsManager.sendGameData("gameover:opponent") // Tell opponent we crashed
            }
            handleGameOver(crashedByLocal = true)
            return
        }

        // Gap logic
        localPlayerState.gapCounter++
        if (localPlayerState.isDrawing && localPlayerState.gapCounter >= GameConstants.DRAW_DURATION_FRAMES) {
            localPlayerState.isDrawing = false
            localPlayerState.gapCounter = 0
        } else if (!localPlayerState.isDrawing && localPlayerState.gapCounter >= GameConstants.GAP_DURATION_FRAMES) {
            localPlayerState.isDrawing = true
            localPlayerState.gapCounter = 0
        }

        // Boost logic
        when (localPlayerState.isBoosting) {
            1 -> { // Boosting
                localPlayerState.boostFrames++
                if (localPlayerState.boostFrames >= GameConstants.BOOST_DURATION_FRAMES) {
                    localPlayerState.isBoosting = 2 // Enter cooldown
                    localPlayerState.boostFrames = 0
                    localPlayerState.boostCooldownFrames =
                        GameConstants.BOOST_COOLDOWN_DURATION_FRAMES
                }
            }

            2 -> { // Cooldown
                localPlayerState.boostCooldownFrames--
                if (localPlayerState.boostCooldownFrames <= 0) {
                    localPlayerState.isBoosting = 0 // Ready for boost
                }
            }
        }

        // Add new segment to local trail
        localPlayerState.trail.add(TrailSegment(nextLocalPos, isGap = !localPlayerState.isDrawing))

        // Send full player state update to opponent (only in multiplayer)
        if (!_gameState.value.isSinglePlayer) {
            nearbyConnectionsManager.sendGameData(
                "player_sync:${nextLocalPos.x},${nextLocalPos.y},${localPlayerState.direction}," + "${localPlayerState.isDrawing},${localPlayerState.turning},${localPlayerState.isBoosting}"
            )
        }


        // Update overall game state flow
        _gameState.update {
            it.copy(
                localTrail = localPlayerState.trail.toList(),
                localDirection = localPlayerState.direction,
                localTurning = localPlayerState.turning,
                localIsDrawing = localPlayerState.isDrawing,
                localGapCounter = localPlayerState.gapCounter,
                localIsBoosting = localPlayerState.isBoosting,
                localBoostFrames = localPlayerState.boostFrames,
                localBoostCooldownFrames = localPlayerState.boostCooldownFrames,
                localIsAlive = localPlayerState.isAlive,
                opponentTrail = opponentPlayerState.trail.toList(), // Make sure opponent's trail is also updated in state
                opponentIsAlive = opponentPlayerState.isAlive
            )
        }
    }

    private fun handleGameDataReceived(message: String) {
        if (_gameState.value.isSinglePlayer) return // Ignore data if in single player mode

        val parts = message.split(":")
        when (parts[0]) {
            "player_sync" -> { // New message type for full player state synchronization
                if (parts.size == 2) {
                    val playerData = parts[1].split(",")
                    // Expecting 6 pieces of data: x, y, direction, isDrawing, turning, isBoosting
                    if (playerData.size == 6) {
                        try {
                            val x = playerData[0].toFloat()
                            val y = playerData[1].toFloat()
                            val receivedDirection = playerData[2].toFloat()
                            val receivedIsDrawing = playerData[3].toBoolean()
                            val receivedTurning =
                                playerData[4].toFloat() // Opponent's turning input
                            val receivedIsBoosting = playerData[5].toInt() // Opponent's boost state

                            val newOpponentPos = Offset(x, y)

                            // Update opponentPlayerState directly with received values
                            opponentPlayerState.direction = receivedDirection
                            opponentPlayerState.turning = receivedTurning
                            opponentPlayerState.isBoosting = receivedIsBoosting
                            opponentPlayerState.isDrawing = receivedIsDrawing

                            // Add to opponent's trail. Ensure we're adding the received position
                            // with a distance check to prevent duplicates if updates are very frequent.
                            if (opponentPlayerState.trail.isEmpty() || (newOpponentPos - opponentPlayerState.trail.last().position).getDistance() > 1f) {
                                opponentPlayerState.trail.add(
                                    TrailSegment(
                                        newOpponentPos,
                                        isGap = !receivedIsDrawing
                                    )
                                )
                            }
                            _gameState.update { it.copy(opponentTrail = opponentPlayerState.trail.toList()) }
                        } catch (e: NumberFormatException) {
                            println("Error parsing received player_sync data: ${e.message}")
                        }
                    }
                }
            }

            "gameover" -> {
                if (parts.size == 2) {
                    val crashedPlayer = parts[1]
                    when (crashedPlayer) {
                        "local" -> { // Opponent says WE crashed
                            localPlayerState.isAlive = false
                            handleGameOver(crashedByLocal = true)
                        }

                        "opponent" -> { // Opponent says THEY crashed
                            opponentPlayerState.isAlive = false
                            handleGameOver(crashedByLocal = false)
                        }
                    }
                }
            }

            "resetGame" -> {
                resetGame()
            }

            "game_running_status" -> { // NEW: Handle game running status
                if (parts.size == 2) {
                    try {
                        val receivedIsRunning = parts[1].toBoolean()
                        _gameState.update { it.copy(isRunning = receivedIsRunning) }
                        if (receivedIsRunning) {
                            // If the game is resumed by the other player, and we are not already running,
                            // we need to potentially restart our game loop.
                            // The LaunchedEffect in GameScreenUI will handle starting the loop
                            // when `isRunning` becomes true.
                        } else {
                            // If the game is paused by the other player, stop our loop.
                            stopGameLoop()
                        }
                    } catch (e: IllegalArgumentException) {
                        println("Error parsing game_running_status: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleGameOver(crashedByLocal: Boolean) {
        stopGameLoop()
        _gameState.update {
            if (_gameState.value.isSinglePlayer) {
                // In single player, if local player crashed, it's game over
                it.copy(
                    isGameOver = true,
                    isRunning = false,
                    gameOverMessage = "Game Over! You crashed!",
                    localIsAlive = false
                )
            } else {
                // Multiplayer logic
                if (crashedByLocal) {
                    val message =
                        if (!opponentPlayerState.isAlive) "Draw! Both players crashed!" else "You Lose! You crashed!"
                    it.copy(
                        isGameOver = true,
                        isRunning = false,
                        gameOverMessage = message,
                        localIsAlive = false
                    )
                } else {
                    val message =
                        if (!localPlayerState.isAlive) "Draw! Both players crashed!" else "You Win! Opponent crashed!"
                    it.copy(
                        isGameOver = true,
                        isRunning = false,
                        gameOverMessage = message,
                        opponentIsAlive = false
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Only stop Nearby Connections if not in a single player game mode
        if (!_gameState.value.isSinglePlayer) {
            nearbyConnectionsManager.stopAllEndpoints()
        }
        stopGameLoop()
    }
}