package com.rommie.app.data.repository

import com.rommie.app.model.TaskAssignment
import com.rommie.app.model.TaskCompletion
import com.rommie.app.model.TaskStatus

object ReviewValidation {
    fun rating(value: Int) { require(value in 1..10) { "Rating must be from 1 to 10" } }
    fun eligible(householdId: String, completionId: String, reviewerUid: String,
        completion: TaskCompletion, assignment: TaskAssignment) {
        require(completion.id == completionId && completion.assignmentId == completionId && completion.householdId == householdId)
        require(assignment.id == completionId && assignment.householdId == householdId)
        require(completion.completedBy == assignment.assignedUserId)
        require(reviewerUid != completion.completedBy && reviewerUid != assignment.assignedUserId) { "You cannot review your own work" }
        require(completion.result == null && assignment.status == TaskStatus.AWAITING_VERIFICATION) { "Completion is not awaiting verification" }
    }
}
