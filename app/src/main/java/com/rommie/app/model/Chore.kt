package com.rommie.app.model

// Null recurrence means one-time. Weekly weekdays supports twice-weekly chores.
sealed interface Recurrence {
    data class EveryDays(val interval: Int) : Recurrence {
        init { require(interval > 0) }
    }
    data class Weekly(val weekdays: Set<Weekday>) : Recurrence {
        init { require(weekdays.isNotEmpty()) }
    }
}

data class Chore(
    val id: String,
    val householdId: String,
    val title: String,
    val description: String = "",
    val estimatedMinutes: Int,
    val difficulty: Difficulty,
    val maxPoints: Int,
    val createdBy: String,
    val recurrence: Recurrence? = null,
) {
    init { require(title.isNotBlank() && estimatedMinutes > 0 && maxPoints >= 0) }
}
