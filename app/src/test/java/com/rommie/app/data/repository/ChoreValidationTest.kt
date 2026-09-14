package com.rommie.app.data.repository

import com.rommie.app.domain.workloadValue
import com.rommie.app.model.*
import org.junit.Assert.*
import org.junit.Test

class ChoreValidationTest {
    private val base = ChoreDetails(" Kitchen ", 30, Difficulty.MEDIUM, 15)
    @Test fun trimsTitleAndSupportsOneTimeAndBothRecurringDefinitions() {
        listOf(null, Recurrence.EveryDays(365), Recurrence.Weekly(Weekday.entries.toSet())).forEach {
            assertEquals("Kitchen", ChoreValidation.details(base.copy(recurrence = it)).title)
        }
    }
    @Test fun rejectsInvalidTitlesDescriptionsDurationsAndPoints() {
        val bad = listOf(base.copy(title = " "), base.copy(title = "a".repeat(101)), base.copy(title = "a\nb"),
            base.copy(description = "a".repeat(2001)), base.copy(estimatedMinutes = 0), base.copy(estimatedMinutes = 1441),
            base.copy(maxPoints = -1), base.copy(maxPoints = 1001), base.copy(recurrence = Recurrence.EveryDays(366)))
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { ChoreValidation.details(it) } }
    }
    @Test fun acceptsBoundariesAndKeepsRewardIndependentOfWorkload() {
        Difficulty.entries.forEachIndexed { index, difficulty ->
            val input = ChoreValidation.details(base.copy(difficulty = difficulty, maxPoints = 1000, estimatedMinutes = 1440))
            assertEquals(index + 1, workloadValue(input.difficulty))
            assertEquals(1000, input.maxPoints)
        }
        assertEquals(0, ChoreValidation.details(base.copy(maxPoints = 0, estimatedMinutes = 1)).maxPoints)
    }
    @Test fun copiesMutableWeekdaysBeforeSuspending() {
        val days = mutableSetOf(Weekday.MONDAY)
        val validated = ChoreValidation.details(base.copy(recurrence = Recurrence.Weekly(days)))
        days.clear()
        assertEquals(setOf(Weekday.MONDAY), (validated.recurrence as Recurrence.Weekly).weekdays)
    }
}
