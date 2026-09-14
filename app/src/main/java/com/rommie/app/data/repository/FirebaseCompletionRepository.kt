package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.firebase.CompletionMappers
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.TaskCompletion
import kotlinx.coroutines.tasks.await

class FirebaseCompletionRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : CompletionRepository {
    override suspend fun submitCompletion(householdId: String, assignmentId: String, proof: ProofSubmission): TaskCompletion = session { uid ->
        ProofValidation.validate(householdId, assignmentId, uid, proof)
        val completion = db.document(FirestorePaths.completion(householdId, assignmentId))
        val assignment = db.document(FirestorePaths.assignment(householdId, assignmentId))
        try {
            db.runTransaction { tx ->
                if (auth.currentUser?.uid != uid) throw SessionChangedException()
                val existing = tx.get(completion)
                val task = tx.get(assignment)
                check(task.getString("assignedUserId") == uid) { "Only the assigned worker can submit completion" }
                if (existing.exists()) {
                    val saved = CompletionMappers.fromDocument(assignmentId, householdId, checkNotNull(existing.data))
                    if (saved.completedBy != uid || saved.proof != listOf(proof)) throw CompletionConflictException()
                } else {
                    check(task.getString("status") == "ASSIGNED") { "Assignment is not awaiting completion" }
                    tx.set(completion, mapOf("householdId" to householdId, "assignmentId" to assignmentId,
                        "completedBy" to uid, "proof" to listOf(CompletionMappers.proofDocument(proof)),
                        "submittedAt" to FieldValue.serverTimestamp(), "result" to null))
                    tx.update(assignment, "status", "AWAITING_VERIFICATION")
                }
            }.await()
        } catch (failure: Exception) {
            var cause: Throwable? = failure
            while (cause != null) {
                if (cause is CompletionConflictException || cause is SessionChangedException) throw cause
                cause = cause.cause
            }
            throw failure
        }
        val saved = completion.get(Source.SERVER).await()
        CompletionMappers.fromDocument(saved.id, householdId, checkNotNull(saved.data))
    }
    override suspend fun getCompletion(householdId: String, assignmentId: String): TaskCompletion? = session {
        val doc = db.document(FirestorePaths.completion(householdId, assignmentId)).get(Source.SERVER).await()
        if (doc.exists()) CompletionMappers.fromDocument(doc.id, householdId, checkNotNull(doc.data)) else null
    }
    override suspend fun getHouseholdCompletions(householdId: String): List<TaskCompletion> = session {
        db.collection(FirestorePaths.completions(householdId)).get(Source.SERVER).await().documents.map {
            CompletionMappers.fromDocument(it.id, householdId, checkNotNull(it.data))
        }.sortedWith(compareBy({ it.submittedAt }, { it.id }))
    }
    private suspend fun <T> session(block: suspend (String) -> T): T {
        val uid = auth.currentUser?.uid ?: throw NotAuthenticatedException()
        return block(uid).also { if (auth.currentUser?.uid != uid) throw SessionChangedException() }
    }
}
