package com.rommie.app.data.repository

import com.rommie.app.data.firebase.ChoreStore
import com.rommie.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChoreRepositoryTest {
    private var uid: String? = "creator"
    private val store = FakeStore()
    private val repo = FirebaseChoreRepository({ uid }, store)
    private val input = ChoreDetails(" Kitchen ", 30, Difficulty.MEDIUM, 15)

    @Test fun createUsesSessionIdentityAndNormalizedInputAndScopedReads() = runBlocking<Unit> {
        val chore = repo.createChore("house", input)
        assertEquals("creator", chore.createdBy)
        assertEquals("house", chore.householdId)
        assertEquals("Kitchen", chore.title)
        assertEquals(chore, repo.getChore("house", chore.id))
        assertEquals(listOf(chore), repo.getHouseholdChores("house"))
        assertNull(repo.getChore("other", chore.id))
        assertTrue(repo.getHouseholdChores("other").isEmpty())
    }
    @Test fun allOperationsRequireAuthentication() {
        uid = null
        listOf<suspend () -> Any?>(
            { repo.createChore("h", input) }, { repo.getChore("h", "c") },
            { repo.getHouseholdChores("h") }, { repo.updateChore("h", "c", input) }
        ).forEach { operation -> assertThrows(NotAuthenticatedException::class.java) { runBlocking { operation() } } }
        assertEquals(0, store.calls)
    }
    @Test fun invalidInputOrPathsNeverReachStore() {
        listOf<suspend () -> Any?>(
            { repo.createChore("h", input.copy(maxPoints = -1)) },
            { repo.updateChore("h", "c", input.copy(estimatedMinutes = 0)) },
            { repo.getChore("h", "../x") }, { repo.getHouseholdChores("a/b") }
        ).forEach { operation -> assertThrows(IllegalArgumentException::class.java) { runBlocking { operation() } } }
        assertEquals(0, store.calls)
    }
    @Test fun updatePassesOnlyEditableInputAndMissingUpdateFails() = runBlocking<Unit> {
        val old = repo.createChore("h", input)
        uid = "joiner"
        val updated = repo.updateChore("h", old.id, input.copy(title = "Bathroom", difficulty = Difficulty.HARD, recurrence = Recurrence.EveryDays(3)))
        assertEquals(old.id, updated.id)
        assertEquals(old.createdBy, updated.createdBy)
        assertEquals("Bathroom", updated.title)
        assertEquals("joiner", store.lastCaller)
        assertThrows(ChoreNotFoundException::class.java) { runBlocking { repo.updateChore("other", old.id, input) } }
    }
    @Test fun sessionChangeDiscardsResult() {
        store.afterCall = { uid = "other" }
        assertThrows(SessionChangedException::class.java) { runBlocking { repo.getHouseholdChores("h") } }
    }
    @Test fun cancellationAndBackendFailureAreNotSwallowed() {
        val cancellation = CancellationException("cancelled")
        store.failure = cancellation
        assertSame(cancellation, assertThrows(CancellationException::class.java) { runBlocking { repo.getHouseholdChores("h") } })
        val denied = IllegalStateException("backend denied")
        store.failure = denied
        assertSame(denied, assertThrows(IllegalStateException::class.java) { runBlocking { repo.createChore("h", input) } })
    }

    private class FakeStore : ChoreStore {
        var calls = 0
        var afterCall: () -> Unit = {}
        var failure: Exception? = null
        var lastCaller: String? = null
        val records = mutableMapOf<Pair<String, String>, Chore>()
        private fun called() { calls++; failure?.let { throw it }; afterCall() }
        override suspend fun create(uid: String, householdId: String, details: ChoreDetails): Chore {
            called(); lastCaller = uid
            return Chore("c$calls", householdId, details.title, details.description, details.estimatedMinutes,
                details.difficulty, details.maxPoints, uid, details.recurrence).also { records[householdId to it.id] = it }
        }
        override suspend fun get(householdId: String, choreId: String): Chore? { called(); return records[householdId to choreId] }
        override suspend fun list(householdId: String): List<Chore> { called(); return records.values.filter { it.householdId == householdId } }
        override suspend fun update(uid: String, householdId: String, choreId: String, details: ChoreDetails): Chore {
            called(); lastCaller = uid
            val old = records[householdId to choreId] ?: throw ChoreNotFoundException()
            return old.copy(title = details.title, description = details.description, estimatedMinutes = details.estimatedMinutes,
                difficulty = details.difficulty, maxPoints = details.maxPoints, recurrence = details.recurrence)
                .also { records[householdId to choreId] = it }
        }
    }
}
