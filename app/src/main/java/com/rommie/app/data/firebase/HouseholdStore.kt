package com.rommie.app.data.firebase

import com.rommie.app.data.repository.HouseholdMemberSummary
import com.rommie.app.model.Household
import com.rommie.app.model.User

/** Internal test seam. Implementations must check existing membership inside each write transaction. */
internal interface HouseholdStore {
    suspend fun create(user: User, name: String, code: String): Household
    suspend fun join(user: User, code: String): Household
    suspend fun currentHouseholdId(uid: String): String?
    suspend fun get(id: String): Household?
    suspend fun members(id: String): List<HouseholdMemberSummary>
}
