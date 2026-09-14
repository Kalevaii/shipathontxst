package com.rommie.app.data.firebase

import com.rommie.app.model.TaskAssignment
import com.rommie.app.model.TaskStatus

/** Uses the shared assignment model; creation audit timestamps stay wire metadata. */
object AssignmentMappers {
    fun toDocument(assignment: TaskAssignment): Map<String, Any> = mapOf(
        "householdId" to assignment.householdId, "choreId" to assignment.choreId,
        "assignedUserId" to assignment.assignedUserId, "dueAt" to assignment.dueAt,
        "workloadValue" to assignment.workloadValue.toLong(), "maxPoints" to assignment.maxPoints.toLong(),
        "status" to assignment.status.name,
    )
    fun fromDocument(id: String, householdId: String, fields: Map<String, Any?>): TaskAssignment {
        fun string(key: String) = (fields[key] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Invalid $key")
        fun integer(key: String): Long = when (val value = fields[key]) {
            is Long -> value
            is Int -> value.toLong()
            else -> throw IllegalArgumentException("Invalid integer $key")
        }
        require(string("householdId") == householdId) { "Assignment household does not match its path" }
        val dueAt = integer("dueAt").also { require(it in 1..253402300799999L) }
        val workload = integer("workloadValue").also { require(it in 1..3) }
        val points = integer("maxPoints").also { require(it in 0..1000) }
        val choreId = string("choreId"); val userId = string("assignedUserId")
        FirestorePaths.assignment(householdId, id)
        FirestorePaths.chore(householdId, choreId)
        FirestorePaths.member(householdId, userId)
        return TaskAssignment(id, householdId, choreId, userId, dueAt, workload.toInt(), points.toInt(),
            TaskStatus.valueOf(string("status")))
    }
}
