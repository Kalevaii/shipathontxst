package com.rommie.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.repository.DuplicateReviewException
import com.rommie.app.data.repository.ReviewValidation
import com.rommie.app.data.repository.SessionChangedException
import kotlinx.coroutines.tasks.await

internal class FirebaseReviewStore(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : ReviewStore {
    override suspend fun submit(uid: String, householdId: String, completionId: String, rating: Int) {
        val review = db.document(FirestorePaths.review(householdId, completionId, uid))
        try {
            db.runTransaction { tx ->
                if (auth.currentUser?.uid != uid) throw SessionChangedException()
                val completionDoc = tx.get(db.document(FirestorePaths.completion(householdId, completionId)))
                val assignmentDoc = tx.get(db.document(FirestorePaths.assignment(householdId, completionId)))
                require(completionDoc.exists() && assignmentDoc.exists()) { "Completion or assignment does not exist" }
                val completion = CompletionMappers.fromDocument(completionId, householdId, checkNotNull(completionDoc.data))
                val assignment = AssignmentMappers.fromDocument(completionId, householdId, checkNotNull(assignmentDoc.data))
                ReviewValidation.eligible(householdId, completionId, uid, completion, assignment)
                if (tx.get(review).exists()) throw DuplicateReviewException()
                tx.set(review, mapOf("householdId" to householdId, "completionId" to completionId,
                    "reviewerId" to uid, "rating" to rating.toLong(), "submittedAt" to FieldValue.serverTimestamp()))
            }.await()
        } catch (failure: Exception) {
            var cause: Throwable? = failure
            while (cause != null) {
                if (cause is DuplicateReviewException || cause is SessionChangedException || cause is IllegalArgumentException) throw cause
                cause = cause.cause
            }
            throw failure
        }
    }
    override suspend fun exists(uid: String, householdId: String, completionId: String): Boolean {
        val doc = db.document(FirestorePaths.review(householdId, completionId, uid)).get(Source.SERVER).await()
        if (doc.exists()) ReviewMappers.fromDocument(uid, householdId, completionId, checkNotNull(doc.data))
        return doc.exists()
    }
}
