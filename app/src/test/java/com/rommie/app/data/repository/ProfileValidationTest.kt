package com.rommie.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class ProfileValidationTest {
    @Test fun nameIsTrimmedAndSupportsUnicode() {
        assertEquals("Álex 李", ProfileValidation.name("  Álex 李  "))
        assertEquals(100, ProfileValidation.name("a".repeat(100)).length)
    }
    @Test fun invalidNamesAreRejected() {
        listOf("", "  ", "a".repeat(101), "A\nB", "A\u0000B").forEach {
            assertThrows(IllegalArgumentException::class.java) { ProfileValidation.name(it) }
        }
    }
    @Test fun emailAndPasswordAreNotLowercasedOrSilentlyRewritten() {
        assertEquals("Alex@example.com", ProfileValidation.email(" Alex@example.com "))
        assertEquals(" password ", ProfileValidation.password(" password "))
        listOf("a", "@a.com", "a@", "a@@b.com", "a b@c.com").forEach {
            assertThrows(IllegalArgumentException::class.java) { ProfileValidation.email(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { ProfileValidation.password("") }
    }
}
