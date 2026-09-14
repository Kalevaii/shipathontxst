package com.rommie.app.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.MemoryEagerGcSettings
import com.google.firebase.storage.FirebaseStorage

/** Create once in the future composition root and inject into data repositories, never screens. */
class FirebaseClients private constructor(
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    private val app: FirebaseApp,
) {
    // Auth/profile setup must work before the team provisions media storage.
    val storage: FirebaseStorage by lazy {
        check(!app.options.storageBucket.isNullOrBlank()) {
            "Firebase Storage bucket is missing. Provision Storage and download updated configuration."
        }
        FirebaseStorage.getInstance(app)
    }

    companion object {
        /** Missing configuration is an explicit failure, never a silent switch to mock data. */
        fun create(context: Context): FirebaseClients {
            val app = checkNotNull(FirebaseApp.initializeApp(context.applicationContext)) {
                "Firebase is not configured. Add app/google-services.json and build with " +
                    "-Proomie.firebase.enabled=true. See docs/FIREBASE.md."
            }
            val firestore = FirebaseFirestore.getInstance(app)
            // This online MVP uses server reads/transactions. Eager GC drops inactive listen
            // targets, avoiding stale one-shot reads after account switches (SDK 26.6.0).
            // Configure before first use; durable offline caching is deliberately deferred.
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder(firestore.firestoreSettings)
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder()
                    .setGcSettings(MemoryEagerGcSettings.newBuilder().build()).build())
                .build()
            return FirebaseClients(
                FirebaseAuth.getInstance(app),
                firestore,
                app,
            )
        }
    }
}
