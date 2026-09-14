package com.rommie.app.data.repository

import com.rommie.app.data.firebase.HouseholdStore
import com.rommie.app.model.Household
import com.rommie.app.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HouseholdRepositoryTest {
    private class Fixture {
        var uid: String? = "u"
        var profile: User? = User("u", "Alex", "private@example.com")
        var current: String? = null
        var collisions = 0
        var attempts = 0
        var receivedCode = ""
        var failure: Exception? = null
        val house = Household("h", "House", "TXST24")
        val users = object : UserRepository {
            override suspend fun fetchCurrentUserProfile() = profile
            override suspend fun createCurrentUserProfile(name: String): User = error("unused")
            override suspend fun updateCurrentUserName(name: String): User = error("unused")
        }
        val store = object : HouseholdStore {
            override suspend fun create(user: User, name: String, code: String): Household {
                attempts++
                failure?.let { throw it }
                HouseholdValidation.requireNoMembership(current)
                if (collisions-- > 0) throw HouseholdException(HouseholdError.CODE_COLLISION)
                current = "h"
                return house.copy(name = name, inviteCode = code)
            }
            override suspend fun join(user: User, code: String): Household {
                failure?.let { throw it }
                HouseholdValidation.requireNoMembership(current)
                receivedCode = code
                if (code != house.inviteCode) throw HouseholdException(HouseholdError.NOT_FOUND)
                current = "h"
                return house
            }
            override suspend fun currentHouseholdId(uid: String) = current
            override suspend fun get(id: String): Household? = if (id == "h") house else null
            override suspend fun members(id: String) = emptyList<HouseholdMemberSummary>()
        }
        val repo = FirebaseHouseholdRepository({ uid }, users, store) { "ABC234" }
    }
    @Test fun creationRetriesCodeCollisionAndTrimsName() = runBlocking<Unit> {
        val f = Fixture().apply { collisions = 2 }
        assertEquals("My House", f.repo.createHousehold(" My House ").name)
        assertEquals(3, f.attempts)
        assertEquals(f.house, f.repo.getCurrentUserHousehold())
    }
    @Test fun collisionRetriesAreBounded() = runBlocking<Unit> {
        val f = Fixture().apply { collisions = 10 }
        assertEquals(HouseholdError.CODE_COLLISION, expect<HouseholdException> { f.repo.createHousehold("House") }.reason)
        assertEquals(5, f.attempts)
    }
    @Test fun joiningNormalizesCodeAndPreventsDuplicateMembership() = runBlocking<Unit> {
        val f = Fixture()
        assertNull(f.repo.getCurrentUserHousehold())
        assertEquals(f.house, f.repo.joinHouseholdByCode(" txst24 "))
        assertEquals("TXST24", f.receivedCode)
        assertEquals(HouseholdError.ALREADY_JOINED, expect<HouseholdException> { f.repo.joinHouseholdByCode("TXST24") }.reason)
        assertEquals(HouseholdError.ALREADY_JOINED, expect<HouseholdException> { f.repo.createHousehold("Other") }.reason)
    }
    @Test fun rejectsInvalidUnknownAndUnauthenticatedRequests() = runBlocking<Unit> {
        val f = Fixture()
        assertEquals(HouseholdError.INVALID_CODE, expect<HouseholdException> { f.repo.joinHouseholdByCode("!") }.reason)
        assertEquals(HouseholdError.NOT_FOUND, expect<HouseholdException> { f.repo.joinHouseholdByCode("ABC234") }.reason)
        f.uid = null
        expect<NotAuthenticatedException> { f.repo.createHousehold("House") }
        expect<NotAuthenticatedException> { f.repo.getCurrentUserHousehold() }
    }
    @Test fun requiresMatchingProfileAndMapsWriteFailureWithoutRetry() = runBlocking<Unit> {
        val f = Fixture()
        f.profile = null
        assertEquals(HouseholdError.PROFILE_REQUIRED, expect<HouseholdException> { f.repo.createHousehold("House") }.reason)
        f.profile = User("other", "Other", "other@example.com")
        expect<SessionChangedException> { f.repo.createHousehold("House") }
        f.profile = User("u", "Alex", "private@example.com")
        f.failure = IllegalStateException("offline")
        assertEquals(HouseholdError.WRITE_FAILED, expect<HouseholdException> { f.repo.createHousehold("House") }.reason)
        assertEquals(1, f.attempts)
    }
    @Test fun preservesCancellation() = runBlocking<Unit> {
        val f = Fixture().apply { failure = CancellationException() }
        expect<CancellationException> { f.repo.createHousehold("House") }
    }
    private suspend inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try { block() } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        error("Expected ${T::class.simpleName}")
    }
}
