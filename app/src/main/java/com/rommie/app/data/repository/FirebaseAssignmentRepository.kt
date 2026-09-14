package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rommie.app.data.firebase.AssignmentStore
import com.rommie.app.data.firebase.FirebaseAssignmentStore
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.model.TaskAssignment

class FirebaseAssignmentRepository internal constructor(
    private val currentUid: () -> String?, private val store: AssignmentStore,
) : AssignmentRepository {
    constructor(auth: FirebaseAuth, firestore: FirebaseFirestore) :
        this({ auth.currentUser?.uid }, FirebaseAssignmentStore(auth, firestore))

    override suspend fun createAssignment(householdId: String, assignmentId: String, choreId: String,
        assignedUserId: String, dueAt: Long): TaskAssignment = session { uid ->
        FirestorePaths.assignment(householdId, assignmentId)
        FirestorePaths.chore(householdId, choreId)
        FirestorePaths.member(householdId, assignedUserId)
        require(dueAt in 1..253402300799999L) { "Due time must be a valid positive UTC epoch millisecond value" }
        store.create(uid, householdId, assignmentId, choreId, assignedUserId, dueAt)
    }
    override suspend fun getAssignment(householdId: String, assignmentId: String): TaskAssignment? = session {
        FirestorePaths.assignment(householdId, assignmentId)
        store.get(householdId, assignmentId)
    }
    override suspend fun getHouseholdAssignments(householdId: String): List<TaskAssignment> = session {
        FirestorePaths.assignments(householdId)
        store.list(householdId)
    }
    override suspend fun getAssignmentsForUser(householdId: String, userId: String): List<TaskAssignment> = session {
        FirestorePaths.member(householdId, userId)
        store.list(householdId, userId)
    }
    private suspend fun <T> session(block: suspend (String) -> T): T {
        val uid = currentUid() ?: throw NotAuthenticatedException()
        return block(uid).also { if (currentUid() != uid) throw SessionChangedException() }
    }
}
