package com.droiddeck.launcher.input

import android.view.KeyEvent

internal class VolumeKeyButtons {
    private val held = HashMap<Pair<Int, Int>, InputControl>()

    val pressed: Boolean get() = held.isNotEmpty()

    fun onKey(event: KeyEvent, mode: String, enabled: Boolean, upIsLeft: Boolean, changed: () -> Unit): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val key = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP) {
            if (held.remove(key) == null) return false
            changed()
            return true
        }
        if (key in held) return true
        if (!enabled || event.action != KeyEvent.ACTION_DOWN) return false
        val left = (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) == upIsLeft
        val control = when (mode) {
            ControllerPrefs.VOLUME_KEYS_SHOULDERS -> if (left) InputControl.LB else InputControl.RB
            ControllerPrefs.VOLUME_KEYS_TRIGGERS -> if (left) InputControl.LT else InputControl.RT
            else -> return false
        }
        if (event.repeatCount == 0) {
            held[key] = control
            changed()
        }
        return true
    }

    fun release(changed: () -> Unit) {
        if (held.isEmpty()) return
        held.clear()
        changed()
    }

    fun write(pad: PadState) {
        pad.press(PadState.LB, InputControl.LB in held.values)
        pad.press(PadState.RB, InputControl.RB in held.values)
        pad.leftTrigger = if (InputControl.LT in held.values) 1f else 0f
        pad.rightTrigger = if (InputControl.RT in held.values) 1f else 0f
    }
}
