package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.droiddeck.launcher.session.SessionPrefs

class TpUbwcHintTest {
    @Test fun a740OnABlobV6xxTurnsTheHintOff() {
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.53"))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.75"))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676"))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.699.10"))
    }

    @Test fun a740OnThePocoBlobTurnsTheHintOn() {
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.76"))
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.80"))
    }

    @Test fun a740OnTheHyperOsBlobThatReports676Point1ReadsItsBuildFromGles() {
        val poco = "OpenGL ES 3.2 V@0676.76.1 (GIT@bad34dcf83, I1064ae0a1f, 1764326394) (Date:11/28/25)"
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.1") { poco })
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.1"))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.1") { null })
    }

    @Test fun glesThatMatchesTheVulkanVersionKeepsTheOutcome() {
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.53") { "OpenGL ES 3.2 V@0676.53 (GIT@1, I2, 3) (Date:01/01/24)" })
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.75") { "OpenGL ES 3.2 V@0676.75" })
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.76") { "OpenGL ES 3.2 V@0676.76" })
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.80") { "OpenGL ES 3.2 V@0676.80" })
    }

    @Test fun glesFromAnotherBranchOrWithoutABuildFallsBackToVulkan() {
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.1") { "OpenGL ES 3.2 V@0762.10" })
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.76") { "OpenGL ES 3.2 V@0615.0" })
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.1") { "OpenGL ES 3.2 (ANGLE 2.1.0)" })
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.76") { "garbage" })
    }

    @Test fun glesIsOnlyAskedForA676BlobInAuto() {
        val unasked: () -> String? = { throw AssertionError("GLES probed") }
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.762.10", glVersion = unasked))
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.700.0", glVersion = unasked))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.699.10", glVersion = unasked))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.615.0", glVersion = unasked))
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.1", samsung = true, glVersion = unasked))
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.1", mode = SessionPrefs.TP_UBWC_ON, glVersion = unasked))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.676.1", mode = SessionPrefs.TP_UBWC_OFF, glVersion = unasked))
        assertNull(TpUbwcHint.features(750, "512.676.1", glVersion = unasked))
        assertNull(TpUbwcHint.features(740, null, glVersion = unasked))
        assertNull(TpUbwcHint.features(740, "garbage", glVersion = unasked))
    }

    @Test fun glBuildReadsTheBuildAfterTheBranch() {
        assertEquals(76, TpUbwcHint.glBuild(676, "OpenGL ES 3.2 V@0676.76.1 (GIT@bad34dcf83, I1064ae0a1f, 1764326394) (Date:11/28/25)"))
        assertEquals(71, TpUbwcHint.glBuild(676, "OpenGL ES 3.2 V@0676.71"))
        assertEquals(53, TpUbwcHint.glBuild(676, "OpenGL ES 3.2 V@676.53"))
        assertNull(TpUbwcHint.glBuild(676, "OpenGL ES 3.2 V@0762.10"))
        assertNull(TpUbwcHint.glBuild(676, "OpenGL ES 3.2 V@0676"))
        assertNull(TpUbwcHint.glBuild(676, "OpenGL ES 3.2 Mesa 25.0"))
        assertNull(TpUbwcHint.glBuild(676, null))
    }

    @Test fun a740OnANewerBlobTurnsTheHintOn() {
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.700.0"))
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.762.10"))
    }

    @Test fun a740OnOneUiTurnsTheHintOnWhateverTheBlobVersion() {
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, "512.676.53", samsung = true))
        assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, null, samsung = true))
    }

    @Test fun onOverridesThePocoRuleEvenWhenTheVersionIsUnreadable() {
        for (version in listOf("512.676.1", "512.676.53", "garbage", null)) {
            assertEquals("enable_tp_ubwc_flag_hint=1", TpUbwcHint.features(740, version, mode = SessionPrefs.TP_UBWC_ON))
        }
    }

    @Test fun offOverridesSamsungAndNewerBlobs() {
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, "512.762.10", mode = SessionPrefs.TP_UBWC_OFF))
        assertEquals("enable_tp_ubwc_flag_hint=0", TpUbwcHint.features(740, null, samsung = true, mode = SessionPrefs.TP_UBWC_OFF))
    }

    @Test fun overridesDoNotApplyToOtherGpus() {
        for (mode in listOf(SessionPrefs.TP_UBWC_ON, SessionPrefs.TP_UBWC_OFF)) {
            assertNull(TpUbwcHint.features(750, "512.762.10", mode = mode))
            assertNull(TpUbwcHint.features(0, null, mode = mode))
        }
    }

    @Test fun otherGpusKeepTheirDriverDefault() {
        assertNull(TpUbwcHint.features(750, "512.762.10"))
        assertNull(TpUbwcHint.features(830, "512.676.53"))
        assertNull(TpUbwcHint.features(750, "512.676.53", samsung = true))
    }

    @Test fun anUnreadableSystemDriverKeepsTheDriverDefault() {
        assertNull(TpUbwcHint.features(740, null))
        assertNull(TpUbwcHint.features(740, "garbage"))
    }
}
