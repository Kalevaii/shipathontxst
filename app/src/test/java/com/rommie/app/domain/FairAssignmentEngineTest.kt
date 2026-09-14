package com.rommie.app.domain

import com.rommie.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

class FairAssignmentEngineTest {
    private val chore = Chore("chore", "h", "Kitchen", estimatedMinutes = 30, difficulty = Difficulty.HARD, maxPoints = 20, createdBy = "a")
    private val a = HouseholdMember("h", "a")
    private val b = HouseholdMember("h", "b")
    private val due = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 0, 5, 12, 0) }.timeInMillis // Monday noon
    private fun task(id: String, uid: String, difficulty: Difficulty = Difficulty.EASY,
        status: TaskStatus = TaskStatus.ASSIGNED, choreId: String = "other", time: Long = due - 1) =
        TaskAssignment(id, "h", choreId, uid, time, workloadValue(difficulty), 5, status)
    private fun select(history: List<TaskAssignment>, members: List<HouseholdMember> = listOf(a, b)) =
        FairAssignmentEngine.selectMember(chore, members, history, due)

    @Test fun oneMemberAndEmptyHousehold() {
        assertEquals(a, select(emptyList(), listOf(a)))
        assertNull(select(emptyList(), emptyList()))
    }
    @Test fun equalWorkloadUsesStableUidRegardlessOfInputOrderOrPoints() {
        assertEquals(a, select(emptyList(), listOf(b.copy(contributionPoints = 999), a)))
        assertEquals(a, select(emptyList()))
    }
    @Test fun lowerTotalWorkloadWinsAcrossActiveAndVerifiedHistory() {
        assertEquals(b, select(listOf(task("1", "a", Difficulty.HARD, TaskStatus.VERIFIED), task("2", "b"))))
        assertEquals(b, select(listOf(task("1", "a", Difficulty.HARD))))
    }
    @Test fun hardChoreCountsMoreThanEasyEvenWithEqualTaskCounts() {
        assertEquals(b, select(listOf(task("1", "a", Difficulty.HARD), task("2", "b", Difficulty.EASY))))
    }
    @Test fun lowerActiveWorkloadBreaksEqualTotalTie() {
        assertEquals(b, select(listOf(task("1", "a"), task("2", "b", status = TaskStatus.VERIFIED))))
    }
    @Test fun sameChoreAvoidanceBreaksWorkloadTie() {
        assertEquals(b, select(listOf(task("1", "a", choreId = chore.id), task("2", "b"))))
    }
    @Test fun oldestLatestDueTimeBreaksRemainingTie() {
        assertEquals(b, select(listOf(task("1", "a", time = due), task("2", "b", time = due - 100))))
    }
    @Test fun cancelledAndOtherHouseholdTasksDoNotAffectFairness() {
        assertEquals(a, select(listOf(task("1", "a", Difficulty.HARD, TaskStatus.CANCELLED),
            task("2", "a", Difficulty.HARD).copy(householdId = "other"))))
    }
    @Test fun repeatedSelectionsStayBalancedAndAreOrderIndependent() {
        val history = mutableListOf<TaskAssignment>()
        repeat(12) { index ->
            val next = chore.copy(difficulty = if (index % 3 == 0) Difficulty.HARD else Difficulty.EASY)
            val selected = FairAssignmentEngine.selectMember(next, listOf(b, a), history, due + index)!!
            assertEquals(selected, FairAssignmentEngine.selectMember(next, listOf(a, b), history.reversed(), due + index))
            history += TaskAssignment("task$index", "h", next.id, selected.userId, due + index,
                workloadValue(next.difficulty), next.maxPoints)
        }
        val totals = listOf(a, b).map { m -> history.filter { it.assignedUserId == m.userId }.sumOf { it.workloadValue } }
        assertTrue(kotlin.math.abs(totals[0] - totals[1]) <= 3)
        assertEquals(setOf("a", "b"), history.map { it.assignedUserId }.toSet())
    }
    @Test fun excludedAndTemporarilyUnavailableMembersAreNotSelected() {
        val away = AvailabilityOverride("away", "h", "a", due - 1000, due, AvailabilityKind.AWAY)
        assertEquals(b, FairAssignmentEngine.selectMember(chore, listOf(a, b), emptyList(), due, overrides = listOf(away)))
        assertNull(FairAssignmentEngine.selectMember(chore, listOf(a), emptyList(), due, unavailableUserIds = setOf("a")))
        assertEquals(b, FairAssignmentEngine.selectMember(chore, listOf(a, b), emptyList(), due, unavailableUserIds = setOf("a")))
    }
    @Test fun weeklyBusyWindowInLocalTimezoneExcludesMember() {
        val busy = Availability("work", "h", "a", Weekday.MONDAY, 330, 360, AvailabilityKind.WORK, "America/Chicago")
        // January: 11:30–12:00 UTC == 05:30–06:00 Chicago.
        assertEquals(b, FairAssignmentEngine.selectMember(chore, listOf(a, b), emptyList(), due, availability = listOf(busy)))
    }
    @Test fun temporaryFreeOverrideReplacesWeeklyBusyAndWindowEndIsExclusive() {
        val busy = Availability("work", "h", "a", Weekday.MONDAY, 690, 720, AvailabilityKind.WORK, "UTC")
        val free = AvailabilityOverride("off", "h", "a", due - 30 * 60_000, due, AvailabilityKind.DAY_OFF)
        assertEquals(a, FairAssignmentEngine.selectMember(chore, listOf(a, b), emptyList(), due, listOf(busy), listOf(free)))
        val later = free.copy(startsAt = due, endsAt = due + 1000, kind = AvailabilityKind.AWAY)
        assertEquals(a, FairAssignmentEngine.selectMember(chore, listOf(a, b), emptyList(), due, overrides = listOf(later)))
    }
    @Test fun freeTimeDoesNotOverrideWorkloadFairness() {
        val free = AvailabilityOverride("off", "h", "a", due - 30 * 60_000, due, AvailabilityKind.AVAILABLE)
        assertEquals(b, FairAssignmentEngine.selectMember(chore, listOf(a, b), listOf(task("1", "a")), due, overrides = listOf(free)))
        assertEquals(b, FairAssignmentEngine.selectMember(chore, listOf(a, b), emptyList(), due, overrides = listOf(free.copy(userId = "b"))))
    }
    @Test fun invalidTimezoneAndDuplicateHistoryAreRejected() {
        val row = Availability("bad", "h", "a", Weekday.MONDAY, 0, 1440, AvailabilityKind.AVAILABLE, "not/a-zone")
        assertThrows(IllegalArgumentException::class.java) { FairAssignmentEngine.selectMember(chore, listOf(a), emptyList(), due, listOf(row)) }
        val assignment = task("same", "a")
        assertThrows(IllegalArgumentException::class.java) { select(listOf(assignment, assignment)) }
    }
}
