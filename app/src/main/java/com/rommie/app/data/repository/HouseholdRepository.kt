package com.rommie.app.data.repository

import com.rommie.app.model.Household
import com.rommie.app.model.HouseholdMember

interface HouseholdRepository {
    suspend fun createHousehold(name: String): Household
    suspend fun joinHouseholdByCode(code: String): Household
    suspend fun getHousehold(householdId: String): Household?
    suspend fun getCurrentUserHousehold(): Household?
    suspend fun getHouseholdMembers(householdId: String): List<HouseholdMemberSummary>
}

/** Public roster projection; reuses membership and never includes private account email. */
data class HouseholdMemberSummary(val member: HouseholdMember, val displayName: String)

enum class HouseholdError {
    INVALID_NAME, INVALID_CODE, NOT_FOUND, ALREADY_JOINED, PROFILE_REQUIRED,
    CODE_COLLISION, WRITE_FAILED, READ_FAILED,
}
class HouseholdException(val reason: HouseholdError, cause: Throwable? = null) :
    Exception(when (reason) {
        HouseholdError.INVALID_NAME -> "Enter a household name of 1–100 characters"
        HouseholdError.INVALID_CODE -> "Enter a six-character household code"
        HouseholdError.NOT_FOUND -> "Household not found"
        HouseholdError.ALREADY_JOINED -> "You already belong to a household"
        HouseholdError.PROFILE_REQUIRED -> "Complete your user profile first"
        HouseholdError.CODE_COLLISION -> "Could not reserve a unique invite code; retry"
        HouseholdError.WRITE_FAILED -> "Household operation failed; check your current household before retrying"
        HouseholdError.READ_FAILED -> "Could not load household data"
    }, cause)
