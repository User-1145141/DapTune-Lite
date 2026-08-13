package com.weich.daptune.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationRetryPolicyTest {
    @Test
    fun nullSystemRestartIntentRecoversTheExistingAutomationSession() {
        assertEquals(AutomationServiceCommand.RECOVER, automationServiceCommand(null))
    }

    @Test
    fun explicitServiceActionsRemainDistinct() {
        assertEquals(
            AutomationServiceCommand.START,
            automationServiceCommand(EqAutomationService.ActionStart),
        )
        assertEquals(
            AutomationServiceCommand.RECOVER,
            automationServiceCommand(EqAutomationService.ActionRecover),
        )
        assertEquals(
            AutomationServiceCommand.STOP,
            automationServiceCommand(EqAutomationService.ActionStop),
        )
        assertEquals(AutomationServiceCommand.IGNORE, automationServiceCommand("unexpected"))
    }

    @Test
    fun delayStartsAtOneSecondAndIsBoundedAtThirtySeconds() {
        assertEquals(1_000L, automationMonitorRetryDelay(0L))
        assertEquals(2_000L, automationMonitorRetryDelay(1L))
        assertEquals(30_000L, automationMonitorRetryDelay(29L))
        assertEquals(30_000L, automationMonitorRetryDelay(Long.MAX_VALUE))
    }

    @Test
    fun negativeAttemptCannotProduceANegativeDelay() {
        assertEquals(1_000L, automationMonitorRetryDelay(-1L))
    }
}
