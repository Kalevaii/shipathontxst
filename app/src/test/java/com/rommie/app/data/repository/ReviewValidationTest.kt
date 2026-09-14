package com.rommie.app.data.repository

import com.rommie.app.model.*
import org.junit.Assert.*
import org.junit.Test

class ReviewValidationTest {
    private val assignment = TaskAssignment("task","home","chore","worker",1,3,20,TaskStatus.AWAITING_VERIFICATION)
    private val completion = TaskCompletion("task","task","home","worker",listOf(ProofSubmission("p","private/path",ProofType.PHOTO)),1)
    @Test fun eligibleRoommate() { ReviewValidation.eligible("home","task","peer",completion,assignment) }
    @Test fun ratingBounds() {
        ReviewValidation.rating(1); ReviewValidation.rating(10)
        for (rating in listOf(0,11)) assertThrows(IllegalArgumentException::class.java) { ReviewValidation.rating(rating) }
    }
    @Test fun cannotReviewOwnWork() {
        assertThrows(IllegalArgumentException::class.java) { ReviewValidation.eligible("home","task","worker",completion,assignment) }
    }
    @Test fun rejectsForeignScopeAndInconsistentWorker() {
        for (bad in listOf(completion.copy(householdId="other"),completion.copy(assignmentId="other"),completion.copy(completedBy="other")))
            assertThrows(IllegalArgumentException::class.java) { ReviewValidation.eligible("home","task","peer",bad,assignment) }
    }
    @Test fun onlyAwaitingStatus() {
        for (status in TaskStatus.entries.filter { it != TaskStatus.AWAITING_VERIFICATION })
            assertThrows(IllegalArgumentException::class.java) { ReviewValidation.eligible("home","task","peer",completion,assignment.copy(status=status)) }
    }
    @Test fun alreadyFinalizedIsIneligible() {
        assertThrows(IllegalArgumentException::class.java) { ReviewValidation.eligible("home","task","peer",completion.copy(result=VerificationResult(10.0,20)),assignment) }
    }
}
