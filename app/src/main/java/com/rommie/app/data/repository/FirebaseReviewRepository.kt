package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rommie.app.data.firebase.FirebaseReviewStore
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.data.firebase.ReviewStore

class FirebaseReviewRepository internal constructor(private val currentUid: () -> String?, private val store: ReviewStore) : ReviewRepository {
    constructor(auth: FirebaseAuth, firestore: FirebaseFirestore) : this({ auth.currentUser?.uid }, FirebaseReviewStore(auth, firestore))
    override suspend fun submitReview(householdId: String, completionId: String, rating: Int) = session(householdId, completionId) { uid ->
        ReviewValidation.rating(rating)
        store.submit(uid, householdId, completionId, rating)
    }
    override suspend fun hasReviewed(householdId: String, completionId: String): Boolean = session(householdId, completionId) { uid ->
        store.exists(uid, householdId, completionId)
    }
    private suspend fun <T> session(householdId: String, completionId: String, action: suspend (String) -> T): T {
        val uid = currentUid() ?: throw NotAuthenticatedException()
        FirestorePaths.completion(householdId, completionId)
        FirestorePaths.review(householdId, completionId, uid)
        return action(uid).also { if (currentUid() != uid) throw SessionChangedException() }
    }
}
