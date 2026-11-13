package com.droiddeck.launcher.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.InputDevice
import android.view.Surface
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

class PadMotion(private val context: Context, private val rotation: () -> Int) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var thread: HandlerThread? = null
    @Volatile private var active = false
    private val accel = ShortArray(3)
    private val gyro = ShortArray(3)
    private var reading = false
    private var idlePolls = 0
    private var turn = Surface.ROTATION_0
    private var turnAt = 0L
    private var padId = PadBridge.NO_CONTROLLER
    private var padSensors: SensorManager? = null
    private var padFrame = FRAME_NONE
    private val padAccel = ShortArray(3)
    private val padGyro = ShortArray(3)
    private val padAxes = FloatArray(3)
    private val padListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!active || padFrame == FRAME_NONE) return
            val target: ShortArray
            val scale: Float
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> { target = padGyro; scale = GYRO_COUNTS_PER_RAD_S }
                Sensor.TYPE_ACCELEROMETER -> { target = padAccel; scale = ACCEL_COUNTS_PER_M_S2 }
                else -> return
            }
            deckAxes(padFrame, event.values, padAxes)
            for (i in 0..2) target[i] = counts(padAxes[i] * scale)
            FakeInputWriter.writeMotion(SLOT, padAccel, padGyro)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (thread != null || sensors == null) return
        val gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyroscope == null && accelerometer == null) {
            Log.i(TAG, "pad motion: this device has no gyro or accelerometer")
            return
        }
        val worker = HandlerThread("pad-motion", android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
        thread = worker
        val handler = Handler(worker.looper)
        active = true
        handler.post(object : Runnable {
            override fun run() {
                if (!active) return
                follow(handler, gyroscope, accelerometer)
                handler.postDelayed(this, POLL_MS)
            }
        })
        Log.i(TAG, "pad motion: gyro ${gyroscope?.name ?: "none"}, accelerometer ${accelerometer?.name ?: "none"}")
    }

    fun stop() {
        val worker = thread ?: return
        thread = null
        active = false
        Handler(worker.looper).post {
            sensors?.unregisterListener(this)
            releaseController()
            reading = false
            idlePolls = 0
            still()
        }
        worker.quitSafely()
    }

    private fun follow(handler: Handler, gyroscope: Sensor?, accelerometer: Sensor?) {
        val touch = SteamTouchDevice.current
        val padWanted = FakeInputWriter.readImuMode(SLOT) != 0
        followController(handler, if (padWanted) PadBridge.controllerForSlot(SLOT) else PadBridge.NO_CONTROLLER)
        val wanted = padWanted && padFrame == FRAME_NONE ||
            touch != null && touch.plugged && touch.gyroRequested && touch.motionEnabled
        idlePolls = if (wanted) 0 else idlePolls + 1
        if (wanted && !reading) {
            reading = true
            turnAt = 0L
            gyroscope?.let { sensors?.registerListener(this, it, SAMPLING_US, handler) }
            accelerometer?.let { sensors?.registerListener(this, it, SAMPLING_US, handler) }
            Log.i(TAG, "pad motion: reading")
        } else if (reading && idlePolls >= 2) {
            reading = false
            sensors?.unregisterListener(this)
            still()
            Log.i(TAG, "pad motion: paused, the client wants none")
        }
    }

    private fun followController(handler: Handler, id: Int) {
        if (id == padId) return
        releaseController()
        padId = id
        if (id == PadBridge.NO_CONTROLLER || Build.VERSION.SDK_INT < 31) return
        val device = InputDevice.getDevice(id) ?: return
        val frame = motionFrame(device.vendorId, JoyCon.side(device) >= 0)
        if (frame != FRAME_NONE) listenToController(handler, device, frame)
    }

    @RequiresApi(31)
    private fun listenToController(handler: Handler, device: InputDevice, frame: Int) {
        val manager = runCatching { device.sensorManager }.getOrNull() ?: return
        val gyroscope = runCatching { manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }.getOrNull() ?: return
        val accelerometer = runCatching { manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }.getOrNull()
        val listening = runCatching {
            manager.registerListener(padListener, gyroscope, SAMPLING_US, handler) &&
                (accelerometer == null || manager.registerListener(padListener, accelerometer, SAMPLING_US, handler))
        }.onFailure { Log.w(TAG, "pad motion: \"${device.name}\" motion unavailable", it) }.getOrDefault(false)
        if (!listening) {
            runCatching { manager.unregisterListener(padListener) }
            return
        }
        padSensors = manager
        padFrame = frame
        Log.i(TAG, "pad motion: from \"${device.name}\" (${"%04x:%04x".format(java.util.Locale.ROOT, device.vendorId, device.productId)})")
    }

    private fun releaseController() {
        padSensors?.let { manager -> runCatching { manager.unregisterListener(padListener) } }
        padSensors = null
        padId = PadBridge.NO_CONTROLLER
        if (padFrame == FRAME_NONE) return
        padFrame = FRAME_NONE
        padGyro.fill(0)
        FakeInputWriter.writeMotion(SLOT, padAccel, padGyro)
        Log.i(TAG, "pad motion: the controller's sensors stopped")
    }

    private fun still() {
        gyro.fill(0)
        if (padFrame == FRAME_NONE) FakeInputWriter.writeMotion(SLOT, accel, gyro)
        SteamTouchDevice.current?.setMotion(accel, gyro)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val values = event.values
        if (event.timestamp - turnAt > ROTATION_REFRESH_NS) {
            turn = rotation()
            turnAt = event.timestamp
        }
        val x: Float
        val y: Float
        when (turn) {
            Surface.ROTATION_90 -> { x = -values[1]; y = values[0] }
            Surface.ROTATION_180 -> { x = -values[0]; y = -values[1] }
            Surface.ROTATION_270 -> { x = values[1]; y = -values[0] }
            else -> { x = values[0]; y = values[1] }
        }
        val z = values[2]
        val target: ShortArray
        val scale: Float
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> { target = gyro; scale = GYRO_COUNTS_PER_RAD_S }
            Sensor.TYPE_ACCELEROMETER -> { target = accel; scale = ACCEL_COUNTS_PER_M_S2 }
            else -> return
        }
        target[0] = counts(x * scale)
        target[1] = counts(-z * scale)
        target[2] = counts(y * scale)
        if (padFrame == FRAME_NONE) FakeInputWriter.writeMotion(SLOT, accel, gyro)
        SteamTouchDevice.current?.setMotion(accel, gyro)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun counts(value: Float): Short = value.roundToInt().coerceIn(-32768, 32767).toShort()

    companion object {
        private const val TAG = "PadMotion"
        private const val SLOT = 0
        private const val SAMPLING_US = 4000
        private const val POLL_MS = 500L
        private const val ROTATION_REFRESH_NS = 250_000_000L
        private const val ACCEL_COUNTS_PER_M_S2 = 16384f / SensorManager.GRAVITY_EARTH
        private const val GYRO_COUNTS_PER_RAD_S = (32768f / 2000f) * (180f / Math.PI.toFloat())
        private const val SONY = 0x054c
        private const val NINTENDO = 0x057e
        internal const val FRAME_NONE = 0
        internal const val FRAME_SONY = 1
        internal const val FRAME_NINTENDO = 2

        internal fun motionFrame(vendorId: Int, joyCon: Boolean): Int = when {
            joyCon -> FRAME_NONE
            vendorId == SONY -> FRAME_SONY
            vendorId == NINTENDO -> FRAME_NINTENDO
            else -> FRAME_NONE
        }

        // hid-playstation and hid-sony report x right, y up, z toward the player;
        // hid-nintendo reports x toward the triggers, y left, z up.
        internal fun deckAxes(frame: Int, v: FloatArray, out: FloatArray) {
            if (frame == FRAME_NINTENDO) {
                out[0] = -v[1]; out[1] = v[0]; out[2] = v[2]
            } else {
                out[0] = v[0]; out[1] = -v[2]; out[2] = v[1]
            }
        }
    }
}
