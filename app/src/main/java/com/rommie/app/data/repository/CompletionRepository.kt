package com.rommie.app.data.repository

import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.TaskCompletion

interface CompletionRepository {
    /** One immutable completion per assignment. Upload proof first; retry with identical proof. */
    suspend fun submitCompletion(householdId: String, assignmentId: String, proof: ProofSubmission): TaskCompletion
    suspend fun getCompletion(householdId: String, assignmentId: String): TaskCompletion?
    suspend fun getHouseholdCompletions(householdId: String): List<TaskCompletion>
}

class CompletionConflictException : IllegalStateException("Assignment was already completed with different proof")
