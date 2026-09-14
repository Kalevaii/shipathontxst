package com.rommie.app.data.repository

import com.rommie.app.model.TaskAssignment

interface AssignmentRepository {
    /** Reuse the same occurrence ID on retries. Selection belongs to FairAssignmentEngine. */
    suspend fun createAssignment(householdId: String, assignmentId: String, choreId: String,
        assignedUserId: String, dueAt: Long): TaskAssignment
    suspend fun getAssignment(householdId: String, assignmentId: String): TaskAssignment?
    suspend fun getHouseholdAssignments(householdId: String): List<TaskAssignment>
    suspend fun getAssignmentsForUser(householdId: String, userId: String): List<TaskAssignment>
}
class AssignmentConflictException : Exception("Occurrence ID already belongs to a different assignment")
class AssignmentReferenceException : Exception("The chore or assigned household member does not exist")
