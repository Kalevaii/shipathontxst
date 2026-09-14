package com.rommie.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.repository.*
import com.rommie.app.model.Household
import com.rommie.app.model.User
import kotlinx.coroutines.tasks.await

internal class FirebaseHouseholdStore(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : HouseholdStore {
    override suspend fun create(user: User, name: String, code: String): Household {
        val household = db.collection("households").document() // Firestore random ID, not a sequence.
        val lookup = db.document(FirestorePaths.userHousehold(user.id))
        val invite = db.document(FirestorePaths.invite(code))
        return transaction {
            db.runTransaction { tx ->
                requireSession(user.id)
                val current = tx.get(lookup)
                HouseholdValidation.requireNoMembership(current.data?.let { HouseholdMappers.string(it, "householdId") })
                if (tx.get(invite).exists()) throw HouseholdException(HouseholdError.CODE_COLLISION)
                tx.set(household, mapOf("name" to name, "inviteCode" to code, "createdBy" to user.id,
                    "createdAt" to FieldValue.serverTimestamp()))
                tx.set(invite, mapOf("householdId" to household.id))
                tx.set(db.document(FirestorePaths.member(household.id, user.id)),
                    HouseholdMappers.memberFields(user) + ("joinedAt" to FieldValue.serverTimestamp()))
                tx.set(lookup, mapOf("householdId" to household.id, "inviteCode" to code))
                Household(household.id, name, code)
            }.await()
        }
    }

    override suspend fun join(user: User, code: String): Household {
        val lookup = db.document(FirestorePaths.userHousehold(user.id))
        val id = transaction {
            db.runTransaction { tx ->
                requireSession(user.id)
                val current = tx.get(lookup)
                HouseholdValidation.requireNoMembership(current.data?.let { HouseholdMappers.string(it, "householdId") })
                val invite = tx.get(db.document(FirestorePaths.invite(code)))
                if (!invite.exists()) throw HouseholdException(HouseholdError.NOT_FOUND)
                val householdId = HouseholdMappers.string(checkNotNull(invite.data), "householdId")
                // Do not read the household before membership: nonmembers have no read permission.
                tx.set(db.document(FirestorePaths.member(householdId, user.id)),
                    HouseholdMappers.memberFields(user) + ("joinedAt" to FieldValue.serverTimestamp()))
                tx.set(lookup, mapOf("householdId" to householdId, "inviteCode" to code))
                householdId
            }.await()
        }
        requireSession(user.id)
        // If this read fails, membership may already be committed: recover using the private lookup.
        return get(id) ?: throw HouseholdException(HouseholdError.NOT_FOUND)
    }
    override suspend fun currentHouseholdId(uid: String): String? {
        requireSession(uid)
        val doc = db.document(FirestorePaths.userHousehold(uid)).get(Source.SERVER).await()
        return if (doc.exists()) HouseholdMappers.string(checkNotNull(doc.data), "householdId") else null
    }
    override suspend fun get(id: String): Household? {
        val doc = db.document(FirestorePaths.household(id)).get(Source.SERVER).await()
        return if (doc.exists()) HouseholdMappers.household(doc.id, checkNotNull(doc.data)) else null
    }
    override suspend fun members(id: String): List<HouseholdMemberSummary> =
        db.collection(FirestorePaths.members(id)).get(Source.SERVER).await().documents.map {
            HouseholdMappers.member(id, it.id, checkNotNull(it.data))
        }.sortedBy { it.member.userId }

    private fun requireSession(uid: String) {
        if (auth.currentUser?.uid != uid) throw SessionChangedException()
    }
    private suspend fun <T> transaction(block: suspend () -> T): T {
        try { return block() } catch (failure: Exception) {
            // Firestore may wrap transaction callback exceptions; preserve domain-friendly reasons.
            var cause: Throwable? = failure
            while (cause != null) {
                if (cause is HouseholdException || cause is SessionChangedException) throw cause
                cause = cause.cause
            }
            throw failure
        }
    }
}
