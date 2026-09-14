package com.rommie.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.rommie.app.data.firebase.FirebaseClients
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.data.repository.*
import com.rommie.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiveFirebaseReviewTest {
    @Test fun privateReviewLifecycle() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveFirebase") == "true")
        val clients=FirebaseClients.create(InstrumentationRegistry.getInstrumentation().targetContext)
        val db=clients.firestore
        check(db.app.options.projectId=="backendtest-21628")
        val users=FirebaseUserRepository(clients.auth,db)
        val auth=FirebaseAuthRepository(clients.auth,users)
        val households=FirebaseHouseholdRepository(clients.auth,db,users)
        val chores=FirebaseChoreRepository(clients.auth,db)
        val assignments=FirebaseAssignmentRepository(clients.auth,db)
        val completions=FirebaseCompletionRepository(clients.auth,db)
        val reviews=FirebaseReviewRepository(clients.auth,db)
        val run=UUID.randomUUID().toString().replace("-","").take(12)
        val password="Roomie!${UUID.randomUUID()}a7"
        fun email(suffix:String)="roomie-review-$run-$suffix@example.com"
        fun mark(message:String)=Log.i("RoomieReviewTest",message)
        try {
            withTimeout(240000) {
                auth.signOut()
                val a=auth.signUp("Review Creator $run",email("a"),password)
                val h=households.createHousehold("Review Test $run")
                val chore=chores.createChore(h.id,ChoreDetails("Bathroom",30,Difficulty.HARD,20))
                auth.signOut()
                val b=auth.signUp("Review Worker $run",email("b"),password)
                households.joinHouseholdByCode(h.inviteCode)
                val task=assignments.createAssignment(h.id,"review-task",chore.id,b.id,2000000000000)
                val proof=ProofSubmission("fixture",ProofValidation.path(h.id,task.id,b.id,"fixture"),ProofType.PHOTO)
                completions.submitCompletion(h.id,task.id,proof)
                assertEquals(TaskStatus.AWAITING_VERIFICATION,assignments.getAssignment(h.id,task.id)!!.status)
                mark("PASS worker completion; proof metadata fixture only, no live Storage upload")
                auth.signOut()
                val c=auth.signUp("Review Reviewer $run",email("c"),password)
                households.joinHouseholdByCode(h.inviteCode)
                assertFalse(reviews.hasReviewed(h.id,task.id))
                reviews.submitReview(h.id,task.id,10)
                assertTrue(reviews.hasReviewed(h.id,task.id))
                try { reviews.submitReview(h.id,task.id,10);fail("Duplicate accepted") } catch (_:DuplicateReviewException) {}
                val reviewRef=db.document(FirestorePaths.review(h.id,task.id,c.id))
                val raw=reviewRef.get(Source.SERVER).await()
                assertEquals(10L,raw.getLong("rating"));assertNotNull(raw.getTimestamp("submittedAt"))
                assertEquals(setOf("householdId","completionId","reviewerId","rating","submittedAt"),raw.data!!.keys)
                for(change in listOf(mapOf("rating" to 1),mapOf("reviewerId" to a.id),mapOf("submittedAt" to FieldValue.serverTimestamp())))
                    denied("immutable review fields") { reviewRef.update(change).await() }
                denied("delete review") { reviewRef.delete().await() }
                denied("reviewer list") { reviewRef.parent.get(Source.SERVER).await() }
                mark("PASS review 10, own status, duplicate rejection and immutable fields")
                auth.signOut();auth.signIn(email("a"),password)
                denied("creator reads another rating before review") { reviewRef.get(Source.SERVER).await() }
                reviews.submitReview(h.id,task.id,1)
                denied("creator reads another rating after review") { reviewRef.get(Source.SERVER).await() }
                mark("PASS second independent review 1; no peer rating visibility")
                auth.signOut();auth.signIn(email("b"),password)
                try { reviews.submitReview(h.id,task.id,5);fail("Self-review accepted") } catch (_:IllegalArgumentException) {}
                denied("direct self-review") {
                    db.document(FirestorePaths.review(h.id,task.id,b.id)).set(mapOf("householdId" to h.id,"completionId" to task.id,
                        "reviewerId" to b.id,"rating" to 5,"submittedAt" to FieldValue.serverTimestamp())).await()
                }
                denied("worker reads rating") { reviewRef.get(Source.SERVER).await() }
                denied("worker lists ratings") { reviewRef.parent.get(Source.SERVER).await() }
                val completion=completions.getCompletion(h.id,task.id)!!
                assertNull(completion.result)
                assertEquals(0,households.getHouseholdMembers(h.id).first { it.member.userId==b.id }.member.contributionPoints)
                auth.signOut();auth.signUp("Review Outsider $run",email("d"),password)
                denied("outsider submits review") { reviews.submitReview(h.id,task.id,5) }
                denied("outsider reads review") { reviewRef.get(Source.SERVER).await() }
                auth.signOut()
                denied("signed-out reads review") { reviewRef.get(Source.SERVER).await() }
                auth.signIn(email("c"),password)
                assertTrue(reviews.hasReviewed(h.id,task.id))
                assertEquals(10L,reviewRef.get(Source.SERVER).await().getLong("rating"))
                mark("ALL STAGE 3 LIVE CHECKS PASSED run=$run household=${h.id}")
            }
        } finally { auth.signOut() }
    }
    private suspend fun denied(label:String, action:suspend()->Unit) {
        try { withTimeout(30000) { action() };fail("Unexpectedly allowed: $label") }
        catch(failure:FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,failure.code)
            Log.i("RoomieReviewTest","PASS denied: $label")
        }
    }
}
