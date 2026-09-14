package com.rommie.app.data.repository

import java.security.SecureRandom
import org.junit.Assert.*
import org.junit.Test

class HouseholdValidationTest {
    @Test fun validatesNameBoundaries() {
        assertEquals("The Apartment", HouseholdValidation.name(" The Apartment "))
        assertEquals(100, HouseholdValidation.name("a".repeat(100)).length)
        listOf("", "   ", "a".repeat(101), "a\nb").forEach {
            assertEquals(HouseholdError.INVALID_NAME,
                assertThrows(HouseholdException::class.java) { HouseholdValidation.name(it) }.reason)
        }
    }
    @Test fun codesNormalizeWithoutAcceptingInternalSpacesOrPunctuation() {
        assertEquals("TXST24", HouseholdValidation.code(" txst24 "))
        listOf("", "ABC", "ABCDEFG", "AB CD2", "ABC!23").forEach {
            assertEquals(HouseholdError.INVALID_CODE,
                assertThrows(HouseholdException::class.java) { HouseholdValidation.code(it) }.reason)
        }
    }
    @Test fun generatorUsesSixUnambiguousRandomSelections() {
        val random = object : SecureRandom() {
            var calls = 0
            override fun nextInt(bound: Int): Int = (calls++).also {
                assertEquals(HouseholdValidation.ALPHABET.length, bound)
            }
        }
        assertEquals("ABCDEF", HouseholdValidation.generate(random))
        assertEquals(6, random.calls)
        repeat(100) {
            val code = HouseholdValidation.generate()
            assertEquals(6, code.length)
            assertTrue(code.all { it in HouseholdValidation.ALPHABET })
        }
    }
    @Test fun anyExistingMembershipPreventsASecondJoin() {
        HouseholdValidation.requireNoMembership(null)
        assertEquals(HouseholdError.ALREADY_JOINED,
            assertThrows(HouseholdException::class.java) { HouseholdValidation.requireNoMembership("house") }.reason)
    }
}
