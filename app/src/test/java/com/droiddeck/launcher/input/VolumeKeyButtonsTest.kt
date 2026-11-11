package com.droiddeck.launcher.input

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class VolumeKeyButtonsTest {
    private val keys = VolumeKeyButtons()
    private var changes = 0

    private fun dispatch(code: Int, mode: String = ControllerPrefs.VOLUME_KEYS_SHOULDERS, enabled: Boolean = true,
        action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0, upIsLeft: Boolean = true) =
        keys.onKey(KeyEvent(0L, 0L, action, code, repeat), mode, enabled, upIsLeft) { changes++ }

    private fun pad() = PadState().also(keys::write)

    @Test fun shouldersFollowTheSideTheButtonsAreOn() {
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(pad().isDown(PadState.LB))
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertTrue(pad().isDown(PadState.RB))
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP, action = KeyEvent.ACTION_UP))
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, action = KeyEvent.ACTION_UP))
        assertFalse(keys.pressed)
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP, upIsLeft = false))
        assertTrue(pad().isDown(PadState.RB))
        assertFalse(pad().isDown(PadState.LB))
    }

    @Test fun triggersPressFully() {
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, mode = ControllerPrefs.VOLUME_KEYS_TRIGGERS))
        assertEquals(1f, pad().rightTrigger)
        assertEquals(0f, pad().leftTrigger)
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, mode = ControllerPrefs.VOLUME_KEYS_TRIGGERS, action = KeyEvent.ACTION_UP))
        assertEquals(0f, pad().rightTrigger)
    }

    @Test fun offAndDisabledLeaveVolumeAlone() {
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            assertFalse(dispatch(code, mode = ControllerPrefs.OFF))
            assertFalse(dispatch(code, mode = ControllerPrefs.OFF, action = KeyEvent.ACTION_UP))
            assertFalse(dispatch(code, enabled = false))
            assertFalse(dispatch(code, enabled = false, action = KeyEvent.ACTION_UP))
        }
        assertFalse(dispatch(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(dispatch(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(0, changes)
    }

    @Test fun aHeldButtonIsReleasedEvenAfterTheOptionTurnsOff() {
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP, enabled = false, repeat = 1))
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP, mode = ControllerPrefs.OFF, enabled = false, action = KeyEvent.ACTION_UP))
        assertFalse(keys.pressed)
        assertFalse(pad().isDown(PadState.LB))
        assertEquals(2, changes)
    }

    @Test fun aReleasedKeyStaysUpUntilPressedAgain() {
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP))
        keys.release { changes++ }
        assertFalse(keys.pressed)
        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP, repeat = 3))
        assertFalse(keys.pressed)
        assertFalse(dispatch(KeyEvent.KEYCODE_VOLUME_UP, action = KeyEvent.ACTION_UP))
        assertEquals(2, changes)
    }
}
