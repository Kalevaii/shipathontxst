package com.rommie.app.model

enum class Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }
enum class AvailabilityKind { AVAILABLE, CLASS, WORK, DAY_OFF, AWAY, UNAVAILABLE }

// Local wall-clock minutes in the supplied IANA time zone; split overnight windows.
data class Availability(
    val id: String,
    val householdId: String,
    val userId: String,
    val weekday: Weekday,
    val startMinute: Int,
    val endMinute: Int,
    val kind: AvailabilityKind,
    val timeZoneId: String,
) {
    init { require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute) }
}

// Absolute time window overriding the weekly schedule; times are UTC epoch milliseconds.
data class AvailabilityOverride(
    val id: String,
    val householdId: String,
    val userId: String,
    val startsAt: Long,
    val endsAt: Long,
    val kind: AvailabilityKind,
) {
    init { require(endsAt > startsAt) }
}
