package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rommie.app.data.firebase.ChoreStore
import com.rommie.app.data.firebase.FirebaseChoreStore
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.model.Chore

/** Inject clients from FirebaseClients; never construct Firebase in a Compose screen. */
class FirebaseChoreRepository internal constructor(
    private val currentUid: () -> String?,
    private val store: ChoreStore,
) : ChoreRepository {
    constructor(auth: FirebaseAuth, firestore: FirebaseFirestore) :
        this({ auth.currentUser?.uid }, FirebaseChoreStore(auth, firestore))

    override suspend fun createChore(householdId: String, details: ChoreDetails): Chore = session { uid ->
        FirestorePaths.chores(householdId)
        store.create(uid, householdId, ChoreValidation.details(details))
    }
    override suspend fun getChore(householdId: String, choreId: String): Chore? = session {
        FirestorePaths.chore(householdId, choreId)
        store.get(householdId, choreId)
    }
    override suspend fun getHouseholdChores(householdId: String): List<Chore> = session {
        FirestorePaths.chores(householdId)
        store.list(householdId)
    }
    override suspend fun updateChore(householdId: String, choreId: String, details: ChoreDetails): Chore = session { uid ->
        FirestorePaths.chore(householdId, choreId)
        store.update(uid, householdId, choreId, ChoreValidation.details(details))
    }

    private suspend fun <T> session(block: suspend (String) -> T): T {
        val uid = currentUid() ?: throw NotAuthenticatedException()
        // Preserve cancellation and Firebase exceptions, including PERMISSION_DENIED.
        return block(uid).also { if (currentUid() != uid) throw SessionChangedException() }
    }
}
