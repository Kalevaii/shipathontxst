package com.rommie.app.data.repository

interface LeaderboardRepository {
    /** Household-scoped public member projection, ranked by existing contribution points. */
    suspend fun getHouseholdLeaderboard(householdId: String): List<HouseholdMemberSummary>
}