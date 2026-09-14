package com.rommie.app.data.firebase

internal interface ReviewStore {
    suspend fun submit(uid: String, householdId: String, completionId: String, rating: Int)
    suspend fun exists(uid: String, householdId: String, completionId: String): Boolean
}
