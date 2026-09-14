package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rommie.app.data.firebase.FirebaseHouseholdStore
import com.rommie.app.data.firebase.HouseholdStore
import com.rommie.app.model.Household
import com.rommie.app.model.User
import kotlinx.coroutines.CancellationException

class FirebaseHouseholdRepository internal constructor(
    private val currentUid: () -> String?,
    private val users: UserRepository,
    private val store: HouseholdStore,
    private val nextCode: () -> String = { HouseholdValidation.generate() },
) : HouseholdRepository {
    constructor(auth: FirebaseAuth, firestore: FirebaseFirestore, users: UserRepository) :
        this({ auth.currentUser?.uid }, users, FirebaseHouseholdStore(auth, firestore))

    override suspend fun createHousehold(name: String): Household = operation(true) { uid ->
        val validName = HouseholdValidation.name(name)
        val user = profile(uid)
        repeat(5) {
            try { return@operation store.create(user, validName, HouseholdValidation.code(nextCode())) }
            catch (failure: HouseholdException) {
                if (failure.reason != HouseholdError.CODE_COLLISION) throw failure
            }
        }
        throw HouseholdException(HouseholdError.CODE_COLLISION)
    }

    override suspend fun joinHouseholdByCode(code: String): Household = operation(true) { uid ->
        val normalized = HouseholdValidation.code(code)
        store.join(profile(uid), normalized)
    }
    override suspend fun getHousehold(householdId: String): Household? = operation(false) { store.get(householdId) }
    override suspend fun getCurrentUserHousehold(): Household? = operation(false) { uid ->
        store.currentHouseholdId(uid)?.let { store.get(it) ?: throw HouseholdException(HouseholdError.NOT_FOUND) }
    }
    override suspend fun getHouseholdMembers(householdId: String): List<HouseholdMemberSummary> =
        operation(false) { store.members(householdId) }

    private suspend fun profile(uid: String): User {
        val user = users.fetchCurrentUserProfile() ?: throw HouseholdException(HouseholdError.PROFILE_REQUIRED)
        if (currentUid() != uid || user.id != uid) throw SessionChangedException()
        return user
    }
    private suspend fun <T> operation(write: Boolean, block: suspend (String) -> T): T {
        val uid = currentUid() ?: throw NotAuthenticatedException()
        try {
            return block(uid).also { if (currentUid() != uid) throw SessionChangedException() }
        } catch (failure: CancellationException) { throw failure }
        catch (failure: SessionChangedException) { throw failure }
        catch (failure: HouseholdException) { throw failure }
        catch (failure: Exception) {
            throw HouseholdException(if (write) HouseholdError.WRITE_FAILED else HouseholdError.READ_FAILED, failure)
        }
    }
}
