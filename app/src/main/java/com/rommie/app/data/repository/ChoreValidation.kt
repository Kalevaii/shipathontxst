package com.rommie.app.data.repository

import com.rommie.app.model.Recurrence

/** Wire limits are mirrored by Firestore rules because clients are untrusted. */
internal object ChoreValidation {
    fun details(input: ChoreDetails): ChoreDetails {
        val title = input.title.trim()
        require(title.length in 1..100 && title.none { it.isISOControl() }) {
            "Title must contain 1–100 characters without control characters"
        }
        require(input.description.length <= 2000) { "Description is limited to 2000 characters" }
        require(input.estimatedMinutes in 1..1440) { "Duration must be 1–1440 minutes" }
        require(input.maxPoints in 0..1000) { "Maximum points must be 0–1000" }
        // Difficulty is a closed shared enum, and workload remains derived by domain.workloadValue.
        val recurrence = when (val value = input.recurrence) {
            null -> null
            is Recurrence.EveryDays -> value.also { require(it.interval in 1..365) { "Interval must be 1–365 days" } }
            is Recurrence.Weekly -> {
                require(value.weekdays.isNotEmpty()) { "Select at least one weekday" }
                value.copy(weekdays = value.weekdays.toSet())
            }
        }
        return input.copy(title = title, recurrence = recurrence)
    }
}
