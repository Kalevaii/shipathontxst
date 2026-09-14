package com.rommie.app.data.firebase

import com.google.firebase.Timestamp
import com.rommie.app.model.ProofType
import org.junit.Assert.*
import org.junit.Test

class CompletionMappersTest {
    private fun data(): Map<String, Any?> = mapOf("householdId" to "home", "assignmentId" to "task", "completedBy" to "worker",
        "proof" to listOf(mapOf("id" to "proof", "mediaUrl" to "households/home/completions/task/proof/worker/proof", "type" to "PHOTO")),
        "submittedAt" to Timestamp(123, 0), "result" to null)
    @Test fun pendingCompletionUsesExistingModelAndServerTime() {
        val value = CompletionMappers.fromDocument("task", "home", data())
        assertEquals(123000L, value.submittedAt); assertNull(value.result)
        assertEquals(ProofType.PHOTO, value.proof.single().type)
        assertEquals("task", value.assignmentId)
    }
    @Test fun rejectsForeignHouseholdAndAssignment() {
        for (change in listOf(mapOf("householdId" to "other"), mapOf("assignmentId" to "other"))) {
            assertThrows(IllegalArgumentException::class.java) { CompletionMappers.fromDocument("task", "home", data() + change) }
        }
    }
    @Test fun rejectsEmptyOrForeignProof() {
        assertThrows(IllegalArgumentException::class.java) { CompletionMappers.fromDocument("task", "home", data() + ("proof" to emptyList<Any>())) }
        assertThrows(IllegalArgumentException::class.java) { CompletionMappers.fromDocument("task", "home", data() + ("completedBy" to "other")) }
    }
    @Test fun rejectsUncommittedTime() {
        assertThrows(IllegalStateException::class.java) { CompletionMappers.fromDocument("task", "home", data() + ("submittedAt" to null)) }
    }
}
