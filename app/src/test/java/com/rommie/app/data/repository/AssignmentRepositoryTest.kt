package com.rommie.app.data.repository

import com.rommie.app.data.firebase.AssignmentStore
import com.rommie.app.model.TaskAssignment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AssignmentRepositoryTest {
    private var uid: String? = "caller"
    private val store = FakeStore()
    private val repo = FirebaseAssignmentRepository({ uid }, store)
    @Test fun createUsesCallerAndExplicitOccurrenceIdentity() = runBlocking<Unit> {
        val a = repo.createAssignment("h", "occurrence", "c", "worker", 1000)
        assertEquals("caller", store.caller)
        assertEquals("worker", a.assignedUserId)
        assertEquals(a, repo.getAssignment("h", "occurrence"))
        repo.getAssignmentsForUser("h", "worker")
        assertEquals("worker", store.filter)
        repo.getHouseholdAssignments("h")
        assertNull(store.filter)
    }
    @Test fun invalidTimeAndIdentifiersDoNotReachStore() {
        listOf<suspend () -> Any?>(
            { repo.createAssignment("h", "occ", "c", "u", 0) },
            { repo.createAssignment("h", "../occ", "c", "u", 1) },
            { repo.getAssignment("h", "a/b") }, { repo.getAssignmentsForUser("h", "a/b") }
        ).forEach { action -> assertThrows(IllegalArgumentException::class.java) { runBlocking { action() } } }
        assertEquals(0, store.calls)
    }
    @Test fun signedOutOperationsAreRejected() {
        uid = null
        listOf<suspend () -> Any?>(
            { repo.createAssignment("h", "occ", "c", "u", 1) }, { repo.getAssignment("h", "occ") },
            { repo.getHouseholdAssignments("h") }, { repo.getAssignmentsForUser("h", "u") }
        ).forEach { action -> assertThrows(NotAuthenticatedException::class.java) { runBlocking { action() } } }
    }
    @Test fun accountSwitchDiscardsSuccessfulResult() {
        store.onCall = { uid = "different" }
        assertThrows(SessionChangedException::class.java) { runBlocking { repo.getHouseholdAssignments("h") } }
    }
    @Test fun backendErrorsAndCancellationPropagate() {
        listOf(AssignmentConflictException(), CancellationException("cancelled")).forEach { expected ->
            store.failure = expected
            val caught = assertThrows(expected.javaClass) { runBlocking { repo.createAssignment("h", "occ", "c", "u", 1) } }
            assertSame(expected, caught)
        }
    }
    private class FakeStore : AssignmentStore {
        var calls = 0; var caller: String? = null; var filter: String? = null
        var onCall: () -> Unit = {}; var failure: Exception? = null
        var assignment: TaskAssignment? = null
        fun called() { calls++; failure?.let { throw it }; onCall() }
        override suspend fun create(callerUid: String, householdId: String, assignmentId: String, choreId: String, assignedUserId: String, dueAt: Long): TaskAssignment {
            called(); caller = callerUid
            return TaskAssignment(assignmentId, householdId, choreId, assignedUserId, dueAt, 2, 10).also { assignment = it }
        }
        override suspend fun get(householdId: String, assignmentId: String): TaskAssignment? { called(); return assignment }
        override suspend fun list(householdId: String, userId: String?): List<TaskAssignment> { called(); filter = userId; return listOfNotNull(assignment) }
    }
}
