package com.rommie.app.domain

import com.rommie.app.data.repository.HouseholdMemberSummary
import com.rommie.app.model.HouseholdMember
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardRankingTest {
    private fun member(id: String, points: Int, name: String = id, householdId: String = "h") =
        HouseholdMemberSummary(HouseholdMember(householdId, id, points), name)

    @Test fun ranksPointsDescendingWithUserIdTieBreak() {
        val result = LeaderboardRanking.rank("h", listOf(member("z", 4), member("a", 10), member("b", 10)))
        assertEquals(listOf("a", "b", "z"), result.map { it.member.userId })
    }

    @Test fun zeroScoresAreRetained() {
        val result = LeaderboardRanking.rank("h", listOf(member("a", 0), member("b", 0)))
        assertEquals(listOf("a", "b"), result.map { it.member.userId })
    }

    @Test fun emptyHouseholdReturnsEmpty() {
        assertEquals(emptyList<HouseholdMemberSummary>(), LeaderboardRanking.rank("h", emptyList()))
    }

    @Test fun oneMemberIsReturnedUnchanged() {
        val result = LeaderboardRanking.rank("h", listOf(member("a", 2, "Alice")))
        assertEquals("Alice", result.single().displayName)
        assertEquals("h", result.single().member.householdId)
    }

    @Test fun orderingIsIndependentOfInputOrder() {
        val members = listOf(member("c", 4), member("a", 7), member("b", 7))
        val expected = listOf("a", "b", "c")
        assertEquals(expected, LeaderboardRanking.rank("h", members).map { it.member.userId })
        assertEquals(expected, LeaderboardRanking.rank("h", members.reversed()).map { it.member.userId })
    }

    @Test fun foreignHouseholdMembersAreExcluded() {
        val result = LeaderboardRanking.rank("h", listOf(
            member("local", 1), member("foreign", 100, householdId = "other"),
        ))
        assertEquals(listOf("local"), result.map { it.member.userId })
    }
}