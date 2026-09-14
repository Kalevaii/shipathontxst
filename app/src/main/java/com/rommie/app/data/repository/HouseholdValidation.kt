package com.rommie.app.data.repository

import java.security.SecureRandom
import java.util.Locale

internal object HouseholdValidation {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun name(value: String): String = value.trim().also {
        if (it.length !in 1..100 || it.any { c -> c.isISOControl() })
            throw HouseholdException(HouseholdError.INVALID_NAME)
    }
    // Accept the full documented alphabet for compatibility with existing codes such as TXST24.
    fun code(value: String): String = value.trim().uppercase(Locale.ROOT).also {
        if (!it.matches(Regex("[A-Z0-9]{6}"))) throw HouseholdException(HouseholdError.INVALID_CODE)
    }
    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString { repeat(6) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    fun requireNoMembership(householdId: String?) {
        if (householdId != null) throw HouseholdException(HouseholdError.ALREADY_JOINED)
    }
}
