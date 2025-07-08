package com.example.achtungdiekurve.data

object GameConstants {
    const val BOOST_DURATION_FRAMES = 60
    const val NORMAL_SPEED = 3f
    const val BOOSTED_SPEED = 6f
    const val BOOST_COOLDOWN_DURATION_FRAMES = 300
    const val TURN_SPEED = 0.07f
    const val STROKE_WIDTH = 6f
    const val DRAW_DURATION_FRAMES = 200
    const val GAP_DURATION_FRAMES = 10
    const val GAME_TICK_RATE_MS = 16L // Roughly 60 FPS
    const val COLLISION_RADIUS_MULTIPLIER = 0.5f // strokeWidth / 2f
    const val TILT_THRESHOLD = 2
}