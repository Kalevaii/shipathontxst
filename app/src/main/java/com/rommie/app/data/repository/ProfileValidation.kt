package com.rommie.app.data.repository

/** Local input checks; Firebase remains authoritative for email/password acceptance and policy. */
internal object ProfileValidation {
    fun name(value: String): String = value.trim().also {
        require(it.length in 1..100 && it.none { c -> c.isISOControl() }) {
            "Name must contain 1–100 characters without control characters"
        }
    }

    fun email(value: String): String = value.trim().also {
        require(it.length in 3..254 && it.count { c -> c == '@' } == 1 &&
            !it.startsWith('@') && !it.endsWith('@') && it.none { c -> c.isWhitespace() || c.isISOControl() }) {
            "Enter an email address"
        }
    }

    fun password(value: String): String = value.also {
        // Never trim or log passwords; project-specific password policy is enforced by Firebase.
        require(it.isNotEmpty()) { "Enter a password" }
    }
}
