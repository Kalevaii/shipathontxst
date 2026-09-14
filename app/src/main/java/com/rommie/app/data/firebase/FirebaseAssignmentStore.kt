package com.rommie.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.repository.AssignmentConflictException
import com.rommie.app.data.repository.AssignmentReferenceException
import com.rommie.app.data.repository.SessionChangedException
import com.rommie.app.domain.workloadValue
import com.rommie.app.model.TaskAssignment
import kotlinx.coroutines.tasks.await

internal class FirebaseAssignmentStore(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : AssignmentStore {
    override suspend fun create(callerUid: String, householdId: String, assignmentId: String,
        choreId: String, assignedUserId: String, dueAt: Long): TaskAssignment {
        val ref = db.document(FirestorePaths.assignment(householdId, assignmentId))
        try {
            return db.runTransaction { tx ->
                if (auth.currentUser?.uid != callerUid) throw SessionChangedException()
                val existing = tx.get(ref)
                if (existing.exists()) {
                    val old = AssignmentMappers.fromDocument(existing.id, householdId, checkNotNull(existing.data))
                    if (old.choreId != choreId || old.assignedUserId != assignedUserId || old.dueAt != dueAt)
                        throw AssignmentConflictException()
                    old // Retry returns the original snapshot, even if the chore was edited meanwhile.
                } else {
                    val choreDoc = tx.get(db.document(FirestorePaths.chore(householdId, choreId)))
                    val memberDoc = tx.get(db.document(FirestorePaths.member(householdId, assignedUserId)))
                    if (!choreDoc.exists() || memberDoc.getString("userId") != assignedUserId)
                        throw AssignmentReferenceException()
                    val chore = FirestoreMappers.choreFromDocument(choreId, householdId, checkNotNull(choreDoc.data))
                    val assignment = TaskAssignment(assignmentId, householdId, choreId, assignedUserId, dueAt,
                        workloadValue(chore.difficulty), chore.maxPoints)
                    tx.set(ref, AssignmentMappers.toDocument(assignment) + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(), "createdBy" to callerUid))
                    assignment
                }
            }.await()
        } catch (failure: Exception) {
            var cause: Throwable? = failure
            while (cause != null) {
                if (cause is AssignmentConflictException || cause is AssignmentReferenceException || cause is SessionChangedException) throw cause
                cause = cause.cause
            }
            throw failure
        }
    }
    override suspend fun get(householdId: String, assignmentId: String): TaskAssignment? {
        val doc = db.document(FirestorePaths.assignment(householdId, assignmentId)).get(Source.SERVER).await()
        return if (doc.exists()) AssignmentMappers.fromDocument(doc.id, householdId, checkNotNull(doc.data)) else null
    }
    override suspend fun list(householdId: String, userId: String?): List<TaskAssignment> {
        val collection = db.collection(FirestorePaths.assignments(householdId))
        val query = if (userId == null) collection else collection.whereEqualTo("assignedUserId", userId)
        return query.get(Source.SERVER).await().documents.map {
            AssignmentMappers.fromDocument(it.id, householdId, checkNotNull(it.data))
        }.sortedWith(compareBy({ it.dueAt }, { it.id }))
    }
}
