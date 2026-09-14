package com.rommie.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rommie.app.data.repository.HouseholdMemberSummary
import com.rommie.app.data.repository.HouseholdSnapshot
import com.rommie.app.domain.LeaderboardRanking
import com.rommie.app.domain.workloadValue
import com.rommie.app.model.Chore
import com.rommie.app.model.Difficulty
import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.ProofType
import com.rommie.app.model.Recurrence
import com.rommie.app.model.TaskAssignment
import com.rommie.app.model.TaskCompletion
import com.rommie.app.model.TaskStatus

class DemoStore(snapshot: HouseholdSnapshot) {
    val household = snapshot.household
    private val users = snapshot.users
    val members = snapshot.members
    var currentUserId by mutableStateOf("aayush")
        private set
    val demoUsers = users
    val rankedMembers
        get() = LeaderboardRanking.rank(
            household.id,
            members.map { member ->
                HouseholdMemberSummary(
                    member = member,
                    displayName = users.firstOrNull { it.id == member.userId }?.name ?: member.userId,
                )
            },
        )
    var chores by mutableStateOf(snapshot.chores)
        private set
    var assignments by mutableStateOf(snapshot.assignments)
        private set
    var completions by mutableStateOf(snapshot.completions)
        private set
    private var reviewedAssignmentIdsByUser by mutableStateOf(emptyMap<String, Set<String>>())
        private set

    fun switchUser(userId: String) {
        if (demoUsers.any { it.id == userId }) currentUserId = userId
    }

    fun hasReviewed(assignmentId: String): Boolean =
        reviewedAssignmentIdsByUser[currentUserId]?.contains(assignmentId) == true

    fun addChore(
        title: String,
        description: String,
        difficulty: Difficulty,
        minutes: Int,
        points: Int,
        recurrence: Recurrence?,
    ) {
        val id = "demo-${chores.size + 1}"
        val chore = Chore(
            id = id,
            householdId = household.id,
            title = title,
            description = description,
            estimatedMinutes = minutes,
            difficulty = difficulty,
            maxPoints = points,
            createdBy = currentUserId,
            recurrence = recurrence,
        )
        chores = chores + chore
        val assignee = members.firstOrNull { it.userId != currentUserId }?.userId ?: currentUserId
        assignments = assignments + TaskAssignment(
            id = "assignment-$id", householdId = household.id, choreId = id,
            assignedUserId = assignee, dueAt = System.currentTimeMillis() + 86_400_000L,
            workloadValue = workloadValue(difficulty), maxPoints = points,
        )
    }

    fun submitProof(assignmentId: String) {
        assignments = assignments.map {
            if (it.id == assignmentId) it.copy(status = TaskStatus.AWAITING_VERIFICATION) else it
        }
        completions = completions.filterNot { it.assignmentId == assignmentId } + TaskCompletion(
            id = assignmentId, assignmentId = assignmentId, householdId = household.id,
            completedBy = currentUserId,
            proof = listOf(ProofSubmission("demo-proof-$assignmentId", "demo://proof/$assignmentId", ProofType.PHOTO)),
            submittedAt = System.currentTimeMillis(),
        )
    }

    fun submitReview(assignmentId: String) {
        if (assignments.any { it.id == assignmentId && it.assignedUserId != currentUserId }) {
            reviewedAssignmentIdsByUser = reviewedAssignmentIdsByUser +
                (currentUserId to ((reviewedAssignmentIdsByUser[currentUserId] ?: emptySet()) + assignmentId))
        }
    }

    fun choreFor(assignment: TaskAssignment): Chore? = chores.firstOrNull { it.id == assignment.choreId }
}