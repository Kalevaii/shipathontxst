package com.rommie.app.model

// Snapshot workload and reward so editing a chore cannot change historical accounting.
// All absolute timestamps throughout the models are UTC epoch milliseconds.
data class TaskAssignment(
    val id: String,
    val householdId: String,
    val choreId: String,
    val assignedUserId: String,
    val dueAt: Long,
    val workloadValue: Int,
    val maxPoints: Int,
    val status: TaskStatus = TaskStatus.ASSIGNED,
) {
    init { require(workloadValue in 1..3 && maxPoints >= 0) }
}
