package me.jaspr.wemodern

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupHeroModeTest {
    @Test
    fun missingCoreSetupIsRequiredRegardlessOfDebugPreferences() {
        assertEquals(
            SetupHeroMode.Required,
            resolveSetupHeroMode(
                coreReady = false,
                captureLoggingEnabled = true,
                rewriteNotificationsEnabled = true,
            ),
        )
    }

    @Test
    fun readyWithBothDebugPreferencesDisabledIsOff() {
        assertEquals(
            SetupHeroMode.Off,
            resolveSetupHeroMode(
                coreReady = true,
                captureLoggingEnabled = false,
                rewriteNotificationsEnabled = false,
            ),
        )
    }

    @Test
    fun readyWithCaptureOnlyIsDebugOnly() {
        assertEquals(
            SetupHeroMode.DebugOnly,
            resolveSetupHeroMode(
                coreReady = true,
                captureLoggingEnabled = true,
                rewriteNotificationsEnabled = false,
            ),
        )
    }

    @Test
    fun readyWithRewriteEnabledIsAllSet() {
        assertEquals(
            SetupHeroMode.AllSet,
            resolveSetupHeroMode(
                coreReady = true,
                captureLoggingEnabled = false,
                rewriteNotificationsEnabled = true,
            ),
        )
    }
}
