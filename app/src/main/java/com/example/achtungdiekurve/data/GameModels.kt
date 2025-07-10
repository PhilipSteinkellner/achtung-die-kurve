package com.example.achtungdiekurve.data

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

// Data class to represent a single point in a player's trail
data class TrailSegment(val position: Offset, val isGap: Boolean)

// Enum for different control modes
enum class ControlMode {
    TAP, TILT
}

// Enum for the player's boost state
enum class BoostState {
    READY, BOOSTING, COOLDOWN
}

// Data class for player-specific state that is relevant to the UI
data class PlayerUiState(
    val trail: List<TrailSegment> = emptyList(),
    val isAlive: Boolean = true,
    val boostState: BoostState = BoostState.READY,
    val boostCooldownFrames: Int = 0,
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Gray
)

// Data class for multiplayer-specific UI state
data class MultiplayerState(
    val isMultiplayer: Boolean = false,
    val isHost: Boolean = false,
    val connectionStatus: String = "Not Connected",
    val showSetupScreen: Boolean = false
)

// The main UI state class for the game screen
data class GameUiState(
    val localPlayer: PlayerUiState = PlayerUiState(),
    val opponentPlayer: PlayerUiState = PlayerUiState(),
    val multiplayerState: MultiplayerState = MultiplayerState(),
    val isRunning: Boolean = false,
    val isGameOver: Boolean = false,
    val gameOverMessage: String = "",
    val showModeSelection: Boolean = true,
    val screenWidthPx: Float = 0f, // New field
    val screenHeightPx: Float = 0f // New field

)

// Internal data class to hold the full state of a player for game logic
data class PlayerState(
    val trail: MutableList<TrailSegment> = mutableListOf(),
    var direction: Float = 0f,
    var turning: Float = 0f,
    var isDrawing: Boolean = true,
    var gapCounter: Int = 0,
    var boostState: BoostState = BoostState.READY,
    var boostFrames: Int = 0,
    var boostCooldownFrames: Int = 0,
    var isAlive: Boolean = true
)

// Helper function to calculate the next position of a player
fun calculateNextPosition(
    currentPosition: Offset, direction: Float, speed: Float
): Offset {
    return Offset(
        currentPosition.x + cos(direction) * speed, currentPosition.y + sin(direction) * speed
    )
}

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