package com.rommie.app.domain

import com.rommie.app.model.*
import java.util.Calendar
import java.util.TimeZone

/** Pure deterministic selection. Call again with each persisted assignment included in history. */
object FairAssignmentEngine {
    fun selectMember(
        chore: Chore,
        members: List<HouseholdMember>,
        assignments: List<TaskAssignment>,
        dueAt: Long,
        availability: List<Availability> = emptyList(),
        overrides: List<AvailabilityOverride> = emptyList(),
        unavailableUserIds: Set<String> = emptySet(),
    ): HouseholdMember? {
        require(dueAt in 1..253402300799999L)
        require(chore.estimatedMinutes in 1..1440)
        val scopedMembers = members.filter { it.householdId == chore.householdId }
        require(scopedMembers.map { it.userId }.distinct().size == scopedMembers.size) { "Duplicate household member" }
        val history = assignments.filter { it.householdId == chore.householdId && it.status != TaskStatus.CANCELLED }
        require(history.map { it.id }.distinct().size == history.size) { "Duplicate assignment in workload history" }
        val start = dueAt - chore.estimatedMinutes * 60_000L
        data class Candidate(val member: HouseholdMember, val total: Long, val active: Long,
            val repeats: Int, val availabilityRank: Int, val lastDueAt: Long)
        return scopedMembers.filterNot { it.userId in unavailableUserIds }.mapNotNull { member ->
            val rank = availabilityRank(member, start, dueAt, availability, overrides) ?: return@mapNotNull null
            val previous = history.filter { it.assignedUserId == member.userId }
            Candidate(member,
                previous.sumOf { it.workloadValue.toLong() },
                previous.filter { it.status == TaskStatus.ASSIGNED || it.status == TaskStatus.AWAITING_VERIFICATION }
                    .sumOf { it.workloadValue.toLong() },
                previous.count { it.choreId == chore.id }, rank,
                previous.maxOfOrNull { it.dueAt } ?: Long.MIN_VALUE)
        }.minWithOrNull(compareBy<Candidate>({ it.total }, { it.active }, { it.repeats },
            { it.availabilityRank }, { it.lastDueAt }, { it.member.userId }))?.member
    }

    // Unknown schedules remain eligible. Explicit free time only breaks workload/repetition ties.
    // A conflicting busy entry wins; absolute overrides take precedence over weekly entries.
    private fun availabilityRank(member: HouseholdMember, start: Long, end: Long,
        weekly: List<Availability>, overrides: List<AvailabilityOverride>): Int? {
        val rows = weekly.filter { it.householdId == member.householdId && it.userId == member.userId }
        val temporary = overrides.filter { it.householdId == member.householdId && it.userId == member.userId
            && it.startsAt < end && it.endsAt > start }
        val calendars = rows.associateWith {
            require(it.timeZoneId in timeZones) { "Unknown availability time zone" }
            Calendar.getInstance(TimeZone.getTimeZone(it.timeZoneId))
        }
        val instants = sortedSetOf(start)
        var minute = Math.floorDiv(start, 60_000L) * 60_000L + 60_000L
        while (minute < end) { instants.add(minute); minute += 60_000L }
        temporary.forEach {
            if (it.startsAt in start until end) instants.add(it.startsAt)
            if (it.endsAt in start until end) instants.add(it.endsAt)
        }
        var allExplicitlyAvailable = true
        for (instant in instants) {
            val activeOverrides = temporary.filter { instant >= it.startsAt && instant < it.endsAt }
            val kinds = if (activeOverrides.isNotEmpty()) activeOverrides.map { it.kind } else rows.filter {
                val calendar = calendars.getValue(it).apply { timeInMillis = instant }
                val day = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
                val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                it.weekday.ordinal == day && minuteOfDay in it.startMinute until it.endMinute
            }.map { it.kind }
            if (kinds.any { it != AvailabilityKind.AVAILABLE && it != AvailabilityKind.DAY_OFF }) return null
            if (kinds.isEmpty()) allExplicitlyAvailable = false
        }
        return if (allExplicitlyAvailable) 0 else 1
    }
    private val timeZones by lazy { TimeZone.getAvailableIDs().toSet() }
}
