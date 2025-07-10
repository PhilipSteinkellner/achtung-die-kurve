package com.example.achtungdiekurve.game

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.achtungdiekurve.data.BoostState
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.data.GameUiState
import com.example.achtungdiekurve.data.MultiplayerState
import com.example.achtungdiekurve.data.PlayerState
import com.example.achtungdiekurve.data.PlayerUiState
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
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val hostPlayerState = PlayerState()
    private val clientPlayerState = PlayerState()

    private var gameLoopJob: Job? = null
    private var connectedEndpointId: String? = null

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
        observeConnectionState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            nearbyConnectionsManager.connectionState.collect { connectionState ->
                if (_uiState.value.multiplayerState.isMultiplayer) {
                    handleMultiplayerStateChange(connectionState)
                }
            }
        }
    }

    private fun handleMultiplayerStateChange(connectionState: ConnectionState) {
        val newMultiplayerState = when (connectionState) {
            is ConnectionState.Connected -> {
                connectedEndpointId = connectionState.endpointId
                _uiState.value.multiplayerState.copy(
                    connectionStatus = "Connected!", showSetupScreen = false
                )
            }

            is ConnectionState.Disconnected -> {
                resetGame()
                _uiState.value.multiplayerState.copy(
                    connectionStatus = "Disconnected", showSetupScreen = true, isHost = false
                )
            }

            is ConnectionState.Error -> _uiState.value.multiplayerState.copy(
                connectionStatus = "Error: ${connectionState.message}", showSetupScreen = true
            )

            is ConnectionState.Status -> _uiState.value.multiplayerState.copy(connectionStatus = connectionState.message)
            ConnectionState.Advertising -> _uiState.value.multiplayerState.copy(
                connectionStatus = "Waiting for players...", showSetupScreen = true
            )

            ConnectionState.Discovering -> _uiState.value.multiplayerState.copy(
                connectionStatus = "Searching for games...", showSetupScreen = true
            )

            ConnectionState.Connecting -> _uiState.value.multiplayerState.copy(
                connectionStatus = "Connecting...", showSetupScreen = true
            )
        }
        _uiState.update { it.copy(multiplayerState = newMultiplayerState) }
    }

    fun selectSinglePlayerMode() {
        _uiState.update {
            it.copy(
                multiplayerState = MultiplayerState(isMultiplayer = false, isHost = true),
                showModeSelection = false,
            )
        }
        resetGame()
    }

    fun selectMultiplayerMode() {
        _uiState.update {
            it.copy(
                multiplayerState = MultiplayerState(isMultiplayer = true, showSetupScreen = true),
                showModeSelection = false
            )
        }
        resetGame()
    }

    fun resetGameModeSelection() {
        stopGameLoop()
        nearbyConnectionsManager.stopAllEndpoints()
        _uiState.update {
            GameUiState(showModeSelection = true) // Reset to initial state
        }
        resetGame()
    }

    // --- Nearby Connections Callbacks ---
    private fun onNearbyConnected(endpointId: String, isHost: Boolean) {
        this.connectedEndpointId = endpointId
        _uiState.update {
            it.copy(
                multiplayerState = it.multiplayerState.copy(
                    isHost = isHost, showSetupScreen = false
                )
            )
        }
        resetGame()
    }

    private fun onNearbyDisconnected() {
        // Handled by the state flow collector
    }

    private fun isHost(): Boolean {
        return uiState.value.multiplayerState.isHost
    }

    private fun isMultiplayer(): Boolean {
        return uiState.value.multiplayerState.isMultiplayer
    }

    // --- Game Controls ---
    fun setLocalTurning(turning: Float) {
        if (isHost()) {
            hostPlayerState.turning = turning
        } else {
            clientPlayerState.turning = turning
            connectedEndpointId?.let { endpoint ->
                nearbyConnectionsManager.sendGameData("client_input:turning:$turning")
            }
        }
    }

    fun toggleBoost(mode: BoostState) {
        if (isHost()) {
            // Host toggles its own boost
            if (hostPlayerState.isAlive && hostPlayerState.boostState == BoostState.READY) {
                hostPlayerState.boostState = mode
                hostPlayerState.boostFrames = 0
            }
        } else {
            // Client sends boost toggle to host
            if (clientPlayerState.isAlive && clientPlayerState.boostState == BoostState.READY) {
                // Client's boost state is speculative until confirmed by host, but update locally for responsiveness
                clientPlayerState.boostState = mode
                clientPlayerState.boostFrames = 0
                connectedEndpointId?.let { endpoint ->
                    nearbyConnectionsManager.sendGameData("client_input:boost:$mode")
                }
            }
        }
    }

    fun toggleGameRunning() {
        val newIsRunning = !_uiState.value.isRunning
        _uiState.update { it.copy(isRunning = newIsRunning) }

        if (isMultiplayer()) {
            if (isHost()) {
                // Host sends status to client
                nearbyConnectionsManager.sendGameData("game_running_status:$newIsRunning")
            } else {
                // Client requests host to toggle status
                nearbyConnectionsManager.sendGameData("request_game_running_status:$newIsRunning")
            }
        } else {
            if (!newIsRunning) stopGameLoop()
        }
    }

    fun resetGameRound() {
        if (isHost()) {
            resetGame()
            nearbyConnectionsManager.sendGameData("resetGame:")
        } else {
            // Client requests host to reset
            nearbyConnectionsManager.sendGameData("request_reset_game:")
        }

    }

    fun startHostingGame() {
        if (!isMultiplayer()) return
        nearbyConnectionsManager.startHosting()
    }

    fun startJoiningGame() {
        if (!isMultiplayer()) return
        nearbyConnectionsManager.startDiscovery()
    }

    // --- Game Logic ---
    private fun resetGame() {
        stopGameLoop()
        hostPlayerState.reset()
        clientPlayerState.reset()
        _uiState.update {
            it.copy(
                localPlayer = PlayerUiState(),
                opponentPlayer = PlayerUiState(),
                isRunning = false,
                isGameOver = false,
                gameOverMessage = ""
            )
        }
    }

    fun initializeGamePositions(screenWidthPx: Float, screenHeightPx: Float) {

        if (!isHost()) return

        // Update screen dimensions in UI state
        _uiState.update {
            it.copy(
                screenWidthPx = screenWidthPx, screenHeightPx = screenHeightPx
            )
        }

        // Only initialize if trails are empty to prevent re-initialization on config changes
        if (hostPlayerState.trail.isNotEmpty() || clientPlayerState.trail.isNotEmpty()) return

        fun randomOffset(): Offset {
            val x =
                Random.nextFloat() * (screenWidthPx - 2 * GameConstants.SPAWN_MARGIN) + GameConstants.SPAWN_MARGIN
            val y =
                Random.nextFloat() * (screenHeightPx - 2 * GameConstants.SPAWN_MARGIN) + GameConstants.SPAWN_MARGIN
            return Offset(x, y)
        }

        // host defines initial state for both
        val hostStartPos = randomOffset()
        val hostStartDir = Random.nextFloat() * 2 * PI.toFloat()

        val clientStartPos = randomOffset()
        val clientStartDir = Random.nextFloat() * 2 * PI.toFloat()

        hostPlayerState.trail.add(TrailSegment(hostStartPos, isGap = false))
        hostPlayerState.direction = hostStartDir

        clientPlayerState.trail.add(TrailSegment(clientStartPos, isGap = false))
        clientPlayerState.direction = clientStartDir

        // Update UI based on who 'local' and 'opponent' are for this device
        _uiState.update {
            it.copy(
                localPlayer = hostPlayerState.toUiState(
                    true,
                ), opponentPlayer = clientPlayerState.toUiState(
                    false,
                )
            )
        }

    }

    fun startGameLoop(screenWidthPx: Float, screenHeightPx: Float) {
        if (gameLoopJob?.isActive == true) return
        if (!isHost()) {
            // Clients do not run the game loop directly
            return
        }

        gameLoopJob = viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            while (_uiState.value.isRunning) {
                delay(GameConstants.GAME_TICK_RATE_MS)
                updateGame(screenWidthPx, screenHeightPx)
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        if (_uiState.value.isRunning) {
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    // This is the core game logic, only run on the host
    private fun updateGame(screenWidthPx: Float, screenHeightPx: Float) {
        if (!hostPlayerState.isAlive && !clientPlayerState.isAlive) {
            // Both crashed, game is over.
            stopGameLoop()
            handleGameOver(crashedByLocal = true, crashedByOpponent = true) // Indicate both crashed
            return
        }

        // Update Host's player state
        if (hostPlayerState.isAlive) {
            updatePlayerState(hostPlayerState)
        }

        // Update Client's player state (on host, apply client's received input)
        if (_uiState.value.multiplayerState.isMultiplayer && clientPlayerState.isAlive) {
            updatePlayerState(
                clientPlayerState
            ) // Client's input already applied by handler
        }

        // Check for collisions AFTER all players have moved
        // Host collision check for self
        if (hostPlayerState.isAlive && checkForCollisions(
                hostPlayerState.trail.last().position,
                hostPlayerState,
                clientPlayerState,
                screenWidthPx,
                screenHeightPx
            )
        ) {
            hostPlayerState.isAlive = false
            handleCrash(crashedHost = true, crashedClient = false)
            return // Game over, stop further processing
        }

        // Host collision check for client
        if (_uiState.value.multiplayerState.isMultiplayer && clientPlayerState.isAlive && checkForCollisions(
                clientPlayerState.trail.last().position,
                clientPlayerState,
                hostPlayerState,
                screenWidthPx,
                screenHeightPx
            )
        ) {
            clientPlayerState.isAlive = false
            handleCrash(crashedHost = false, crashedClient = true)
            return // Game over, stop further processing
        }

        // Host sends full game state to client
        if (_uiState.value.multiplayerState.isMultiplayer && connectedEndpointId != null) {
            val gameStateString = serializeGameState(hostPlayerState, clientPlayerState)
            nearbyConnectionsManager.sendGameData("game_state_sync:$gameStateString")
        }

        // Update UI based on roles
        _uiState.update {
            it.copy(
                localPlayer = if (isHost()) hostPlayerState.toUiState(
                    true,
                ) else clientPlayerState.toUiState(false),
                opponentPlayer = if (isHost()) clientPlayerState.toUiState(
                    false,
                ) else hostPlayerState.toUiState(true)
            )
        }
    }

    // isLocal is true for the player whose input is controlled by this device (on host, it's hostPlayerState; on client, it's clientPlayerState for sending input)
    private fun updatePlayerState(player: PlayerState) {
        // Update direction and position
        player.direction += player.turning * GameConstants.TURN_SPEED
        val speed =
            if (player.boostState == BoostState.BOOSTING) GameConstants.BOOSTED_SPEED else if (player.boostState == BoostState.BRAKING) GameConstants.BRAKING_SPEED else GameConstants.NORMAL_SPEED
        val nextPos = calculateNextPosition(player.trail.last().position, player.direction, speed)

        // Update gap logic
        player.gapCounter++
        if (player.isDrawing && player.gapCounter >= GameConstants.DRAW_DURATION_FRAMES) {
            player.isDrawing = false
            player.gapCounter = 0
        } else if (!player.isDrawing && player.gapCounter >= GameConstants.GAP_DURATION_FRAMES) {
            player.isDrawing = true
            player.gapCounter = 0
        }

        if (player.boostState == BoostState.BOOSTING || player.boostState == BoostState.BRAKING) {
            player.boostFrames++
            if (player.boostFrames >= GameConstants.BOOST_DURATION_FRAMES) {
                player.boostState = BoostState.COOLDOWN
                player.boostFrames = 0
                player.boostCooldownFrames = GameConstants.BOOST_COOLDOWN_DURATION_FRAMES
            }
        } else if (player.boostState == BoostState.COOLDOWN) {
            player.boostCooldownFrames--
            if (player.boostCooldownFrames <= 0) {
                player.boostState = BoostState.READY
            }
        }

        // Add new segment
        player.trail.add(TrailSegment(nextPos, isGap = !player.isDrawing))
    }

    // Collision detection now considers both players' trails
    private fun checkForCollisions(
        pos: Offset, selfPlayer: PlayerState, otherPlayer: PlayerState, width: Float, height: Float
    ): Boolean {
        val radius = GameConstants.STROKE_WIDTH * GameConstants.COLLISION_RADIUS_MULTIPLIER

        // Boundary collision
        if (pos.x < 0 || pos.x > width || pos.y < 0 || pos.y > height) return true

        // Self-collision (ignore recent segments)
        val selfTrail = selfPlayer.trail
        if (selfTrail.size > GameConstants.MIN_SEGMENTS_FOR_SELF_COLLISION) { // Use a constant
            for (i in 0 until selfTrail.size - GameConstants.MIN_SEGMENTS_FOR_SELF_COLLISION) {
                if (!selfTrail[i].isGap && !selfTrail[i + 1].isGap && distanceFromPointToSegment(
                        pos, selfTrail[i].position, selfTrail[i + 1].position
                    ) < radius
                ) {
                    return true
                }
            }
        }

        // Opponent collision (if multiplayer)
        if (_uiState.value.multiplayerState.isMultiplayer) {
            val opponentTrail = otherPlayer.trail
            if (opponentTrail.size > 1) {
                for (i in 0 until opponentTrail.size - 1) {
                    if (!opponentTrail[i].isGap && !opponentTrail[i + 1].isGap && distanceFromPointToSegment(
                            pos, opponentTrail[i].position, opponentTrail[i + 1].position
                        ) < radius
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    // Host calls this when a crash occurs
    private fun handleCrash(crashedHost: Boolean, crashedClient: Boolean) {
        if (crashedHost) hostPlayerState.isAlive = false
        if (crashedClient) clientPlayerState.isAlive = false

        if (_uiState.value.multiplayerState.isMultiplayer) {
            // Host informs client about crash status
            nearbyConnectionsManager.sendGameData("game_over_status:$crashedHost,$crashedClient")
        }

        // Determine actual game over state
        val isGameOverNow = !hostPlayerState.isAlive || !clientPlayerState.isAlive

        if (isGameOverNow) {
            stopGameLoop()
            handleGameOver(crashedByLocal = crashedHost, crashedByOpponent = crashedClient)
        }
    }


    private fun handleGameDataReceived(message: String) {

        val parts =
            message.split(":", limit = 2) // Limit to 2 parts to keep full data string if present
        val type = parts[0]
        val data = parts.getOrNull(1)

        when (type) {
            // Client receives full game state from host
            "game_state_sync" -> if (!isHost() && data != null) handleGameStateSync(data)
            // Host receives client input
            "client_input" -> if (isHost() && data != null) handleClientInput(data)
            // Host receives request to change game running status
            "request_game_running_status" -> if (isHost() && data != null) {
                val requestedStatus = data.toBoolean()
                _uiState.update { it.copy(isRunning = requestedStatus) }
                if (requestedStatus) {
                    startGameLoop(
                        _uiState.value.screenWidthPx, _uiState.value.screenHeightPx
                    ) // Host starts its loop
                } else {
                    stopGameLoop()
                }
                nearbyConnectionsManager.sendGameData("game_running_status:$requestedStatus") // Host broadcasts confirmation
            }
            // Client receives game running status from host
            "game_running_status" -> if (!isHost() && data != null) handleGameRunningStatus(data.toBoolean())
            // Host receives request to reset game
            "request_reset_game" -> if (isHost()) {
                resetGame()
                nearbyConnectionsManager.sendGameData("resetGame:") // Host broadcasts confirmation
            }
            // Client receives reset game command from host
            "resetGame" -> if (!isHost()) resetGame()
            // Client receives game over status from host
            "game_over_status" -> if (!isHost() && data != null) handleRemoteGameOverStatus(data)
        }
    }

    // Host to client: full game state sync
    private fun serializeGameState(hostState: PlayerState, clientState: PlayerState): String {
        val hostData =
            "${hostState.trail.lastOrNull()?.position?.x ?: 0f}," + "${hostState.trail.lastOrNull()?.position?.y ?: 0f}," + "${hostState.direction},${hostState.isDrawing},${hostState.isAlive},${hostState.boostState.ordinal}," + "${hostState.boostCooldownFrames}"

        val clientData =
            "${clientState.trail.lastOrNull()?.position?.x ?: 0f}," + "${clientState.trail.lastOrNull()?.position?.y ?: 0f}," + "${clientState.direction},${clientState.isDrawing},${clientState.isAlive},${clientState.boostState.ordinal}," + "${clientState.boostCooldownFrames}"
        return "$hostData;$clientData"
    }

    private fun handleGameStateSync(data: String) {
        if (isHost()) return // Host doesn't process incoming game state syncs

        try {
            val playerStates = data.split(";")
            val hostData = playerStates[0].split(",")
            val clientData = playerStates[1].split(",")

            // Update hostPlayerState (which is the opponent from client's perspective)
            val newHostPos = Offset(hostData[0].toFloat(), hostData[1].toFloat())
            if (hostPlayerState.trail.isEmpty() || (newHostPos - hostPlayerState.trail.last().position).getDistance() > 1f) {
                hostPlayerState.trail.add(
                    TrailSegment(
                        newHostPos, isGap = !hostData[3].toBoolean()
                    )
                )
            }
            hostPlayerState.direction = hostData[2].toFloat()
            hostPlayerState.isDrawing = hostData[3].toBoolean()
            hostPlayerState.isAlive = hostData[4].toBoolean()
            hostPlayerState.boostState = BoostState.entries.toTypedArray()[hostData[5].toInt()]
            hostPlayerState.boostCooldownFrames = hostData[6].toInt()


            // Update clientPlayerState (which is local from client's perspective)
            val newClientPos = Offset(clientData[0].toFloat(), clientData[1].toFloat())
            if (clientPlayerState.trail.isEmpty() || (newClientPos - clientPlayerState.trail.last().position).getDistance() > 1f) {
                clientPlayerState.trail.add(
                    TrailSegment(
                        newClientPos, isGap = !clientData[3].toBoolean()
                    )
                )
            }
            clientPlayerState.direction = clientData[2].toFloat()
            clientPlayerState.isDrawing = clientData[3].toBoolean()
            clientPlayerState.isAlive = clientData[4].toBoolean()
            clientPlayerState.boostState = BoostState.entries.toTypedArray()[clientData[5].toInt()]
            clientPlayerState.boostCooldownFrames = clientData[6].toInt()

            _uiState.update {
                it.copy(
                    localPlayer = clientPlayerState.toUiState(false), // Client's perspective
                    opponentPlayer = hostPlayerState.toUiState(true), // Client's perspective
                    isGameOver = !hostPlayerState.isAlive || !clientPlayerState.isAlive
                )
            }
        } catch (e: Exception) {
            println("Error parsing game_state_sync data: ${e.message}")
        }
    }

    // Client to host: player input
    private fun handleClientInput(data: String) {
        val parts = data.split(":")
        when (parts[0]) {
            "turning" -> clientPlayerState.turning = parts[1].toFloat()
            "boost" -> {
                if (clientPlayerState.isAlive && clientPlayerState.boostState == BoostState.READY) {
                    clientPlayerState.boostState = BoostState.valueOf(parts[1])
                    clientPlayerState.boostFrames = 0
                }
            }
        }
    }

    private fun handleGameRunningStatus(receivedIsRunning: Boolean) {
        _uiState.update { it.copy(isRunning = receivedIsRunning) }
        if (!receivedIsRunning) {
            stopGameLoop() // Client stops its local "loop" (which isn't running game logic, but could be for responsiveness)
        }
    }

    // Client receives game over status from host
    private fun handleRemoteGameOverStatus(data: String) {
        val parts = data.split(",")
        val crashedHost = parts[0].toBoolean()
        val crashedClient = parts[1].toBoolean()

        hostPlayerState.isAlive = !crashedHost // Update host player's alive status
        clientPlayerState.isAlive = !crashedClient // Update client player's alive status

        stopGameLoop() // Ensure client's local loop (if any) stops
        handleGameOver(crashedByLocal = crashedClient, crashedByOpponent = crashedHost)
    }

    // Consolidated game over logic
    private fun handleGameOver(crashedByLocal: Boolean, crashedByOpponent: Boolean) {
        stopGameLoop()
        val isSinglePlayer = !_uiState.value.multiplayerState.isMultiplayer

        val message = when {
            isSinglePlayer -> "Game Over! You crashed!"
            crashedByLocal && crashedByOpponent -> "Draw! Both players crashed!"
            // In multiplayer, 'local' refers to the player controlled by THIS device.
            // On host: crashedByLocal is hostPlayerState, crashedByOpponent is clientPlayerState
            // On client: crashedByLocal is clientPlayerState, crashedByOpponent is hostPlayerState
            isHost() && crashedByLocal -> "You Lose! You crashed!"
            isHost() && crashedByOpponent -> "You Win! Opponent crashed!"
            !isHost() && crashedByLocal -> "You Lose! You crashed!"
            !isHost() && crashedByOpponent -> "You Win! Opponent crashed!"
            else -> "Game Over!" // Fallback
        }

        _uiState.update {
            it.copy(
                isGameOver = true,
                gameOverMessage = message,
                localPlayer = if (isHost()) hostPlayerState.toUiState(
                    true,
                ) else clientPlayerState.toUiState(false),
                opponentPlayer = if (isHost()) clientPlayerState.toUiState(
                    false,
                ) else hostPlayerState.toUiState(true)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        nearbyConnectionsManager.stopAllEndpoints()
        stopGameLoop()
    }
}

// --- Helper Extensions ---
private fun PlayerState.reset() {
    trail.clear()
    turning = 0f
    isDrawing = true
    gapCounter = 0
    boostState = BoostState.READY
    boostFrames = 0
    boostCooldownFrames = 0
    isAlive = true
    direction = 0f
}

private fun PlayerState.toUiState(isHost: Boolean): PlayerUiState {
    val color = when {
        !this.isAlive -> Color.Gray
        this.boostState == BoostState.BOOSTING -> Color.Magenta
        this.boostState == BoostState.BRAKING -> Color.Magenta
        isHost -> Color.Blue
        else -> Color.Red
    }
    return PlayerUiState(
        trail = this.trail.toList(),
        isAlive = this.isAlive,
        boostState = this.boostState,
        boostCooldownFrames = this.boostCooldownFrames,
        color = color
    )
}