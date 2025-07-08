package com.example.achtungdiekurve.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.achtungdiekurve.data.GameConstants

@Composable
fun rememberAccelerometerSensorHandler(onTiltChange: (Float) -> Unit): SensorManager? {
    val context = LocalContext.current
    val sensorManager =
        remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    if (accelerometer == null) {
        // Accelerometer not available
        return null
    }

    DisposableEffect(sensorManager, accelerometer) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val tiltX = it.values[0] // tilt left/right
                    val turning = when {
                        tiltX > GameConstants.TILT_THRESHOLD -> -1f // turn left
                        tiltX < -1 * GameConstants.TILT_THRESHOLD -> 1f // turn right
                        else -> 0f
                    }
                    onTiltChange(turning)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not used
            }
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    return sensorManager // Return manager if successful
}