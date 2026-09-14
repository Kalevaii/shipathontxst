package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.firebase.FirestoreMappers
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.model.User
import kotlinx.coroutines.tasks.await

class FirebaseUserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : UserRepository {
    override suspend fun createCurrentUserProfile(name: String): User {
        val validName = ProfileValidation.name(name)
        val account = auth.currentUser ?: throw NotAuthenticatedException()
        val uid = account.uid
        val email = checkNotNull(account.email) { "An email/password account is required" }
        val candidate = User(uid, validName, email)
        val document = firestore.document(FirestorePaths.user(uid))
        val profile = firestore.runTransaction { transaction ->
            requireSession(uid)
            val existing = transaction.get(document)
            if (existing.exists()) {
                FirestoreMappers.userFromDocument(uid, checkNotNull(existing.data))
            } else {
                transaction.set(document, FirestoreMappers.userToDocument(candidate))
                candidate
            }
        }.await()
        requireSession(uid)
        return profile
    }

    override suspend fun fetchCurrentUserProfile(): User? {
        val uid = auth.currentUser?.uid ?: throw NotAuthenticatedException()
        // Do not mistake a cached missing document for confirmed missing data, or return stale account data.
        val document = firestore.document(FirestorePaths.user(uid)).get(Source.SERVER).await()
        requireSession(uid)
        return if (document.exists()) FirestoreMappers.userFromDocument(uid, checkNotNull(document.data)) else null
    }

    override suspend fun updateCurrentUserName(name: String): User {
        val validName = ProfileValidation.name(name)
        val uid = auth.currentUser?.uid ?: throw NotAuthenticatedException()
        val document = firestore.document(FirestorePaths.user(uid))
        val profile = firestore.runTransaction { transaction ->
            requireSession(uid)
            val existing = transaction.get(document)
            check(existing.exists()) { "Create the user profile before updating it" }
            val user = FirestoreMappers.userFromDocument(uid, checkNotNull(existing.data))
            transaction.update(document, "name", validName)
            user.copy(name = validName)
        }.await()
        requireSession(uid)
        return profile
    }

    private fun requireSession(uid: String) {
        if (auth.currentUser?.uid != uid) throw SessionChangedException()
    }
}
