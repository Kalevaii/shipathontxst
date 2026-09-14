package com.rommie.app.model

data class Household(val id: String, val name: String, val inviteCode: String)

// Membership owns household-scoped points; a user identity is not a points ledger.
data class HouseholdMember(
    val householdId: String,
    val userId: String,
    val contributionPoints: Int = 0,
)
