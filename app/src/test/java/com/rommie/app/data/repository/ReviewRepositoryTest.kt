package com.rommie.app.data.repository

import com.rommie.app.data.firebase.ReviewStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReviewRepositoryTest {
    private class Store : ReviewStore {
        var saved = false
        var after: () -> Unit = {}
        override suspend fun submit(uid: String, householdId: String, completionId: String, rating: Int) {
            if (saved) throw DuplicateReviewException()
            saved = true; after()
        }
        override suspend fun exists(uid: String, householdId: String, completionId: String): Boolean { after(); return saved }
    }
    @Test fun submitAndCheck() = runBlocking {
        val store = Store(); val repo = FirebaseReviewRepository({ "reviewer" }, store)
        assertFalse(repo.hasReviewed("home", "task")); repo.submitReview("home", "task", 10)
        assertTrue(repo.hasReviewed("home", "task"))
    }
    @Test fun duplicateIsNotSilentlyAccepted() = runBlocking {
        val repo = FirebaseReviewRepository({ "reviewer" }, Store())
        repo.submitReview("home", "task", 1)
        try { repo.submitReview("home", "task", 1); fail() } catch (_: DuplicateReviewException) { }
    }
    @Test fun requiresAuth() = runBlocking {
        val repo = FirebaseReviewRepository({ null }, Store())
        try { repo.hasReviewed("home", "task"); fail() } catch (_: NotAuthenticatedException) { }
        try { repo.submitReview("home", "task", 5); fail() } catch (_: NotAuthenticatedException) { }
    }
    @Test fun rejectsInvalidRatingBeforeWrite() = runBlocking {
        val store = Store(); val repo = FirebaseReviewRepository({ "reviewer" }, store)
        for (rating in listOf(0,11)) try { repo.submitReview("home", "task", rating); fail() } catch (_: IllegalArgumentException) { }
        assertFalse(store.saved)
    }
    @Test fun rejectsInvalidPathBeforeWrite() = runBlocking {
        val store = Store(); val repo = FirebaseReviewRepository({ "reviewer" }, store)
        try { repo.submitReview("home", "../task", 5); fail() } catch (_: IllegalArgumentException) { }
        assertFalse(store.saved)
    }
    @Test fun discardsResultsOnSessionSwitch() = runBlocking {
        var uid = "reviewer"; val store = Store(); val repo = FirebaseReviewRepository({ uid }, store)
        store.after = { uid = "other" }
        try { repo.hasReviewed("home", "task"); fail() } catch (_: SessionChangedException) { }
        uid = "reviewer"
        try { repo.submitReview("home", "task", 5); fail() } catch (_: SessionChangedException) { }
    }
}
