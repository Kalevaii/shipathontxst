package com.rommie.app.data.repository

import com.rommie.app.model.User

interface UserRepository {
    /** Online, idempotent create: returns an existing profile without overwriting it. */
    suspend fun createCurrentUserProfile(name: String): User
    /** Null means no document; signed-out calls throw NotAuthenticatedException. */
    suspend fun fetchCurrentUserProfile(): User?
    /** Updates only name; email is owned by Firebase Authentication. Requires an existing profile. */
    suspend fun updateCurrentUserName(name: String): User
}
