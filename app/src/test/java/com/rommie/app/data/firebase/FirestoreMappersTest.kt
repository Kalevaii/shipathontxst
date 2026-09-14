package com.rommie.app.data.firebase

import com.rommie.app.data.mock.RoomieSampleData
import com.rommie.app.model.Recurrence
import com.rommie.app.model.Weekday
import org.junit.Assert.*
import org.junit.Test

class FirestoreMappersTest {
    @Test fun userPayloadContainsOnlyPrivateProfileFieldsAndUsesPathUid() {
        val user = RoomieSampleData.household.users.first()
        val fields = FirestoreMappers.userToDocument(user)
        assertEquals(setOf("name", "email"), fields.keys)
        assertEquals("path-uid", FirestoreMappers.userFromDocument("path-uid", fields + ("id" to "spoofed")).id)
        listOf(fields - "name", fields - "email", fields + ("name" to 42)).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FirestoreMappers.userFromDocument(user.id, it)
            }
        }
    }

    @Test fun userRoundTripUsesDocumentIdentity() {
        val user = RoomieSampleData.household.users.first()
        val fields = FirestoreMappers.userToDocument(user)
        assertFalse(fields.containsKey("id"))
        assertEquals(user, FirestoreMappers.userFromDocument(user.id, fields))
    }

    @Test fun choreRoundTripCoversAllRecurrences() {
        val sample = RoomieSampleData.household.chores.first()
        listOf(null, Recurrence.EveryDays(3), Recurrence.Weekly(setOf(Weekday.MONDAY, Weekday.FRIDAY)))
            .forEach { recurrence ->
                val chore = sample.copy(recurrence = recurrence)
                assertEquals(chore, FirestoreMappers.choreFromDocument(chore.id, chore.householdId,
                    FirestoreMappers.choreToDocument(chore)))
            }
    }

    @Test fun malformedChoresFailInsteadOfInventingDefaults() {
        val chore = RoomieSampleData.household.chores.first()
        val fields = FirestoreMappers.choreToDocument(chore)
        val invalid = listOf(
            fields - "title",
            fields + ("householdId" to "other-house"),
            fields + ("maxPoints" to Long.MAX_VALUE),
            fields + ("estimatedMinutes" to 2.5),
            fields + ("difficulty" to "IMPOSSIBLE"),
            fields + ("recurrence" to mapOf("type" to "UNKNOWN")),
            fields + ("recurrence" to mapOf("type" to "EVERY_DAYS", "interval" to 0L)),
            fields - "recurrence",
            fields + ("recurrence" to mapOf("type" to "WEEKLY", "weekdays" to listOf("MONDAY", "MONDAY"))),
            fields + ("recurrence" to mapOf("type" to "WEEKLY", "weekdays" to listOf("UNKNOWN"))),
            fields + ("recurrence" to mapOf("type" to "WEEKLY", "weekdays" to emptyList<String>())),
            fields + ("recurrence" to mapOf("type" to "EVERY_DAYS", "interval" to 1.5)),
            fields + ("recurrence" to mapOf("type" to "EVERY_DAYS", "interval" to 2L, "extra" to true)),
        )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FirestoreMappers.choreFromDocument(chore.id, chore.householdId, it)
            }
        }
    }

    @Test fun choreIdentityComesFromPathAndCreationTimestampRemainsWireMetadata() {
        val chore = RoomieSampleData.household.chores.first()
        val fields = FirestoreMappers.choreToDocument(chore)
        assertFalse(fields.containsKey("id"))
        assertFalse(fields.containsKey("workloadValue"))
        assertEquals(chore.copy(id = "path-id"), FirestoreMappers.choreFromDocument("path-id", chore.householdId,
            fields + mapOf("id" to "spoofed", "createdAt" to 123L)))
    }

    @Test fun privateReviewAndProofPathsAreHouseholdScoped() {
        assertEquals("households/h/completions/c/reviews/u", FirestorePaths.review("h", "c", "u"))
        assertEquals("households/h/completions/c/proof/u/p", FirestorePaths.proof("h", "c", "u", "p"))
        listOf("", " ", "../h", "a/b", ".", "..").forEach {
            assertThrows(IllegalArgumentException::class.java) { FirestorePaths.household(it) }
        }
        assertEquals("householdInvites/TXST24", FirestorePaths.invite("TXST24"))
        assertThrows(IllegalArgumentException::class.java) { FirestorePaths.invite("txst24") }
    }
}
