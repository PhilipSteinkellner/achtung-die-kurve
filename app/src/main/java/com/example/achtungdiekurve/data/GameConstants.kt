package com.example.achtungdiekurve.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object GameConstants {
    const val SPECIAL_MOVE_DURATION_FRAMES = 66
    const val SLOW_SPEED = 2f
    const val NORMAL_SPEED = 4f
    const val BOOST_SPEED = 6f
    const val SPECIAL_MOVE_COOLDOWN_DURATION_FRAMES = 303
    const val TURN_SPEED = 0.07f
    val STROKE_WIDTH = 3.dp
    const val DRAW_DURATION_FRAMES = 150
    const val GAP_DURATION_FRAMES = 5
    const val GAME_TICK_RATE_MS = 33L // 30 FPS
    const val COLLISION_RADIUS_MULTIPLIER = 1.5f
    const val TILT_THRESHOLD = 2
    const val SWIPE_THRESHOLD = 200f
    const val MIN_SEGMENTS_FOR_SELF_COLLISION = 5
    const val SPAWN_EDGE_MARGIN = 200f
    const val SPAWN_PLAYER_MARGIN = 300f
    val COLORS = listOf(Color.Red, Color.Green, Color.Cyan, Color.Yellow)
    const val GAME_WORLD_WIDTH = 1080f
    const val GAME_WORLD_HEIGHT = 1920f
}