package com.example.achtungdiekurve.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class TrailSegment(
    @Serializable(with = OffsetSerializer::class) val position: Offset, val isGap: Boolean
)

enum class ControlMode {
    TAP, TILT
}

enum class SpecialMoveState {
    READY, BOOST, SLOW, COOLDOWN
}

enum class MatchState {
    SETUP, MATCH_SETTINGS, RUNNING, ROUND_OVER, GAME_OVER
}

data class MultiplayerState(
    val isMultiplayer: Boolean = false,
    val isHost: Boolean = false,
    val connectionStatus: String = "Not Connected",
    val connectedEndpoints: String = "",
    val searching: Boolean = false
)

// The main UI state class for the game screen
data class GameState(
    val localPlayer: PlayerState = PlayerState(id = "HOST", color = Color.Blue, name = ""),
    val opponents: List<PlayerState> = listOf(),
    val multiplayerState: MultiplayerState = MultiplayerState(),
    val isRunning: Boolean = false,
    val screenWidthPx: Float = 0f,
    val screenHeightPx: Float = 0f,
    val scoreToWin: Int = 5,
    val matchState: MatchState = MatchState.SETUP,
    val lastCollision: CollisionResult? = null,
    val collisionAnimation: ConfettiAnimation? = null,
    val isPausedForCollision: Boolean = false,
    val zoomScale: Float = 1f,
    val zoomCenter: Offset = Offset.Zero

)

// Internal data class to hold the full state of a player for game logic
@Serializable
data class PlayerState(
    val trail: MutableList<TrailSegment> = mutableListOf(),
    var direction: Float = 0f,
    var turning: Float = 0f,
    var isDrawing: Boolean = true,
    var gapCounter: Int = 0,
    var boostState: SpecialMoveState = SpecialMoveState.COOLDOWN,
    var boostFrames: Int = 0,
    var boostCooldownFrames: Int = GameConstants.SPECIAL_MOVE_COOLDOWN_DURATION_FRAMES,
    var isAlive: Boolean = true,
    var score: Int = 0,
    @Serializable(with = ComposeColorSerializer::class) val color: Color,
    val id: String,
    var name: String,
    var lastCollision: CollisionResult? = null,
)

@Serializable
data class LatestPlayerState(
    val id: String,
    val pos: TrailSegment,
    val score: Int,
    val isAlive: Boolean,
    val boostState: SpecialMoveState,
    val lastCollision: CollisionResult? = null,
    val boostCooldownFrames: Int
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

enum class CollisionType {
    BOUNDARY, TRAIL
}


@Serializable
data class CollisionResult(
    val type: CollisionType,
    @Serializable(with = OffsetSerializer::class) val surfaceNormal: Offset
)


fun calculateImpactAngle(playerDirection: Float, surfaceNormal: Offset): Float {

    val directionVector = Offset(cos(playerDirection), sin(playerDirection))
    val dotProduct = (-directionVector.x * surfaceNormal.x) + (-directionVector.y * surfaceNormal.y)
    return acos(dotProduct.coerceIn(-1.0f, 1.0f))
}

// Data class to hold confetti animation state
data class ConfettiAnimation(
    val position: Offset,
    val rotation: Float,
    val id: String
)

// --- Helper extension functions for Offset ---

fun Offset.normalized(): Offset {
    val length = sqrt(this.x * this.x + this.y * this.y)
    return if (length > 0) this / length else Offset.Zero
}