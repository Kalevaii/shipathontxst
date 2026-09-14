package com.rommie.app.data.firebase

import com.rommie.app.data.repository.HouseholdMemberSummary
import com.rommie.app.model.Household
import com.rommie.app.model.HouseholdMember
import com.rommie.app.model.User

internal object HouseholdMappers {
    fun household(id: String, fields: Map<String, Any?>) = Household(
        id, string(fields, "name"), string(fields, "inviteCode"),
    )
    fun member(householdId: String, uid: String, fields: Map<String, Any?>): HouseholdMemberSummary {
        require(string(fields, "userId") == uid) { "Member UID does not match document path" }
        val points = fields["contributionPoints"]
        val number = when (points) {
            is Long -> points
            is Int -> points.toLong()
            else -> throw IllegalArgumentException("Invalid contributionPoints")
        }
        require(number in 0..Int.MAX_VALUE.toLong())
        return HouseholdMemberSummary(HouseholdMember(householdId, uid, number.toInt()), string(fields, "displayName"))
    }
    fun memberFields(user: User): Map<String, Any> = mapOf(
        "userId" to user.id, "displayName" to user.name, "contributionPoints" to 0L,
    )
    fun string(fields: Map<String, Any?>, key: String): String =
        (fields[key] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing/invalid $key")
}
