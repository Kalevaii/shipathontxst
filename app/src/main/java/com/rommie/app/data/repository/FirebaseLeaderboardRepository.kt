package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.data.firebase.HouseholdMappers
import com.rommie.app.domain.LeaderboardRanking
import kotlinx.coroutines.tasks.await

class FirebaseLeaderboardRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : LeaderboardRepository {
    override suspend fun getHouseholdLeaderboard(householdId: String): List<HouseholdMemberSummary> {
        val uid = auth.currentUser?.uid ?: throw NotAuthenticatedException()
        val members = db.collection(FirestorePaths.members(householdId)).get(Source.SERVER).await()
            .documents.map { HouseholdMappers.member(householdId, it.id, checkNotNull(it.data)) }
        if (auth.currentUser?.uid != uid) throw SessionChangedException()
        return LeaderboardRanking.rank(householdId, members)
    }
}