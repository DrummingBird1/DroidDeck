package com.droiddeck.launcher.input

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PadMotionTest {
    @Test fun sonyAndNintendoPadsReportTheirOwnMotion() {
        assertEquals(PadMotion.FRAME_SONY, PadMotion.motionFrame(SONY, joyCon = false))
        assertEquals(PadMotion.FRAME_NINTENDO, PadMotion.motionFrame(NINTENDO, joyCon = false))
    }

    @Test fun joyConsAndUnknownFramesKeepThePhoneSensors() {
        assertEquals(PadMotion.FRAME_NONE, PadMotion.motionFrame(NINTENDO, joyCon = true))
        assertEquals(PadMotion.FRAME_NONE, PadMotion.motionFrame(EIGHTBITDO, joyCon = false))
        assertEquals(PadMotion.FRAME_NONE, PadMotion.motionFrame(MICROSOFT, joyCon = false))
    }

    @Test fun aLevelPadPutsGravityWhereTheUprightPhoneDoes() {
        assertDeck(floatArrayOf(0f, 0f, G), PadMotion.FRAME_SONY, floatArrayOf(0f, G, 0f))
        assertDeck(floatArrayOf(0f, 0f, G), PadMotion.FRAME_NINTENDO, floatArrayOf(0f, 0f, G))
    }

    @Test fun turningLeftIsTheSameDeckRateFromEitherPad() {
        assertDeck(floatArrayOf(0f, 0f, 1f), PadMotion.FRAME_SONY, floatArrayOf(0f, 1f, 0f))
        assertDeck(floatArrayOf(0f, 0f, 1f), PadMotion.FRAME_NINTENDO, floatArrayOf(0f, 0f, 1f))
    }

    @Test fun tiltingTheFarEdgeUpIsTheSameDeckRateFromEitherPad() {
        assertDeck(floatArrayOf(1f, 0f, 0f), PadMotion.FRAME_SONY, floatArrayOf(1f, 0f, 0f))
        assertDeck(floatArrayOf(1f, 0f, 0f), PadMotion.FRAME_NINTENDO, floatArrayOf(0f, -1f, 0f))
    }

    @Test fun rollingTheRightGripDownIsTheSameDeckRateFromEitherPad() {
        assertDeck(floatArrayOf(0f, 1f, 0f), PadMotion.FRAME_SONY, floatArrayOf(0f, 0f, -1f))
        assertDeck(floatArrayOf(0f, 1f, 0f), PadMotion.FRAME_NINTENDO, floatArrayOf(1f, 0f, 0f))
    }

    private fun assertDeck(expected: FloatArray, frame: Int, values: FloatArray) {
        val out = FloatArray(3)
        PadMotion.deckAxes(frame, values, out)
        assertArrayEquals(expected, out, 1e-6f)
    }

    private companion object {
        const val G = 9.80665f
        const val SONY = 0x054c
        const val NINTENDO = 0x057e
        const val EIGHTBITDO = 0x2dc8
        const val MICROSOFT = 0x045e
    }
}
