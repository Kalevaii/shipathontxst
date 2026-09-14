package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.rommie.app.data.firebase.AuthGateway
import com.rommie.app.data.firebase.FirebaseAuthGateway
import com.rommie.app.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirebaseAuthRepository internal constructor(
    private val gateway: AuthGateway,
    private val users: UserRepository,
) : AuthRepository {
    constructor(auth: FirebaseAuth, users: UserRepository) : this(FirebaseAuthGateway(auth), users)

    // Share one repository instance. Serialize session-changing operations from ViewModels.
    private val sessionMutex = Mutex()
    override val currentUserId: String? get() = gateway.currentUserId
    override val authState: Flow<String?> get() = gateway.authState

    override suspend fun signUp(name: String, email: String, password: String): User = sessionMutex.withLock {
        val validName = ProfileValidation.name(name)
        val validEmail = ProfileValidation.email(email)
        val validPassword = ProfileValidation.password(password)
        check(currentUserId == null) { "Sign out before creating another account" }
        val uid = gateway.signUp(validEmail, validPassword)
        requireSession(uid)
        try {
            users.createCurrentUserProfile(validName).also {
                requireSession(uid)
                if (it.id != uid) throw SessionChangedException()
            }
        } catch (cancelled: CancellationException) {
            // Firebase tasks may still finish remotely. Recover by inspecting session/profile on resume.
            throw cancelled
        } catch (changed: SessionChangedException) {
            throw changed
        } catch (failure: Exception) {
            throw ProfileProvisioningException(uid, failure)
        }
    }

    override suspend fun signIn(email: String, password: String): User? = sessionMutex.withLock {
        val uid = gateway.signIn(ProfileValidation.email(email), ProfileValidation.password(password))
        requireSession(uid)
        users.fetchCurrentUserProfile().also {
            requireSession(uid)
            if (it != null && it.id != uid) throw SessionChangedException()
        }
    }

    override suspend fun signOut() = sessionMutex.withLock { gateway.signOut() }

    private fun requireSession(uid: String) {
        if (currentUserId != uid) throw SessionChangedException()
    }
}
