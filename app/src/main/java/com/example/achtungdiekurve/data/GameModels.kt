package com.example.achtungdiekurve.data

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

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

// Enum for control modes
enum class ControlMode {
    TAP, TILT
}

// Data class to hold the full game state
data class GameState(
    val localTrail: List<TrailSegment>,
    val localDirection: Float,
    val localTurning: Float,
    val localIsDrawing: Boolean,
    val localGapCounter: Int,
    val localIsBoosting: Int,
    val localBoostFrames: Int,
    val localBoostCooldownFrames: Int,
    val localIsAlive: Boolean,
    val opponentTrail: List<TrailSegment>,
    val opponentIsAlive: Boolean,
    val isRunning: Boolean,
    val isGameOver: Boolean,
    val gameOverMessage: String,
    val isHost: Boolean,
    val connectionStatus: String,
    val showMultiplayerSetup: Boolean,
    val connectedEndpointId: String?,
    val isSinglePlayer: Boolean,
    val showModeSelection: Boolean
)

// Data class for player specific state
data class PlayerState(
    val trail: MutableList<TrailSegment> = mutableListOf(),
    var direction: Float = 0f,
    var turning: Float = 0f,
    var isDrawing: Boolean = true,
    var gapCounter: Int = 0,
    var isBoosting: Int = 0, // 0: ready, 1: boosting, 2: cooldown
    var boostFrames: Int = 0,
    var boostCooldownFrames: Int = 0,
    var isAlive: Boolean = true
)

// Function to calculate the next position of a player
fun calculateNextPosition(
    currentPosition: Offset, direction: Float, speed: Float
): Offset {
    return Offset(
        currentPosition.x + cos(direction) * speed, currentPosition.y + sin(direction) * speed
    )
}