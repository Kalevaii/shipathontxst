package com.rommie.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Internal SDK boundary enables orchestration tests without Android or live credentials. */
internal interface AuthGateway {
    val currentUserId: String?
    val authState: Flow<String?>
    suspend fun signUp(email: String, password: String): String
    suspend fun signIn(email: String, password: String): String
    fun signOut()
}

internal class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {
    override val currentUserId: String? get() = auth.currentUser?.uid
    override val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.conflate().distinctUntilChanged()

    override suspend fun signUp(email: String, password: String): String =
        checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user).uid

    override suspend fun signIn(email: String, password: String): String =
        checkNotNull(auth.signInWithEmailAndPassword(email, password).await().user).uid

    override fun signOut() = auth.signOut()
}
