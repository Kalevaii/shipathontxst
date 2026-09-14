package com.rommie.app.data.firebase

import com.rommie.app.data.repository.ChoreDetails
import com.rommie.app.model.Chore

/** Internal persistence seam for JVM tests; Firestore rules remain authoritative for membership. */
internal interface ChoreStore {
    suspend fun create(uid: String, householdId: String, details: ChoreDetails): Chore
    suspend fun get(householdId: String, choreId: String): Chore?
    suspend fun list(householdId: String): List<Chore>
    suspend fun update(uid: String, householdId: String, choreId: String, details: ChoreDetails): Chore
}
