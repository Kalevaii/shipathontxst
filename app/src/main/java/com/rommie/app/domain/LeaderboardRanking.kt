package com.rommie.app.domain

import com.rommie.app.data.repository.HouseholdMemberSummary

object LeaderboardRanking {
    fun rank(householdId: String, members: List<HouseholdMemberSummary>): List<HouseholdMemberSummary> {
        require(householdId.isNotBlank())
        return members.filter { it.member.householdId == householdId }
            .sortedWith(compareByDescending<HouseholdMemberSummary> { it.member.contributionPoints }
            .thenBy { it.member.userId })
    }
}