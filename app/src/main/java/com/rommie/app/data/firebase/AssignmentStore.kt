package com.rommie.app.data.firebase

import com.rommie.app.model.TaskAssignment

internal interface AssignmentStore {
    suspend fun create(callerUid: String, householdId: String, assignmentId: String, choreId: String,
        assignedUserId: String, dueAt: Long): TaskAssignment
    suspend fun get(householdId: String, assignmentId: String): TaskAssignment?
    suspend fun list(householdId: String, userId: String? = null): List<TaskAssignment>
}
