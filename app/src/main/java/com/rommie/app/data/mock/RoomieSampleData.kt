package com.rommie.app.data.mock

import com.rommie.app.data.repository.HouseholdSnapshot
import com.rommie.app.domain.*
import com.rommie.app.model.*

// Fixed IDs and times keep previews deterministic. URLs are placeholders, not live media.
object RoomieSampleData {
    const val HOUSEHOLD_ID = "house-demo"
    const val CURRENT_USER_ID = "aayush"
    private val users = listOf("Aayush", "Alex", "Sam", "Jake").map {
        User(it.lowercase(), it, "${it.lowercase()}@example.com")
    }
    private val chores = listOf(
        Chore("trash", HOUSEHOLD_ID, "Take Out Trash", estimatedMinutes = 5,
            difficulty = Difficulty.EASY, maxPoints = 5, createdBy = CURRENT_USER_ID,
            recurrence = Recurrence.EveryDays(1)),
        Chore("bathroom", HOUSEHOLD_ID, "Clean Bathroom", estimatedMinutes = 40,
            difficulty = Difficulty.HARD, maxPoints = 20, createdBy = CURRENT_USER_ID,
            recurrence = Recurrence.Weekly(setOf(Weekday.TUESDAY, Weekday.FRIDAY))),
    )
    val household = HouseholdSnapshot(
        household = Household(HOUSEHOLD_ID, "The Boys Apartment", "TXST24"),
        users = users,
        members = users.map { HouseholdMember(HOUSEHOLD_ID, it.id) },
        chores = chores,
        assignments = chores.mapIndexed { index, chore ->
            TaskAssignment("assignment-${chore.id}", HOUSEHOLD_ID, chore.id, users[index].id,
                1789516800000L, workloadValue(chore.difficulty), chore.maxPoints,
                if (index == 1) TaskStatus.AWAITING_VERIFICATION else TaskStatus.ASSIGNED)
        },
        completions = listOf(TaskCompletion("completion-bathroom", "assignment-bathroom",
            HOUSEHOLD_ID, "alex", listOf(ProofSubmission("proof-demo",
                "https://example.com/roomie/bathroom.jpg", ProofType.PHOTO)), 1789500000000L)),
        availability = listOf(Availability("availability-aayush", HOUSEHOLD_ID,
            CURRENT_USER_ID, Weekday.FRIDAY, 0, 1440, AvailabilityKind.DAY_OFF, "America/Chicago")),
    )
}
