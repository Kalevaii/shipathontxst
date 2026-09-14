package com.rommie.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rommie.app.data.repository.ChoreDetails
import com.rommie.app.data.repository.ChoreNotFoundException
import com.rommie.app.data.repository.SessionChangedException
import com.rommie.app.model.Chore
import kotlinx.coroutines.tasks.await

internal class FirebaseChoreStore(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : ChoreStore {
    override suspend fun create(uid: String, householdId: String, details: ChoreDetails): Chore {
        val ref = db.collection(FirestorePaths.chores(householdId)).document()
        val chore = Chore(ref.id, householdId, details.title, details.description, details.estimatedMinutes,
            details.difficulty, details.maxPoints, uid, details.recurrence)
        return transaction { db.runTransaction { tx ->
            requireSession(uid)
            check(!tx.get(ref).exists()) { "Chore ID already exists" }
            tx.set(ref, FirestoreMappers.choreToDocument(chore) + ("createdAt" to FieldValue.serverTimestamp()))
            chore
        }.await() }
    }
    override suspend fun get(householdId: String, choreId: String): Chore? {
        val doc = db.document(FirestorePaths.chore(householdId, choreId)).get(Source.SERVER).await()
        return if (doc.exists()) FirestoreMappers.choreFromDocument(doc.id, householdId, checkNotNull(doc.data)) else null
    }
    override suspend fun list(householdId: String): List<Chore> =
        db.collection(FirestorePaths.chores(householdId)).get(Source.SERVER).await().documents.map {
            FirestoreMappers.choreFromDocument(it.id, householdId, checkNotNull(it.data))
        }.sortedBy { it.id }

    override suspend fun update(uid: String, householdId: String, choreId: String, details: ChoreDetails): Chore {
        val ref = db.document(FirestorePaths.chore(householdId, choreId))
        return transaction { db.runTransaction { tx ->
            requireSession(uid)
            val doc = tx.get(ref)
            if (!doc.exists()) throw ChoreNotFoundException()
            val old = FirestoreMappers.choreFromDocument(doc.id, householdId, checkNotNull(doc.data))
            val updated = old.copy(title = details.title, description = details.description,
                estimatedMinutes = details.estimatedMinutes, difficulty = details.difficulty,
                maxPoints = details.maxPoints, recurrence = details.recurrence)
            tx.update(ref, FirestoreMappers.choreToDocument(updated).filterKeys { it != "householdId" && it != "createdBy" })
            updated
        }.await() }
    }
    private fun requireSession(uid: String) {
        if (auth.currentUser?.uid != uid) throw SessionChangedException()
    }
    private suspend fun <T> transaction(block: suspend () -> T): T {
        try { return block() } catch (failure: Exception) {
            // Preserve domain exceptions wrapped by the SDK's transaction callback machinery.
            var cause: Throwable? = failure
            while (cause != null) {
                if (cause is ChoreNotFoundException || cause is SessionChangedException) throw cause
                cause = cause.cause
            }
            throw failure
        }
    }
}
