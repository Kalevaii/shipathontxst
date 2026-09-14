package com.rommie.app.data.repository

import com.rommie.app.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Session identity only; a signed-in account may still need a Roomie profile. */
    val currentUserId: String?
    /** Emits current UID immediately on collection, then sign-in/sign-out changes. */
    val authState: Flow<String?>
    suspend fun signUp(name: String, email: String, password: String): User
    /** Null means authenticated but profile missing; call UserRepository.createCurrentUserProfile. */
    suspend fun signIn(email: String, password: String): User?
    suspend fun signOut()
}

class ProfileProvisioningException(val userId: String, cause: Exception) :
    Exception("Account exists, but the profile could not be saved. Retry profile creation while signed in.", cause)

class NotAuthenticatedException : IllegalStateException("Sign in to access your profile")
class SessionChangedException : IllegalStateException("The authenticated account changed; reload the profile")
