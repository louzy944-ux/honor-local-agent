package com.mobilefork.hermesagent.device

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HermesExternalTermuxBridgeTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun readinessNeverClaimsExternalExecutionWithoutBothAppsAndPermission() {
        val status = HermesExternalTermuxBridge.status(context)

        if (!status.termuxInstalled || !status.termuxApiInstalled ||
            !status.runCommandPermissionGranted
        ) {
            assertFalse(status.readyForExternalCommands)
        }
    }

    @Test
    fun onlyFixedReadOnlyBatteryCommandIsExposed() {
        assertTrue(
            HermesExternalTermuxBridge.DiagnosticCommand.BATTERY_STATUS.executable ==
                "/data/data/com.termux/files/usr/bin/termux-battery-status",
        )
        assertTrue(
            HermesExternalTermuxBridge.DiagnosticCommand.values().size == 1,
        )
    }

    @Test
    fun readinessDetailExplainsTheTermuxConfigurationGate() {
        val status = HermesExternalTermuxBridge.status(context)

        assertTrue(status.detail.isNotBlank())
        assertTrue(
            status.detail.contains("Termux") ||
                status.detail.contains("permission"),
        )
    }
}
