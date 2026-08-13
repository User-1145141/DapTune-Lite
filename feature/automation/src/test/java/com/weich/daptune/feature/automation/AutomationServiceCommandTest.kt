package com.weich.daptune.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationServiceCommandTest {
    @Test
    fun nullIntentDoesNotCreateASecondRecoveryPath() {
        assertEquals(AutomationServiceCommand.IGNORE, automationServiceCommand(null))
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
}
