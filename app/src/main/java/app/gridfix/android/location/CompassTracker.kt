package app.gridfix.android.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class CompassData(
    val azimuthMagnetic: Float = 0f,   // degrees, 0..360, magnetic north
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val hasSensor: Boolean = true,
    val hasReading: Boolean = false,
)

/**
 * Compass heading from the rotation-vector sensor with circular low-pass smoothing.
 * Azimuth is referenced to MAGNETIC north; callers apply declination for true north.
 */
class CompassTracker(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val remapped = FloatArray(9)

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _data = MutableStateFlow(CompassData(hasSensor = rotationSensor != null))
    val data: StateFlow<CompassData> = _data.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Smoothing state on the unit circle (avoids the 359->0 wraparound problem)
    private var smoothSin = 0.0
    private var smoothCos = 0.0
    private var initialized = false

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        initialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        // The raw azimuth is the device's natural "up" edge. Remap to the current
        // display rotation so a landscape phone (or landscape-natural tablet)
        // does not read 90 or 180 degrees off.
        val rotation = runCatching {
            (appContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.rotation
        }.getOrDefault(android.view.Surface.ROTATION_0)
        val (ax, ay) = when (rotation) {
            android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, ax, ay, remapped)
        SensorManager.getOrientation(remapped, orientationAngles)
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
        val rad = Math.toRadians(azimuth)

        val alpha = 0.25
        if (!initialized) {
            smoothSin = sin(rad)
            smoothCos = cos(rad)
            initialized = true
        } else {
            smoothSin = (1 - alpha) * smoothSin + alpha * sin(rad)
            smoothCos = (1 - alpha) * smoothCos + alpha * cos(rad)
        }
        val smoothed = (Math.toDegrees(atan2(smoothSin, smoothCos)) + 360.0) % 360.0

        _data.update {
            it.copy(azimuthMagnetic = smoothed.toFloat(), hasReading = true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            _data.update { it.copy(accuracy = accuracy) }
        }
    }
}
