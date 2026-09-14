package com.rommie.app.data.firebase

import com.rommie.app.model.User
import org.junit.Assert.*
import org.junit.Test

class HouseholdMappersTest {
    @Test fun membershipContainsNoPrivateEmailAndStartsWithZeroPoints() {
        val fields = HouseholdMappers.memberFields(User("u", "Alex", "private@example.com"))
        assertEquals(setOf("userId", "displayName", "contributionPoints"), fields.keys)
        val summary = HouseholdMappers.member("h", "u", fields)
        assertEquals("Alex", summary.displayName)
        assertEquals(0, summary.member.contributionPoints)
        assertEquals("h", summary.member.householdId)
    }
    @Test fun rejectsWrongUidAndCorruptPoints() {
        val fields = HouseholdMappers.memberFields(User("u", "Alex", "private@example.com"))
        listOf(fields + ("userId" to "other"), fields + ("contributionPoints" to -1L),
            fields + ("contributionPoints" to Long.MAX_VALUE), fields + ("contributionPoints" to 0.5),
            fields - "displayName").forEach {
            assertThrows(IllegalArgumentException::class.java) { HouseholdMappers.member("h", "u", it) }
        }
    }
    @Test fun householdUsesDocumentIdAndLookupPathIsPrivate() {
        val result = HouseholdMappers.household("path-id", mapOf("name" to "House", "inviteCode" to "TXST24"))
        assertEquals("path-id", result.id)
        assertEquals("TXST24", result.inviteCode)
        assertEquals("userHouseholds/u", FirestorePaths.userHousehold("u"))
    }
}
