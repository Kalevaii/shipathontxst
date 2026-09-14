package com.rommie.app.data.repository

import com.rommie.app.data.firebase.AuthGateway
import com.rommie.app.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FirebaseAuthRepositoryTest {
    private class FakeAuth : AuthGateway {
        override val authState = MutableStateFlow<String?>(null)
        override val currentUserId get() = authState.value
        var calls = 0
        var email = ""
        var password = ""
        var failure: Exception? = null
        override suspend fun signUp(email: String, password: String): String {
            calls++
            failure?.let { throw it }
            this.email = email
            this.password = password
            authState.value = "uid-1"
            return "uid-1"
        }
        override suspend fun signIn(email: String, password: String) = signUp(email, password)
        override fun signOut() { authState.value = null }
    }
    private class FakeUsers : UserRepository {
        var profile: User? = null
        var failure: Exception? = null
        var createCalls = 0
        override suspend fun createCurrentUserProfile(name: String): User {
            createCalls++
            failure?.let { throw it }
            return profile ?: User("uid-1", name, "alex@example.com").also { profile = it }
        }
        override suspend fun fetchCurrentUserProfile(): User? {
            failure?.let { throw it }
            return profile
        }
        override suspend fun updateCurrentUserName(name: String): User = error("Not used")
    }

    @Test fun signupCreatesSharedProfileAndNormalizesOnlyNameAndEmail() = runBlocking<Unit> {
        val auth = FakeAuth()
        val users = FakeUsers()
        val repo = FirebaseAuthRepository(auth, users)
        assertEquals(User("uid-1", "Alex", "alex@example.com"),
            repo.signUp(" Alex ", " alex@example.com ", " password "))
        assertEquals("alex@example.com", auth.email)
        assertEquals(" password ", auth.password)
        assertEquals("uid-1", repo.currentUserId)
        assertEquals("uid-1", repo.authState.first())
        assertEquals(1, users.createCalls)
    }

    @Test fun invalidSignupDoesNotCreateAuthAccount() = runBlocking<Unit> {
        val auth = FakeAuth()
        val users = FakeUsers()
        val repo = FirebaseAuthRepository(auth, users)
        expect<IllegalArgumentException> { repo.signUp(" ", "alex@example.com", "password") }
        expect<IllegalArgumentException> { repo.signUp("Alex", "bad-email", "password") }
        expect<IllegalArgumentException> { repo.signUp("Alex", "alex@example.com", "") }
        assertEquals(0, auth.calls)
        assertEquals(0, users.createCalls)
    }

    @Test fun authFailureDoesNotAttemptProfileCreation() = runBlocking<Unit> {
        val auth = FakeAuth().apply { failure = IllegalStateException("rejected") }
        val users = FakeUsers()
        expect<IllegalStateException> {
            FirebaseAuthRepository(auth, users).signUp("Alex", "alex@example.com", "password")
        }
        assertEquals(0, users.createCalls)
    }

    @Test fun profileFailureRetainsSessionAndCanBeRecoveredWithoutAnotherSignup() = runBlocking<Unit> {
        val auth = FakeAuth()
        val failure = IllegalStateException("offline")
        val users = FakeUsers().apply { this.failure = failure }
        val repo = FirebaseAuthRepository(auth, users)
        val error = expect<ProfileProvisioningException> { repo.signUp("Alex", "alex@example.com", "password") }
        assertEquals("uid-1", error.userId)
        assertSame(failure, error.cause)
        assertEquals("uid-1", repo.currentUserId)
        users.failure = null
        assertEquals("Alex", users.createCurrentUserProfile("Alex").name)
        assertEquals(1, auth.calls)
    }

    @Test fun cancellationIsNotReportedAsAnOrdinaryProfileFailure() = runBlocking<Unit> {
        val users = FakeUsers().apply { failure = CancellationException("cancelled") }
        expect<CancellationException> {
            FirebaseAuthRepository(FakeAuth(), users).signUp("Alex", "alex@example.com", "password")
        }
    }

    @Test fun signinDistinguishesMissingProfileFromReadFailureAndSupportsSignout() = runBlocking<Unit> {
        val auth = FakeAuth()
        val users = FakeUsers()
        val repo = FirebaseAuthRepository(auth, users)
        assertNull(repo.signIn("alex@example.com", "password"))
        assertEquals("uid-1", repo.currentUserId)
        users.profile = User("uid-1", "Alex", "alex@example.com")
        assertEquals(users.profile, repo.signIn("alex@example.com", "password"))
        users.failure = IllegalStateException("offline")
        expect<IllegalStateException> { repo.signIn("alex@example.com", "password") }
        repo.signOut()
        assertNull(repo.currentUserId)
        assertNull(repo.authState.first())
    }

    @Test fun refusesSignupWhileAlreadySignedInAndRejectsMismatchedProfile() = runBlocking<Unit> {
        val auth = FakeAuth().apply { authState.value = "existing" }
        val users = FakeUsers()
        val repo = FirebaseAuthRepository(auth, users)
        expect<IllegalStateException> { repo.signUp("Alex", "alex@example.com", "password") }
        assertEquals(0, auth.calls)
        users.profile = User("someone-else", "Other", "other@example.com")
        expect<SessionChangedException> { repo.signIn("alex@example.com", "password") }
    }

    private suspend inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try { block() } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        error("Expected ${T::class.simpleName}")
    }
}
