package com.rommie.app.data.repository

import com.rommie.app.model.*

// Backend implementations map storage DTOs into these Firebase-free models.
// Deliberately read-only until feature owners agree on mutation/error contracts.
interface RoomieRepository {
    suspend fun loadHousehold(householdId: String): HouseholdSnapshot?
}

data class HouseholdSnapshot(
    val household: Household,
    val users: List<User>,
    val members: List<HouseholdMember>,
    val chores: List<Chore>,
    val assignments: List<TaskAssignment>,
    val completions: List<TaskCompletion>,
    val availability: List<Availability>,
    val availabilityOverrides: List<AvailabilityOverride> = emptyList(),
)
