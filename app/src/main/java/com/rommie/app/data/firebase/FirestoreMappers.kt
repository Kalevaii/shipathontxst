package com.rommie.app.data.firebase

import com.rommie.app.model.Chore
import com.rommie.app.model.Difficulty
import com.rommie.app.model.Recurrence
import com.rommie.app.model.User
import com.rommie.app.model.Weekday

/** Explicit wire mapping avoids reflection/default constructors and duplicate domain models. */
object FirestoreMappers {
    fun userToDocument(user: User): Map<String, Any> = mapOf(
        "name" to user.name,
        "email" to user.email,
    )

    fun userFromDocument(uid: String, fields: Map<String, Any?>): User =
        User(uid, fields.string("name"), fields.string("email"))

    fun choreToDocument(chore: Chore): Map<String, Any?> = mapOf(
        "householdId" to chore.householdId,
        "title" to chore.title,
        "description" to chore.description,
        "estimatedMinutes" to chore.estimatedMinutes.toLong(),
        "difficulty" to chore.difficulty.name,
        "maxPoints" to chore.maxPoints.toLong(),
        "createdBy" to chore.createdBy,
        "recurrence" to when (val recurrence = chore.recurrence) {
            null -> null
            is Recurrence.EveryDays -> mapOf("type" to "EVERY_DAYS", "interval" to recurrence.interval.toLong())
            is Recurrence.Weekly -> mapOf("type" to "WEEKLY", "weekdays" to recurrence.weekdays.sortedBy { it.ordinal }.map { it.name })
        },
    )

    fun choreFromDocument(id: String, householdId: String, fields: Map<String, Any?>): Chore {
        require(fields.string("householdId") == householdId) { "Chore household does not match its path" }
        require(fields.containsKey("recurrence")) { "Missing recurrence; use explicit null for one-time chores" }
        return Chore(
            id = id,
            householdId = householdId,
            title = fields.string("title"),
            description = fields.string("description"),
            estimatedMinutes = fields.integer("estimatedMinutes"),
            difficulty = Difficulty.valueOf(fields.string("difficulty")),
            maxPoints = fields.integer("maxPoints"),
            createdBy = fields.string("createdBy"),
            recurrence = recurrenceFromDocument(fields["recurrence"]),
        )
    }

    private fun recurrenceFromDocument(value: Any?): Recurrence? {
        if (value == null) return null
        require(value is Map<*, *>) { "Invalid recurrence" }
        return when (value.string("type")) {
            "EVERY_DAYS" -> {
                require(value.keys == setOf("type", "interval")) { "Invalid interval recurrence fields" }
                Recurrence.EveryDays(value.integer("interval"))
            }
            "WEEKLY" -> {
                require(value.keys == setOf("type", "weekdays")) { "Invalid weekly recurrence fields" }
                val days = value["weekdays"]
                require(days is List<*>) { "Invalid weekdays" }
                require(days.size in 1..7 && days.toSet().size == days.size) { "Weekdays must be nonempty and unique" }
                Recurrence.Weekly(days.map {
                    require(it is String) { "Invalid weekday" }
                    Weekday.valueOf(it)
                }.toSet())
            }
            else -> throw IllegalArgumentException("Unknown recurrence type")
        }
    }

    private fun Map<*, *>.string(key: String): String =
        this[key] as? String ?: throw IllegalArgumentException("Missing/invalid $key")

    private fun Map<*, *>.integer(key: String): Int {
        val value = this[key]
        val number = when (value) {
            is Long -> value
            is Int -> value.toLong()
            else -> throw IllegalArgumentException("Missing/invalid integer $key")
        }
        require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$key is out of range" }
        return number.toInt()
    }
}
