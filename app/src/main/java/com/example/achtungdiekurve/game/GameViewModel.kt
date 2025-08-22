package com.example.achtungdiekurve.game

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.achtungdiekurve.data.CollisionResult
import com.example.achtungdiekurve.data.CollisionType
import com.example.achtungdiekurve.data.ConfettiAnimation
import com.example.achtungdiekurve.data.GameConstants
import com.example.achtungdiekurve.data.GameState
import com.example.achtungdiekurve.data.LatestPlayerState
import com.example.achtungdiekurve.data.MatchState
import com.example.achtungdiekurve.data.MultiplayerState
import com.example.achtungdiekurve.data.PlayerState
import com.example.achtungdiekurve.data.SpecialMoveState
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random


class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    private val clientPlayerStates = mutableMapOf<String, PlayerState>()
    private val localPlayer = gameState.value.localPlayer.copy()
    private var gameLoopJob: Job? = null



    private val nearbyConnectionsManager: NearbyConnectionsManager by lazy {
        NearbyConnectionsManager(
            context = getApplication<Application>().applicationContext,
            serviceId = "com.example.achtungdiekurve",
            onGameDataReceived = ::handleGameDataReceived,
            onConnected = ::onNearbyConnected,
            onDisconnect = ::onNearbyDisconnected,
            onSearching = {
                _gameState.update {
                    it.copy(
                        multiplayerState = it.multiplayerState.copy(searching = true)
                    )
                }
            },
        )
    }

    init {
        observeConnectionState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            nearbyConnectionsManager.connectionState.collect { connectionState ->
                if (_gameState.value.multiplayerState.isMultiplayer) {
                    handleMultiplayerStateChange(connectionState)
                }
            }
        }
    }

    private fun handleMultiplayerStateChange(connectionState: ConnectionState) {
        val newMultiplayerState = when (connectionState) {
            is ConnectionState.Connected -> {
                _gameState.value.multiplayerState.copy(
                    connectionStatus = "Connected!",
                )
            }

            is ConnectionState.Disconnected -> {
                resetGame()
                _gameState.value.multiplayerState.copy(
                    connectionStatus = "Disconnected", isHost = false
                )
            }

            is ConnectionState.Error -> _gameState.value.multiplayerState.copy(
                connectionStatus = "Error: ${connectionState.message}"
            )

            is ConnectionState.Status -> _gameState.value.multiplayerState.copy(connectionStatus = connectionState.message)
            ConnectionState.Advertising -> _gameState.value.multiplayerState.copy(
                connectionStatus = "Waiting for players...",
            )

            ConnectionState.Discovering -> _gameState.value.multiplayerState.copy(
                connectionStatus = "Searching for games...",
            )

            ConnectionState.Connecting -> _gameState.value.multiplayerState.copy(
                connectionStatus = "Connecting...",
            )
        }
        _gameState.update { it.copy(multiplayerState = newMultiplayerState) }
    }

    fun selectSinglePlayerMode() {
        _gameState.update {
            it.copy(
                multiplayerState = MultiplayerState(isMultiplayer = false, isHost = true),
                matchState = MatchState.MATCH_SETTINGS
            )
        }
        resetGame()
    }

    fun selectMultiplayerMode() {
        _gameState.update {
            it.copy(
                multiplayerState = MultiplayerState(isMultiplayer = true)
            )
        }
        resetGame()
    }

    fun resetGameModeSelection() {
        stopGameLoop()
        nearbyConnectionsManager.stopAllEndpoints()
        _gameState.update {
            GameState(matchState = MatchState.SETUP) // Reset to initial state
        }
        resetGame()
    }

    fun changeMatchState(matchState: MatchState) {
        _gameState.update {
            it.copy(
                matchState = matchState
            )
        }
        sendMatchState()
    }

    // --- Nearby Connections Callbacks ---
    private fun onNearbyConnected(endpointId: String, endpointName: String, isHost: Boolean) {
        _gameState.update {
            it.copy(
                multiplayerState = it.multiplayerState.copy(
                    isHost = isHost,
                ),
            )
        }

        if (isHost) {
            clientPlayerStates[endpointId] = PlayerState(
                color = GameConstants.COLORS.first { c -> clientPlayerStates.none { it.value.color == c } },
                id = endpointId,
                name = if (endpointName.matches(Regex("^Google.*"))) endpointId else endpointName
            )
            _gameState.update {
                it.copy(
                    multiplayerState = it.multiplayerState.copy(
                        connectedEndpoints = clientPlayerStates.values.joinToString(", ") { p -> p.name })
                )
            }
            resetGame()
        } else {
            _gameState.update {
                it.copy(
                    multiplayerState = it.multiplayerState.copy(
                        searching = false
                    )

                )
            }
        }
    }

    private fun onNearbyDisconnected(endpointId: String) {
        clientPlayerStates.remove(endpointId)
    }

    private fun isHost(): Boolean {
        return gameState.value.multiplayerState.isHost
    }

    private fun isMultiplayer(): Boolean {
        return gameState.value.multiplayerState.isMultiplayer
    }

    // --- Game Controls ---
    fun setLocalTurning(turning: Float) {
        if (isHost()) {
            localPlayer.turning = turning
        } else {
            nearbyConnectionsManager.sendGameData("client_input:turning:$turning")
        }
    }

    fun toggleBoost(mode: SpecialMoveState) {
        if (isHost()) {
            // Host toggles its own boost
            if (localPlayer.isAlive && localPlayer.boostState == SpecialMoveState.READY) {
                localPlayer.boostState = mode
                localPlayer.boostFrames = 0
            }
        } else {
            nearbyConnectionsManager.sendGameData("client_input:boost:$mode")
        }
    }

    fun toggleGameRunning() {
        if (!isHost()) return

        val newIsRunning = !_gameState.value.isRunning
        _gameState.update { it.copy(isRunning = newIsRunning) }

        if (newIsRunning) {
            startGameLoop()
        } else {
            stopGameLoop()
        }

        nearbyConnectionsManager.sendGameData("game_running_status:$newIsRunning")
    }

    fun resetGameRound() {
        if (isHost()) {
            stopGameLoop()

            // If match is over, start a new match
            if (_gameState.value.matchState == MatchState.GAME_OVER) {
                // Full reset including scores
                localPlayer.reset()
                clientPlayerStates.forEach { it.value.reset() }
                _gameState.update {
                    it.copy(
                        localPlayer = localPlayer.toUiState(),
                        opponents = clientPlayerStates.values.map { it.toUiState() },
                        matchState = MatchState.RUNNING
                    )
                }
                // Tell client to do a full reset
                nearbyConnectionsManager.sendGameData("reset_match:")
            } else {
                // Just reset for new round, keep scores
                localPlayer.resetForNewRound()
                clientPlayerStates.forEach { it.value.resetForNewRound() }
                _gameState.update {
                    it.copy(
                        // Keep the scores in UI
                        localPlayer = localPlayer.toUiState(),
                        opponents = clientPlayerStates.values.map { it.toUiState() },
                        matchState = MatchState.RUNNING
                    )
                }
                // Tell client to reset for new round only
                nearbyConnectionsManager.sendGameData("reset_round:")
            }
            sendMatchState()
            // Send the running status to client
            nearbyConnectionsManager.sendGameData("game_running_status:true")

            startGameLoop()
        } else {
            // Client requests host to reset
            nearbyConnectionsManager.sendGameData("request_reset_game:")
        }
    }

    fun startNewMatch() {
        if (!isHost()) return

        // Reset scores and start fresh
        localPlayer.reset()
        clientPlayerStates.forEach { it.value.reset() }

        _gameState.update {
            it.copy(
                localPlayer = localPlayer.toUiState(),
                opponents = clientPlayerStates.values.map { it.toUiState() },
                matchState = MatchState.RUNNING
            )
        }

        // Tell client to reset everything
        nearbyConnectionsManager.sendGameData("reset_match:")
        nearbyConnectionsManager.sendGameData("game_running_status:true")
        sendMatchState()

        initializeGamePositions()
        startGameLoop()
    }

    fun setScoreToWin(score: Int) {
        if (!isHost()) return // Only host can set this
        _gameState.update { it.copy(scoreToWin = score) }
        // Inform the client of the new setting
        nearbyConnectionsManager.sendGameData("set_score_to_win:$score")
    }


    fun startHostingGame(nickname: String) {
        if (!isMultiplayer()) return
        localPlayer.name = nickname
        nearbyConnectionsManager.startHosting(nickname)
    }

    fun startJoiningGame(nickname: String) {
        if (!isMultiplayer()) return
        localPlayer.name = nickname
        nearbyConnectionsManager.startDiscovery(nickname)
    }

    // --- Game Logic ---
    private fun resetGame() {
        stopGameLoop()
        localPlayer.reset()
        clientPlayerStates.forEach { it.value.reset() }
        _gameState.update {
            it.copy(
                localPlayer = localPlayer.toUiState(),
                opponents = clientPlayerStates.values.map { it.toUiState() },
            )
        }
    }

    fun initializeGamePositions() {
        if (!isHost()) return

        val shouldInitialize =
            localPlayer.trail.isEmpty() || clientPlayerStates.all { it.value.trail.isEmpty() } || gameState.value.matchState != MatchState.RUNNING

        if (!shouldInitialize) return

        val takenPositions = mutableListOf<Offset>()
        val edgeMargin = GameConstants.SPAWN_EDGE_MARGIN
        val playerMargin = GameConstants.SPAWN_PLAYER_MARGIN

        fun randomOffset(): Offset {
            var pos: Offset
            var tries = 0
            do {
                val x =
                    Random.nextFloat() * (GameConstants.GAME_WORLD_WIDTH - 2 * edgeMargin) + edgeMargin
                val y =
                    Random.nextFloat() * (GameConstants.GAME_WORLD_HEIGHT - 2 * edgeMargin) + edgeMargin
                pos = Offset(x, y)
                tries++
            } while (takenPositions.any { other -> (pos - other).getDistance() < playerMargin } && tries < 100)
            takenPositions.add(pos)
            return pos
        }

        // Clear trails before adding new positions
        localPlayer.trail.clear()
        clientPlayerStates.forEach { it.value.trail.clear() }

        // Host player
        val hostStartPos = randomOffset()
        val hostStartDir = Random.nextFloat() * 2 * PI.toFloat()
        localPlayer.trail.add(TrailSegment(hostStartPos, isGap = false))
        localPlayer.direction = hostStartDir

        // Client players
        clientPlayerStates.forEach {
            val clientStartPos = randomOffset()
            val clientStartDir = Random.nextFloat() * 2 * PI.toFloat()
            it.value.trail.add(TrailSegment(clientStartPos, isGap = false))
            it.value.direction = clientStartDir
        }

        if (_gameState.value.multiplayerState.isMultiplayer) {
            val gameStateString = serializeFullState()
            for (clientPlayer in clientPlayerStates) {
                nearbyConnectionsManager.sendGameDataToEndpoint(
                    clientPlayer.key, "player_state_full_sync:${clientPlayer.key}:$gameStateString"
                )
            }
        }

        _gameState.update {
            it.copy(
                localPlayer = localPlayer.toUiState(),
                opponents = clientPlayerStates.values.map { it.toUiState() })
        }
    }


    fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        if (!isHost()) {
            // Clients do not run the game loop directly
            return
        }

        gameLoopJob = viewModelScope.launch {
            _gameState.update { it.copy(isRunning = true) }
            while (_gameState.value.isRunning) {
                delay(GameConstants.GAME_TICK_RATE_MS)
                updateGame()
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        if (_gameState.value.isRunning) {
            _gameState.update { it.copy(isRunning = false) }
        }
    }

    // This is the core game logic, only run on the host
    private fun updateGame() {
        if (_gameState.value.isPausedForCollision) {
            return
        }

        val alivePlayers =
            clientPlayerStates.values.toList().plus(localPlayer).filter { it.isAlive }.size

        if ((isMultiplayer() && alivePlayers <= 1) || (!isMultiplayer() && alivePlayers == 0)) {
            stopGameLoop()
            handleRoundEnd()
            return
        }

        // Update Host's player state
        if (localPlayer.isAlive) {
            updatePlayerState(localPlayer)
        }

        // Update Client's player state (on host, apply client's received input)
        if (_gameState.value.multiplayerState.isMultiplayer) {
            clientPlayerStates.forEach { if (it.value.isAlive) updatePlayerState(it.value) }
        }

        // Check for collisions AFTER all players have moved
        // Host collision check for self
        if (localPlayer.isAlive) {
            val allOtherPlayers = clientPlayerStates.values
            val collision = checkForCollisions(
                localPlayer, allOtherPlayers
            )
            if (collision != null) {
                // A collision occurred!
                localPlayer.isAlive = false
                localPlayer.lastCollision = collision
                _gameState.update {
                    it.copy(lastCollision = collision)
                }
                // Trigger collision animation at the impact point
                val impactPoint = localPlayer.trail.last().position
                handleCollisionAnimation(impactPoint)


                distributePoints()
                return
            }
        }

        // Host collision check for client
        if (_gameState.value.multiplayerState.isMultiplayer) {
            for ((key, clientPlayer) in clientPlayerStates) {
                if (clientPlayer.isAlive) {
                    val otherPlayers = clientPlayerStates.filterKeys { it != key }.values.toMutableList()
                    otherPlayers.add(localPlayer)

                    val collision = checkForCollisions(
                        clientPlayer, otherPlayers
                    )
                    if (collision != null) {
                        clientPlayer.isAlive = false
                        clientPlayer.lastCollision = collision
                        // Trigger collision animation
                        val impactPoint = clientPlayer.trail.last().position
                        handleCollisionAnimation(impactPoint)
                        distributePoints()
                        return
                    }
                }
            }
        }

        if (_gameState.value.multiplayerState.isMultiplayer) {
            val gameStateString = serializeGameState()
            for (clientPlayer in clientPlayerStates) {
                nearbyConnectionsManager.sendGameDataToEndpoint(
                    clientPlayer.key, "player_state_update:${clientPlayer.key}:$gameStateString"
                )
            }
        }

        // Update UI based on roles
        _gameState.update {
            it.copy(
                localPlayer = localPlayer.toUiState(),
                opponents = clientPlayerStates.values.map { it.toUiState() })
        }
    }

    private fun updatePlayerState(player: PlayerState) {
        // Update direction and position
        player.direction += player.turning * GameConstants.TURN_SPEED
        val speed =
            if (player.boostState == SpecialMoveState.BOOST) GameConstants.BOOST_SPEED else if (player.boostState == SpecialMoveState.SLOW) GameConstants.SLOW_SPEED else GameConstants.NORMAL_SPEED
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

        if (player.boostState == SpecialMoveState.BOOST || player.boostState == SpecialMoveState.SLOW) {
            player.boostFrames++
            if (player.boostFrames >= GameConstants.SPECIAL_MOVE_DURATION_FRAMES) {
                player.boostState = SpecialMoveState.COOLDOWN
                player.boostFrames = 0
                player.boostCooldownFrames = GameConstants.SPECIAL_MOVE_COOLDOWN_DURATION_FRAMES
            }
        } else if (player.boostState == SpecialMoveState.COOLDOWN) {
            player.boostCooldownFrames--
            if (player.boostCooldownFrames <= 0) {
                player.boostState = SpecialMoveState.READY
            }
        }

        // Add new segment
        player.trail.add(TrailSegment(nextPos, isGap = !player.isDrawing))
    }

    private fun checkForCollisions(
        selfPlayer: PlayerState, otherPlayers: Collection<PlayerState>
    ): CollisionResult? {
        val radius = GameConstants.STROKE_WIDTH.value * GameConstants.COLLISION_RADIUS_MULTIPLIER
        val pos = selfPlayer.trail.last().position

        // Boundary collision
        if (pos.x < 0) return CollisionResult(CollisionType.BOUNDARY, Offset(1f, 0f)) // Left wall normal
        if (pos.x > GameConstants.GAME_WORLD_WIDTH) return CollisionResult(CollisionType.BOUNDARY, Offset(-1f, 0f)) // Right wall normal
        if (pos.y < 0) return CollisionResult(CollisionType.BOUNDARY, Offset(0f, 1f)) // Top wall normal
        if (pos.y > GameConstants.GAME_WORLD_HEIGHT) return CollisionResult(CollisionType.BOUNDARY, Offset(0f, -1f)) // Bottom wall normal

        // --- Trail Collisions ---
        val allTrails = otherPlayers.map { it.trail }.toMutableList()
        allTrails.add(selfPlayer.trail)

        for (trail in allTrails) {
            val segmentsToIgnore = if (trail === selfPlayer.trail) {
                GameConstants.MIN_SEGMENTS_FOR_SELF_COLLISION
            } else {
                1 // For opponent trails, only ignore the very last segment
            }

            if (trail.size > segmentsToIgnore) {
                for (i in 0 until trail.size - segmentsToIgnore) {
                    val p1 = trail[i]
                    val p2 = trail[i + 1]

                    if (!p1.isGap && !p2.isGap &&
                        distanceFromPointToSegment(pos, p1.position, p2.position) < radius
                    ) {
                        val playerDir = Offset(cos(selfPlayer.direction), sin(selfPlayer.direction))
                        return CollisionResult(CollisionType.TRAIL, -playerDir)
                    }
                }
            }
        }

        return null // No collision
    }

    private fun distributePoints() {
        clientPlayerStates.values.forEach { if (it.isAlive) it.score++ }
        if (localPlayer.isAlive) localPlayer.score++
    }

    private fun handleGameDataReceived(endpointId: String, message: String) {
        val parts =
            message.split(":", limit = 2) // Limit to 2 parts to keep full data string if present
        val type = parts[0]
        val data = parts.getOrNull(1)

        when (type) {
            "player_state_full_sync" -> if (!isHost() && data != null) handlePlayerStateFullSync(data)
            "player_state_update" -> if (!isHost() && data != null) handlePlayerStateUpdate(data)
            "client_input" -> if (isHost() && data != null) handleClientInput(endpointId, data)
            "game_running_status" -> if (!isHost() && data != null) handleGameRunningStatus(data.toBoolean())
            "reset_round" -> if (!isHost()) resetGameForNewRound()
            "reset_match" -> if (!isHost()) resetGame()
            "round_over" -> if (!isHost() && data != null) handleRemoteRoundOver(data)
            "set_score_to_win" -> if (!isHost() && data != null) _gameState.update {
                it.copy(
                    scoreToWin = data.toInt()
                )
            }
            "collision_event" -> if (!isHost() && data != null) {
                val coords = data.split(",")
                if (coords.size == 2) {
                    val x = coords[0].toFloatOrNull()
                    val y = coords[1].toFloatOrNull()
                    if (x != null && y != null) {
                        handleCollisionAnimation(Offset(x, y))
                    }
                }
            }
            "match_state" -> if (!isHost() && data != null) handleMatchState(data)
        }
    }

    private fun resetGameForNewRound() {
        stopGameLoop()
        localPlayer.resetForNewRound()
        clientPlayerStates.forEach { it.value.resetForNewRound() }
        _gameState.update {
            it.copy(
                localPlayer = localPlayer.toUiState(),
                opponents = clientPlayerStates.values.map { it.toUiState() },
            )
        }
        initializeGamePositions()
    }

    private fun serializeFullState(): String {
        val playerStates = clientPlayerStates.values.toList().plus(localPlayer)
        val json = playerStates.map { Json.encodeToString(it) }
        return json.joinToString(";")
    }

    private fun serializeGameState(): String {
        val playerStates = clientPlayerStates.values.toList().plus(localPlayer)
        val latestPlayerStates = playerStates.map {
            LatestPlayerState(
                it.id, it.trail.last(), it.score, it.isAlive, it.boostState, it.lastCollision,it.boostCooldownFrames
            )
        }
        val json = latestPlayerStates.map { Json.encodeToString(it) }
        return json.joinToString(";")
    }

    private fun handlePlayerStateFullSync(data: String) {
        val parts = data.split(":", limit = 2)
        val localPlayerId = parts[0]
        val playersJson = parts[1]

        val playerStates: MutableList<PlayerState> = playersJson.split(";").map { Json.decodeFromString(it) }.toMutableList()
        val index = playerStates.indexOfFirst { it.id == localPlayerId }
        val localPlayerState = playerStates.removeAt(index)

        _gameState.update {
            it.copy(
                localPlayer = localPlayerState, opponents = playerStates
            )
        }
    }

    private fun handlePlayerStateUpdate(data: String) {
        val parts = data.split(":", limit = 2)
        val localPlayerId = parts[0]
        val playersJson = parts[1]

        val playerStates: List<LatestPlayerState> = playersJson.split(";").map { Json.decodeFromString<LatestPlayerState>(it) }
        val localPlayerState = playerStates.first { it.id == localPlayerId }
        val opponentStates = playerStates.filter { it.id != localPlayerId }

        _gameState.update { current ->
            val updatedLocalPlayer = current.localPlayer.copy(
                trail = (current.localPlayer.trail + localPlayerState.pos).toMutableList(),
                score = localPlayerState.score,
                isAlive = localPlayerState.isAlive,
                boostState = localPlayerState.boostState,
                lastCollision = localPlayerState.lastCollision
            )
            val updatedOpponents = current.opponents.map { opponent ->
                opponentStates.find { it.id == opponent.id }?.let { state ->
                    opponent.copy(
                        trail = (opponent.trail + state.pos).toMutableList(),
                        score = state.score,
                        isAlive = state.isAlive,
                        boostState = state.boostState,
                        lastCollision = state.lastCollision
                    )
                } ?: opponent
            }
            current.copy(
                localPlayer = updatedLocalPlayer,
                opponents = updatedOpponents
            )
        }
    }

    private fun handleClientInput(endpointId: String, data: String) {
        val parts = data.split(":")
        val playerState = clientPlayerStates[endpointId] ?: return
        when (parts[0]) {
            "turning" -> playerState.turning = parts[1].toFloat()
            "boost" -> {
                if (playerState.isAlive && playerState.boostState == SpecialMoveState.READY) {
                    playerState.boostState = SpecialMoveState.valueOf(parts[1])
                    playerState.boostFrames = 0
                }
            }
        }
    }

    private fun handleGameRunningStatus(receivedIsRunning: Boolean) {
        _gameState.update { it.copy(isRunning = receivedIsRunning) }
    }

    private fun handleRemoteRoundOver(data: String) {
        handleRoundEnd()
    }

    private fun handleMatchState(data: String) {
        _gameState.update {
            it.copy(
                matchState = MatchState.valueOf(data)
            )
        }
    }

    private fun handleRoundEnd() {
        stopGameLoop()

        if (isHost()) {
            val scoreToWin = _gameState.value.scoreToWin
            if (isMultiplayer() && (localPlayer.score >= scoreToWin || clientPlayerStates.values.any { it.score >= scoreToWin })) {
                handleMatchOver()
                return
            }
        }

        val isSinglePlayer = !_gameState.value.multiplayerState.isMultiplayer
        val message = when {
            isSinglePlayer -> "You crashed! Tap New Game to try again."
            else -> "Round Over!"
        }

        if (isHost() && isMultiplayer()) {
            nearbyConnectionsManager.sendGameData("round_over:$message")
        }

        _gameState.update {
            it.copy(
                matchState = MatchState.ROUND_OVER
            )
        }
    }

    private fun handleMatchOver() {
        _gameState.update {
            it.copy(
                matchState = MatchState.GAME_OVER
            )
        }
        sendMatchState()
    }

    fun sendMatchState() {
        nearbyConnectionsManager.sendGameData("match_state:${gameState.value.matchState}")
    }

    fun hideGameOver() {
        _gameState.update { it.copy() }
    }

    fun onScoreSetupStartGame() {
        startNewMatch()
    }

    fun onGameOverNewGame() {
        if (_gameState.value.matchState == MatchState.GAME_OVER) {
            _gameState.update {
                it.copy(
                    matchState = MatchState.MATCH_SETTINGS
                )
            }
        } else {
            resetGameRound()
            initializeGamePositions()
            hideGameOver()
        }
    }

    fun incrementScoreToWin() {
        setScoreToWin(_gameState.value.scoreToWin + 1)
    }

    fun decrementScoreToWin() {
        setScoreToWin((_gameState.value.scoreToWin - 1).coerceAtLeast(1))
    }

    override fun onCleared() {
        super.onCleared()
        nearbyConnectionsManager.stopAllEndpoints()
        stopGameLoop()
    }

    private fun handleCollisionAnimation(position: Offset) {
        viewModelScope.launch {
            if (isHost()) {
                nearbyConnectionsManager.sendGameData("collision_event:${position.x},${position.y}")
            }
            val animationId = "${System.currentTimeMillis()}_${position.x}_${position.y}"
            val zoomCenter = position

            _gameState.update {
                it.copy(
                    collisionAnimation = ConfettiAnimation(
                        position = position,
                        rotation = 0f,
                        id = animationId
                    ),
                    isPausedForCollision = true,
                    zoomScale = 1f,
                    zoomCenter = zoomCenter
                )
            }

            // Zoom in animation
            val zoomDuration = 50L
            val zoomSteps = 50
            val zoomDelay = zoomDuration / zoomSteps

            for (i in 1..zoomSteps) {
                val progress = i.toFloat() / zoomSteps
                val scale = 1f + (2f * progress) // Zoom to 3x
                _gameState.update {
                    it.copy(zoomScale = scale)
                }
                delay(zoomDelay)
            }

            delay(1000L)

            // Zoom out animation
            for (i in zoomSteps downTo 1) {
                val progress = i.toFloat() / zoomSteps
                val scale = 1f + (2f * progress)
                _gameState.update {
                    it.copy(zoomScale = scale)
                }
                delay(zoomDelay)
            }

            // Clear animation and resume
            _gameState.update {
                it.copy(
                    collisionAnimation = null,
                    isPausedForCollision = false,
                    zoomScale = 1f
                )
            }
        }
    }
}


// --- Helper Extensions ---
private fun PlayerState.reset() {
    resetForNewRound()
    score = 0
}

private fun PlayerState.resetForNewRound() {
    trail.clear()
    turning = 0f
    isDrawing = true
    gapCounter = 0
    boostState = SpecialMoveState.READY
    boostFrames = 0
    boostCooldownFrames = 0
    isAlive = true
    direction = 0f
    lastCollision = null



}

private fun PlayerState.toUiState(): PlayerState {
    return this.copy()
}