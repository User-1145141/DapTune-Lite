package com.weich.daptune.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationServiceCommandTest {
    @Test
    fun nullIntentRestoresTheStickyServiceSession() {
        assertEquals(AutomationServiceCommand.SYSTEM_RESTART, automationServiceCommand(null))
    }

    @Test
    fun pendingRecoveryFromThePreviousVersionMigratesToStickyRecovery() {
        assertEquals(
            AutomationServiceCommand.SYSTEM_RESTART,
            automationServiceCommand("com.weich.daptune.action.RECOVER_AFTER_TASK_REMOVAL"),
        )
    }

    @Test
    fun explicitServiceActionsRemainDistinct() {
        assertEquals(
            AutomationServiceCommand.START,
            automationServiceCommand(EqAutomationService.ActionStart),
        )
        assertEquals(
            AutomationServiceCommand.STOP,
            automationServiceCommand(EqAutomationService.ActionStop),
        )
        assertEquals(AutomationServiceCommand.IGNORE, automationServiceCommand("unexpected"))
    }
}
