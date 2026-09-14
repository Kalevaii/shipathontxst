package com.rommie.app

import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.rommie.app.data.firebase.CompletionMappers
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

/** Live Firestore is opt-in. Storage must be explicitly enabled once project access is provisioned. */
@RunWith(AndroidJUnit4::class)
class LiveFirebaseCompletionTest {
    @Test fun completionMetadataLifecycle() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveFirebase") == "true")
        val clients = FirebaseClients.create(InstrumentationRegistry.getInstrumentation().targetContext)
        check(clients.firestore.app.options.projectId == "backendtest-21628")
        val db = clients.firestore
        val users = FirebaseUserRepository(clients.auth, db)
        val auth = FirebaseAuthRepository(clients.auth, users)
        val households = FirebaseHouseholdRepository(clients.auth, db, users)
        val chores = FirebaseChoreRepository(clients.auth, db)
        val assignments = FirebaseAssignmentRepository(clients.auth, db)
        val completions = FirebaseCompletionRepository(clients.auth, db)
        val run = UUID.randomUUID().toString().replace("-", "").take(12)
        val password = "Roomie!${UUID.randomUUID()}a7"
        val emailA = "roomie-completion-$run-a@example.com"
        val emailB = "roomie-completion-$run-b@example.com"
        fun mark(text: String) = Log.i("RoomieCompletionTest", text)
        try {
            withTimeout(240_000) {
                auth.signOut()
                val a = auth.signUp("Completion Worker $run", emailA, password)
                val h = households.createHousehold("Completion test $run")
                val chore = chores.createChore(h.id, ChoreDetails("Bathroom", 30, Difficulty.HARD, 20))
                val task = assignments.createAssignment(h.id, "completion-task", chore.id, a.id, 2_000_000_000_000)
                val untouched = assignments.createAssignment(h.id, "atomic-task", chore.id, a.id, 2_000_000_000_001)
                var proof = ProofSubmission("proof-photo", ProofValidation.path(h.id, task.id, a.id, "proof-photo"), ProofType.PHOTO)
                val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXioAAAAASUVORK5CYII=", Base64.DEFAULT)
                val liveStorage = args.getString("liveStorage") == "true"
                if (liveStorage) {
                    proof = FirebaseProofRepository(clients.auth, clients.storage).uploadProof(h.id, task.id, proof.id, proof.type, "image/png", png)
                    mark("PASS real PNG upload")
                } else mark("NOT TESTED live Storage: explicit metadata-only run; proof path is a test fixture, not an uploaded asset")
                val ref = db.document(FirestorePaths.completion(h.id, task.id))
                val taskRef = db.document(FirestorePaths.assignment(h.id, task.id))
                val atomicRef = db.document(FirestorePaths.assignment(h.id, untouched.id))
                denied("status without completion") { atomicRef.update("status", "AWAITING_VERIFICATION").await() }
                denied("completion without status") {
                    ref.set(mapOf("householdId" to h.id, "assignmentId" to task.id, "completedBy" to a.id,
                        "proof" to listOf(CompletionMappers.proofDocument(proof)), "submittedAt" to FieldValue.serverTimestamp(), "result" to null)).await()
                }
                assertEquals(TaskStatus.ASSIGNED, assignments.getAssignment(h.id, untouched.id)!!.status)
                assertNull(completions.getCompletion(h.id, task.id))
                auth.signOut()
                val b = auth.signUp("Completion Peer $run", emailB, password)
                households.joinHouseholdByCode(h.inviteCode)
                denied("peer completes worker task directly") {
                    val batch = db.batch()
                    batch.set(ref, mapOf("householdId" to h.id, "assignmentId" to task.id, "completedBy" to b.id,
                        "proof" to listOf(CompletionMappers.proofDocument(proof)), "submittedAt" to FieldValue.serverTimestamp(), "result" to null))
                    batch.update(taskRef, "status", "AWAITING_VERIFICATION"); batch.commit().await()
                }
                auth.signOut(); auth.signIn(emailA, password)
                val saved = completions.submitCompletion(h.id, task.id, proof)
                assertEquals(a.id, saved.completedBy); assertEquals(listOf(proof), saved.proof)
                assertTrue(saved.submittedAt > 0); assertNull(saved.result)
                assertEquals(TaskStatus.AWAITING_VERIFICATION, assignments.getAssignment(h.id, task.id)!!.status)
                assertEquals(saved, completions.submitCompletion(h.id, task.id, proof))
                try {
                    completions.submitCompletion(h.id, task.id, proof.copy(id = "other", mediaUrl = ProofValidation.path(h.id, task.id, a.id, "other")))
                    fail("Conflicting proof accepted")
                } catch (_: CompletionConflictException) { }
                assertEquals(1, completions.getHouseholdCompletions(h.id).size)
                assertEquals(setOf("householdId", "assignmentId", "completedBy", "proof", "submittedAt", "result"), ref.get(Source.SERVER).await().data!!.keys)
                mark("PASS atomic completion/proof metadata/status, identical retry and conflicting retry")
                for (changes in listOf(mapOf("completedBy" to b.id), mapOf("result" to mapOf("finalRating" to 10, "pointsAwarded" to 20)), mapOf("proof" to emptyList<Any>())))
                    denied("protected completion edit") { ref.update(changes).await() }
                denied("completion deletion") { ref.delete().await() }
                denied("client verification") { taskRef.update("status", "VERIFIED").await() }
                denied("client reward") { db.document(FirestorePaths.member(h.id, a.id)).update("contributionPoints", 20L).await() }
                auth.signOut(); auth.signIn(emailB, password)
                assertEquals(saved, completions.getCompletion(h.id, task.id))
                assertEquals(listOf(saved), completions.getHouseholdCompletions(h.id))
                if (liveStorage) assertArrayEquals(png, FirebaseProofRepository(clients.auth, clients.storage).downloadProof(h.id, task.id, proof))
                denied("peer private user read") { db.document(FirestorePaths.user(a.id)).get(Source.SERVER).await() }
                mark("PASS peer completion reads after sign-in; private profile remains denied")
                auth.signOut(); auth.signUp("Completion Outsider $run", "roomie-completion-$run-c@example.com", password)
                denied("outsider completion read") { completions.getCompletion(h.id, task.id) }
                denied("outsider completion list") { completions.getHouseholdCompletions(h.id) }
                denied("outsider submission") { completions.submitCompletion(h.id, untouched.id,
                    ProofSubmission("proof", ProofValidation.path(h.id, untouched.id, auth.currentUserId!!, "proof"), ProofType.PHOTO)) }
                auth.signOut()
                denied("signed-out completion read") { ref.get(Source.SERVER).await() }
                auth.signIn(emailA, password)
                assertEquals(saved, completions.getCompletion(h.id, task.id))
                assertEquals(0, households.getHouseholdMembers(h.id).first { it.member.userId == a.id }.member.contributionPoints)
                mark("ALL STAGE 2 FIRESTORE CHECKS PASSED run=$run household=${h.id} liveStorage=$liveStorage")
            }
        } finally { auth.signOut() }
    }
    private suspend fun denied(label: String, action: suspend () -> Unit) {
        try { withTimeout(30_000) { action() }; fail("Unexpectedly allowed: $label") }
        catch (failure: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, failure.code)
            Log.i("RoomieCompletionTest", "PASS denied: $label")
        }
    }
}
