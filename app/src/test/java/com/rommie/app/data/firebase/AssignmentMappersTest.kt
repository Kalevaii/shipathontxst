package com.rommie.app.data.firebase

import com.rommie.app.model.*
import org.junit.Assert.*
import org.junit.Test

class AssignmentMappersTest {
    private val sample = TaskAssignment("occurrence", "h", "c", "u", 1000, 3, 20)
    @Test fun allStatusesRoundTripAndIdComesFromPath() {
        TaskStatus.entries.forEach { status ->
            val assignment = sample.copy(status = status)
            assertEquals(assignment, AssignmentMappers.fromDocument(assignment.id, "h", AssignmentMappers.toDocument(assignment)))
        }
        assertEquals("path", AssignmentMappers.fromDocument("path", "h", AssignmentMappers.toDocument(sample) + ("id" to "spoof")).id)
    }
    @Test fun corruptNumericStatusAndReferenceFieldsFail() {
        val fields = AssignmentMappers.toDocument(sample)
        listOf(fields - "dueAt", fields + ("dueAt" to 0L), fields + ("dueAt" to Long.MAX_VALUE),
            fields + ("workloadValue" to 2.5), fields + ("workloadValue" to 4L),
            fields + ("maxPoints" to -1L), fields + ("maxPoints" to 1001L),
            fields + ("status" to "DONE"), fields + ("householdId" to "other"),
            fields + ("choreId" to "a/b"), fields + ("assignedUserId" to ""))
            .forEach { assertThrows(IllegalArgumentException::class.java) { AssignmentMappers.fromDocument("a", "h", it) } }
    }
    @Test fun occurrencePathValidationIsBounded() {
        assertEquals("households/h/assignments/day_1-c", FirestorePaths.assignment("h", "day_1-c"))
        listOf("", "a/b", "..", "x".repeat(129)).forEach {
            assertThrows(IllegalArgumentException::class.java) { FirestorePaths.assignment("h", it) }
        }
    }
}
