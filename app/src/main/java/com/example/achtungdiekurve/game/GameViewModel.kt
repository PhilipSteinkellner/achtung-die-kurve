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

    private val localPlayerState = PlayerState()
    private val opponentPlayerState = PlayerState()
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
                _uiState.value.multiplayerState.copy(connectionStatus = "Connected!", showSetupScreen = false)
            }
            is ConnectionState.Disconnected -> {
                resetGame()
                _uiState.value.multiplayerState.copy(connectionStatus = "Disconnected", showSetupScreen = true, isHost = false)
            }
            is ConnectionState.Error -> _uiState.value.multiplayerState.copy(connectionStatus = "Error: ${connectionState.message}", showSetupScreen = true)
            is ConnectionState.Status -> _uiState.value.multiplayerState.copy(connectionStatus = connectionState.message)
            ConnectionState.Advertising -> _uiState.value.multiplayerState.copy(connectionStatus = "Waiting for players...", showSetupScreen = true)
            ConnectionState.Discovering -> _uiState.value.multiplayerState.copy(connectionStatus = "Searching for games...", showSetupScreen = true)
            ConnectionState.Connecting -> _uiState.value.multiplayerState.copy(connectionStatus = "Connecting...", showSetupScreen = true)
        }
        _uiState.update { it.copy(multiplayerState = newMultiplayerState) }
    }

    // --- Mode Selection ---
    fun selectSinglePlayerMode() {
        _uiState.update {
            it.copy(
                multiplayerState = MultiplayerState(isMultiplayer = false),
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
            it.copy(multiplayerState = it.multiplayerState.copy(
                isHost = isHost,
                showSetupScreen = false
            ))
        }
        resetGame()
    }

    private fun onNearbyDisconnected() {
        // Handled by the state flow collector
    }

    // --- Game Controls ---
    fun setLocalTurning(turning: Float) {
        localPlayerState.turning = turning
    }

    fun toggleBoost() {
        if (localPlayerState.isAlive && localPlayerState.boostState == BoostState.READY) {
            localPlayerState.boostState = BoostState.BOOSTING
            localPlayerState.boostFrames = 0
        }
    }

    fun toggleGameRunning() {
        val newIsRunning = !_uiState.value.isRunning
        _uiState.update { it.copy(isRunning = newIsRunning) }

        if (_uiState.value.multiplayerState.isMultiplayer) {
            nearbyConnectionsManager.sendGameData("game_running_status:$newIsRunning")
        }
    }

    fun resetGameRound() {
        if (_uiState.value.multiplayerState.isMultiplayer) {
            nearbyConnectionsManager.sendGameData("resetGame:")
        }
        resetGame()
    }

    fun startHostingGame() {
        if (!_uiState.value.multiplayerState.isMultiplayer) return
        nearbyConnectionsManager.startHosting()
    }

    fun startJoiningGame() {
        if (!_uiState.value.multiplayerState.isMultiplayer) return
        nearbyConnectionsManager.startDiscovery()
    }

    // --- Game Logic ---
    private fun resetGame() {
        stopGameLoop()
        localPlayerState.reset()
        opponentPlayerState.reset()
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
        if (localPlayerState.trail.isNotEmpty()) return

        val isMultiplayer = _uiState.value.multiplayerState.isMultiplayer
        val isHost = _uiState.value.multiplayerState.isHost

        val startPos: Offset
        val startDir: Float

        if (!isMultiplayer) {
            // Single player starts in a random position
            startPos = Offset(Random.nextFloat() * screenWidthPx, Random.nextFloat() * screenHeightPx)
            startDir = Random.nextFloat() * 2 * PI.toFloat()
        } else {
            // Multiplayer fixed positions
            startPos = if (isHost) Offset(screenWidthPx / 4f, screenHeightPx / 2f) else Offset(3 * screenWidthPx / 4f, screenHeightPx / 2f)
            startDir = if (isHost) 0f else PI.toFloat() // Host right, Client left
        }

        localPlayerState.trail.add(TrailSegment(startPos, isGap = false))
        localPlayerState.direction = startDir
        _uiState.update { it.copy(localPlayer = localPlayerState.toUiState(isHost, isMultiplayer)) }
    }

    fun startGameLoop(screenWidthPx: Float, screenHeightPx: Float) {
        if (gameLoopJob?.isActive == true) return
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

    private fun updateGame(screenWidthPx: Float, screenHeightPx: Float) {
        if (!localPlayerState.isAlive) {
            stopGameLoop()
            return
        }

        updatePlayerState(localPlayerState, screenWidthPx, screenHeightPx)

        if (_uiState.value.multiplayerState.isMultiplayer) {
            val (posX, posY) = localPlayerState.trail.last().position
            nearbyConnectionsManager.sendGameData(
                "player_sync:$posX,$posY,${localPlayerState.direction},${localPlayerState.isDrawing},${localPlayerState.turning},${localPlayerState.boostState.ordinal}"
            )
        }

        _uiState.update {
            it.copy(
                localPlayer = localPlayerState.toUiState(it.multiplayerState.isHost, it.multiplayerState.isMultiplayer),
                opponentPlayer = opponentPlayerState.toUiState(!it.multiplayerState.isHost, it.multiplayerState.isMultiplayer)
            )
        }
    }

    private fun updatePlayerState(player: PlayerState, width: Float, height: Float) {
        // Update direction and position
        player.direction += player.turning * GameConstants.TURN_SPEED
        val speed = if (player.boostState == BoostState.BOOSTING) GameConstants.BOOSTED_SPEED else GameConstants.NORMAL_SPEED
        val nextPos = calculateNextPosition(player.trail.last().position, player.direction, speed)

        // Check for collisions
        if (checkForCollisions(nextPos, width, height)) {
            handleCrash(crashedByLocal = true)
            return
        }

        // Update gap logic
        player.gapCounter++
        if (player.isDrawing && player.gapCounter >= GameConstants.DRAW_DURATION_FRAMES) {
            player.isDrawing = false
            player.gapCounter = 0
        } else if (!player.isDrawing && player.gapCounter >= GameConstants.GAP_DURATION_FRAMES) {
            player.isDrawing = true
            player.gapCounter = 0
        }

        // Update boost logic
        when (player.boostState) {
            BoostState.BOOSTING -> {
                player.boostFrames++
                if (player.boostFrames >= GameConstants.BOOST_DURATION_FRAMES) {
                    player.boostState = BoostState.COOLDOWN
                    player.boostFrames = 0
                    player.boostCooldownFrames = GameConstants.BOOST_COOLDOWN_DURATION_FRAMES
                }
            }
            BoostState.COOLDOWN -> {
                player.boostCooldownFrames--
                if (player.boostCooldownFrames <= 0) {
                    player.boostState = BoostState.READY
                }
            }
            BoostState.READY -> { /* Do nothing */ }
        }

        // Add new segment
        player.trail.add(TrailSegment(nextPos, isGap = !player.isDrawing))
    }

    private fun checkForCollisions(pos: Offset, width: Float, height: Float): Boolean {
        val radius = GameConstants.STROKE_WIDTH * GameConstants.COLLISION_RADIUS_MULTIPLIER

        // Boundary collision
        if (pos.x < 0 || pos.x > width || pos.y < 0 || pos.y > height) return true

        // Self-collision (ignore recent segments)
        val selfTrail = localPlayerState.trail
        if (selfTrail.size > 5) {
            for (i in 0 until selfTrail.size - 5) {
                if (!selfTrail[i].isGap && !selfTrail[i + 1].isGap && distanceFromPointToSegment(pos, selfTrail[i].position, selfTrail[i + 1].position) < radius) {
                    return true
                }
            }
        }

        // Opponent collision (in multiplayer)
        val opponentTrail = opponentPlayerState.trail
        if (_uiState.value.multiplayerState.isMultiplayer && opponentTrail.size > 1) {
            for (i in 0 until opponentTrail.size - 1) {
                if (!opponentTrail[i].isGap && !opponentTrail[i + 1].isGap && distanceFromPointToSegment(pos, opponentTrail[i].position, opponentTrail[i + 1].position) < radius) {
                    return true
                }
            }
        }

        return false
    }

    private fun handleCrash(crashedByLocal: Boolean) {
        localPlayerState.isAlive = false
        if (_uiState.value.multiplayerState.isMultiplayer) {
            nearbyConnectionsManager.sendGameData("gameover:opponent") // Tell opponent we crashed
        }
        handleGameOver(crashedByLocal = true)
    }

    private fun handleGameDataReceived(message: String) {
        if (!_uiState.value.multiplayerState.isMultiplayer) return

        val parts = message.split(":")
        when (parts[0]) {
            "player_sync" -> handlePlayerSync(parts)
            "gameover" -> handleOpponentCrash()
            "resetGame" -> resetGame()
            "game_running_status" -> handleGameRunningStatus(parts)
        }
    }

    private fun handlePlayerSync(parts: List<String>) {
        try {
            val data = parts[1].split(",")
            val newOpponentPos = Offset(data[0].toFloat(), data[1].toFloat())
            opponentPlayerState.direction = data[2].toFloat()
            opponentPlayerState.isDrawing = data[3].toBoolean()
            opponentPlayerState.turning = data[4].toFloat()
            opponentPlayerState.boostState = BoostState.values()[data[5].toInt()]

            if (opponentPlayerState.trail.isEmpty() || (newOpponentPos - opponentPlayerState.trail.last().position).getDistance() > 1f) {
                opponentPlayerState.trail.add(TrailSegment(newOpponentPos, isGap = !opponentPlayerState.isDrawing))
            }
        } catch (e: Exception) {
            println("Error parsing player_sync data: ${e.message}")
        }
    }

    private fun handleOpponentCrash() {
        opponentPlayerState.isAlive = false
        handleGameOver(crashedByLocal = false)
    }

    private fun handleGameRunningStatus(parts: List<String>) {
        try {
            val receivedIsRunning = parts[1].toBoolean()
            if (_uiState.value.isRunning != receivedIsRunning) {
                _uiState.update { it.copy(isRunning = receivedIsRunning) }
                if (!receivedIsRunning) {
                    stopGameLoop()
                }
            }
        } catch (e: Exception) {
            println("Error parsing game_running_status: ${e.message}")
        }
    }

    private fun handleGameOver(crashedByLocal: Boolean) {
        stopGameLoop()
        val isSinglePlayer = !_uiState.value.multiplayerState.isMultiplayer

        val message = when {
            isSinglePlayer -> "Game Over! You crashed!"
            crashedByLocal && !opponentPlayerState.isAlive -> "Draw! Both players crashed!"
            crashedByLocal -> "You Lose! You crashed!"
            !localPlayerState.isAlive -> "Draw! Both players crashed!"
            else -> "You Win! Opponent crashed!"
        }

        _uiState.update {
            it.copy(
                isGameOver = true,
                gameOverMessage = message,
                localPlayer = it.localPlayer.copy(isAlive = !crashedByLocal),
                opponentPlayer = it.opponentPlayer.copy(isAlive = crashedByLocal || opponentPlayerState.isAlive)
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
}

private fun PlayerState.toUiState(isHost: Boolean, isMultiplayer: Boolean): PlayerUiState {
    val color = when {
        !this.isAlive -> Color.Gray
        this.boostState == BoostState.BOOSTING -> Color.Magenta
        !isMultiplayer -> Color.Red // Single player is always red
        isHost -> Color.Red
        else -> Color.Green
    }
    return PlayerUiState(
        trail = this.trail.toList(),
        isAlive = this.isAlive,
        boostState = this.boostState,
        boostCooldownFrames = this.boostCooldownFrames,
        color = color
    )
}