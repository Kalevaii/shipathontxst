package com.rommie.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.rommie.app.data.firebase.FirebaseClients
import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.data.repository.FirebaseAuthRepository
import com.rommie.app.data.repository.FirebaseHouseholdRepository
import com.rommie.app.data.repository.FirebaseUserRepository
import com.rommie.app.data.repository.FirebaseChoreRepository
import com.rommie.app.data.repository.ChoreDetails
import com.rommie.app.data.repository.ChoreNotFoundException
import com.rommie.app.data.repository.FirebaseAssignmentRepository
import com.rommie.app.data.repository.AssignmentConflictException
import com.rommie.app.data.repository.AssignmentReferenceException
import com.rommie.app.domain.FairAssignmentEngine
import com.rommie.app.domain.workloadValue
import com.rommie.app.model.TaskAssignment
import com.rommie.app.model.Difficulty
import com.rommie.app.model.Recurrence
import com.rommie.app.model.Weekday
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Explicit opt-in only. Leaves disposable records for console inspection; never logs credentials. */
@RunWith(AndroidJUnit4::class)
class LiveFirebaseBackendTest {
    @Test fun realBackendLifecycleAndAccessRules() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live tests require explicit opt-in", args.getString("liveFirebase") == "true")
        val clients = FirebaseClients.create(InstrumentationRegistry.getInstrumentation().targetContext)
        check(clients.firestore.app.options.projectId == "backendtest-21628") { "Wrong Firebase project" }
        val users = FirebaseUserRepository(clients.auth, clients.firestore)
        val auth = FirebaseAuthRepository(clients.auth, users)
        val households = FirebaseHouseholdRepository(clients.auth, clients.firestore, users)
        val chores = FirebaseChoreRepository(clients.auth, clients.firestore)
        val assignments = FirebaseAssignmentRepository(clients.auth, clients.firestore)
        val db = clients.firestore
        val runId = UUID.randomUUID().toString().replace("-", "").take(12)
        val password = "Roomie!${UUID.randomUUID()}a7"
        val emailA = "roomie-test-$runId-a@example.com"
        val emailB = "roomie-test-$runId-b@example.com"
        val emailC = "roomie-test-$runId-c@example.com"
        fun mark(message: String) = Log.i("RoomieLiveTest", message)
        val integrationFailures = mutableListOf<String>()
        suspend fun checkIntegration(label: String, action: suspend () -> Unit) {
            try {
                action()
                mark("PASS integration: $label")
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                integrationFailures += label
                mark("FAIL integration: $label (${failure.javaClass.simpleName})")
            }
        }
        try {
            withTimeout(240_000) {
                auth.signOut()
                val a = auth.signUp("Test Creator $runId", emailA, password)
                assertEquals(a, users.fetchCurrentUserProfile())
                assertEquals(setOf("name", "email"), db.document(FirestorePaths.user(a.id)).get(Source.SERVER).await().data!!.keys)
                mark("PASS signup/profile creator=${a.id} email=$emailA")
                auth.signOut()
                assertNull(auth.currentUserId)
                assertEquals(a, auth.signIn(emailA, password))
                mark("PASS signout/signin")
                val h = households.createHousehold("Roomie Live Test $runId")
                assertEquals(h, households.getCurrentUserHousehold())
                assertEquals(h.id, db.document(FirestorePaths.invite(h.inviteCode)).get(Source.SERVER).await().getString("householdId"))
                assertEquals(h.id, db.document(FirestorePaths.userHousehold(a.id)).get(Source.SERVER).await().getString("householdId"))
                assertEquals(1, households.getHouseholdMembers(h.id).size)
                mark("PASS create household=${h.id} invite=${h.inviteCode}")
                val details = ChoreDetails(" Kitchen $runId ", 30, Difficulty.MEDIUM, 15, "Clean counters")
                val chore = chores.createChore(h.id, details)
                assertEquals(a.id, chore.createdBy)
                assertEquals(details.title.trim(), chore.title)
                assertEquals(chore, chores.getChore(h.id, chore.id))
                val choreRef = db.document(FirestorePaths.chore(h.id, chore.id))
                val initialChoreDoc = choreRef.get(Source.SERVER).await()
                val createdAt = checkNotNull(initialChoreDoc.getTimestamp("createdAt"))
                assertEquals(setOf("householdId", "title", "description", "estimatedMinutes", "difficulty", "maxPoints", "createdBy", "recurrence", "createdAt"), initialChoreDoc.data!!.keys)
                val recurring = chores.createChore(h.id, details.copy(title = "Dishes", recurrence = Recurrence.EveryDays(3)))
                val weekly = chores.createChore(h.id, details.copy(title = "Bathroom", difficulty = Difficulty.HARD, recurrence = Recurrence.Weekly(setOf(Weekday.MONDAY, Weekday.FRIDAY))))
                assertEquals(setOf(chore, recurring, weekly), chores.getHouseholdChores(h.id).toSet())
                assertNull(chores.getChore(h.id, "missing-$runId"))
                try {
                    chores.updateChore(h.id, "missing-$runId", details)
                    fail("Missing chore update unexpectedly succeeded")
                } catch (_: ChoreNotFoundException) { /* Expected domain error, not an upsert. */ }
                mark("PASS chore create/read/list and recurrence chore=${chore.id}")
                auth.signOut()
                val b = auth.signUp("Test Joiner $runId", emailB, password)
                if (args.getString("preJoinDeniedRead") == "true") {
                    denied("nonmember household read") { db.document(FirestorePaths.household(h.id)).get(Source.SERVER).await() }
                    denied("nonmember roster read") { db.collection(FirestorePaths.members(h.id)).get(Source.SERVER).await() }
                    denied("prejoin chore read") { chores.getChore(h.id, chore.id) }
                    denied("prejoin chore list") { chores.getHouseholdChores(h.id) }
                }
                checkIntegration("join return/read") { assertEquals(h, households.joinHouseholdByCode(" ${h.inviteCode.lowercase()} ")) }
                // Inspect the committed membership independently so later security checks still run.
                val memberB = db.document(FirestorePaths.member(h.id, b.id)).get(Source.SERVER).await()
                assertTrue("Join did not create membership", memberB.exists())
                assertEquals(h.id, db.document(FirestorePaths.userHousehold(b.id)).get(Source.SERVER).await().getString("householdId"))
                checkIntegration("joiner direct household read") { assertEquals(h, households.getHousehold(h.id)) }
                checkIntegration("joiner creator membership read") { assertTrue(db.document(FirestorePaths.member(h.id, a.id)).get(Source.SERVER).await().exists()) }
                checkIntegration("joiner current household read") { assertEquals(h, households.getCurrentUserHousehold()) }
                checkIntegration("joiner roster read") { assertEquals(setOf(a.id, b.id), households.getHouseholdMembers(h.id).map { it.member.userId }.toSet()) }
                auth.signOut()
                assertEquals(b, auth.signIn(emailB, password))
                checkIntegration("joiner reads after re-signin") {
                    assertEquals(h, households.getCurrentUserHousehold())
                    assertEquals(2, households.getHouseholdMembers(h.id).size)
                }
                mark("PASS membership committed joiner=${b.id} email=$emailB")
                val roster = households.getHouseholdMembers(h.id).map { it.member }
                val savedAssignments = mutableListOf<TaskAssignment>()
                for ((index, definition) in listOf(chore, recurring, weekly).withIndex()) {
                    val dueAt = 2_000_000_000_000L + index
                    val history = assignments.getHouseholdAssignments(h.id)
                    val selected = checkNotNull(FairAssignmentEngine.selectMember(definition, roster, history, dueAt))
                    assertEquals(selected, FairAssignmentEngine.selectMember(definition, roster.reversed(), history.reversed(), dueAt))
                    val assignment = assignments.createAssignment(h.id, "occurrence-$index", definition.id, selected.userId, dueAt)
                    assertEquals(workloadValue(definition.difficulty), assignment.workloadValue)
                    assertEquals(definition.maxPoints, assignment.maxPoints)
                    assertEquals(assignment, assignments.getAssignment(h.id, assignment.id))
                    assertEquals(assignment, assignments.createAssignment(h.id, assignment.id, definition.id, selected.userId, dueAt))
                    savedAssignments += assignment
                }
                assertEquals(setOf(a.id, b.id), savedAssignments.map { it.assignedUserId }.toSet())
                assertEquals(savedAssignments.toSet(), assignments.getHouseholdAssignments(h.id).toSet())
                for (member in roster) assertEquals(savedAssignments.filter { it.assignedUserId == member.userId }, assignments.getAssignmentsForUser(h.id, member.userId))
                val originalAssignment = savedAssignments.first()
                val assignmentRef = db.document(FirestorePaths.assignment(h.id, originalAssignment.id))
                val assignmentDoc = assignmentRef.get(Source.SERVER).await()
                assertNotNull(assignmentDoc.getTimestamp("createdAt"))
                assertEquals(b.id, assignmentDoc.getString("createdBy"))
                assertEquals(setOf("householdId", "choreId", "assignedUserId", "dueAt", "workloadValue", "maxPoints", "status", "createdBy", "createdAt"), assignmentDoc.data!!.keys)
                try {
                    assignments.createAssignment(h.id, originalAssignment.id, originalAssignment.choreId, originalAssignment.assignedUserId, originalAssignment.dueAt + 10)
                    fail("Conflicting occurrence unexpectedly accepted")
                } catch (_: AssignmentConflictException) { }
                for ((missingChore, missingMember) in listOf("missing" to a.id, chore.id to "missing")) {
                    try {
                        assignments.createAssignment(h.id, "bad-reference", missingChore, missingMember, originalAssignment.dueAt)
                        fail("Missing assignment reference unexpectedly accepted")
                    } catch (_: AssignmentReferenceException) { }
                }
                denied("assignment ownership edit") { assignmentRef.update("assignedUserId", "forged").await() }
                denied("assignment points edit") { assignmentRef.update("maxPoints", 999L).await() }
                denied("assignment workload edit") { assignmentRef.update("workloadValue", 1L).await() }
                denied("assignment status edit") { assignmentRef.update("status", "VERIFIED").await() }
                denied("assignment due time edit") { assignmentRef.update("dueAt", 1L).await() }
                denied("assignment deletion") { assignmentRef.delete().await() }
                mark("PASS deterministic fair assignment/persistence/idempotency/protected fields")
                assertEquals(setOf(chore, recurring, weekly), chores.getHouseholdChores(h.id).toSet())
                val updated = chores.updateChore(h.id, chore.id, details.copy(title = "Updated kitchen", maxPoints = 20,
                    estimatedMinutes = 45, difficulty = Difficulty.HARD, recurrence = Recurrence.EveryDays(7)))
                assertEquals(chore.createdBy, updated.createdBy)
                assertEquals(updated, chores.getChore(h.id, chore.id))
                assertEquals(createdAt, choreRef.get(Source.SERVER).await().getTimestamp("createdAt"))
                assertEquals(originalAssignment, assignments.getAssignment(h.id, originalAssignment.id))
                assertEquals(originalAssignment, assignments.createAssignment(h.id, originalAssignment.id,
                    originalAssignment.choreId, originalAssignment.assignedUserId, originalAssignment.dueAt))
                val backToOneTime = chores.updateChore(h.id, chore.id, details.copy(title = "One-time kitchen", recurrence = null))
                assertNull(chores.getChore(h.id, chore.id)!!.recurrence)
                denied("chore creator edit") { choreRef.update("createdBy", b.id).await() }
                denied("chore household edit") { choreRef.update("householdId", "other").await() }
                denied("chore creation timestamp edit") { choreRef.update("createdAt", FieldValue.serverTimestamp()).await() }
                denied("chore deletion") { choreRef.delete().await() }
                mark("PASS joiner chore list/update and immutable metadata")
                denied("peer private profile read") { db.document(FirestorePaths.user(a.id)).get(Source.SERVER).await() }
                denied("peer private lookup read") { db.document(FirestorePaths.userHousehold(a.id)).get(Source.SERVER).await() }
                denied("add another UID") {
                    db.document(FirestorePaths.member(h.id, "forged-$runId")).set(mapOf("userId" to "forged-$runId", "displayName" to "Forged", "joinedAt" to FieldValue.serverTimestamp(), "contributionPoints" to 0L)).await()
                }
                denied("change own contribution points") { db.document(FirestorePaths.member(h.id, b.id)).update("contributionPoints", 999L).await() }
                denied("add workload score") { db.document(FirestorePaths.member(h.id, b.id)).update("workloadScore", 999L).await() }
                denied("change member identity") { db.document(FirestorePaths.member(h.id, b.id)).update("userId", a.id).await() }
                denied("change household creator") { db.document(FirestorePaths.household(h.id)).update("createdBy", b.id).await() }
                denied("change household invite code") { db.document(FirestorePaths.household(h.id)).update("inviteCode", "ABC234").await() }
                denied("enumerate invites") { db.collection("householdInvites").get(Source.SERVER).await() }
                denied("overwrite peer profile") { db.document(FirestorePaths.user(a.id)).set(mapOf("name" to "Forged", "email" to emailA)).await() }
                auth.signOut()
                auth.signIn(emailA, password)
                checkIntegration("creator roster after re-signin") { assertEquals(2, households.getHouseholdMembers(h.id).size) }
                assertEquals(backToOneTime, chores.getChore(h.id, chore.id))
                assertEquals(savedAssignments.toSet(), assignments.getHouseholdAssignments(h.id).toSet())
                assertEquals(originalAssignment, assignments.getAssignment(h.id, originalAssignment.id))
                mark("PASS creator reads assignment snapshots after chore edit and re-signin")
                denied("creator reading peer private profile") { db.document(FirestorePaths.user(b.id)).get(Source.SERVER).await() }
                val protectedMember = db.runTransaction { tx -> tx.get(db.document(FirestorePaths.member(h.id, b.id))) }.await()
                assertEquals(0L, protectedMember.getLong("contributionPoints"))
                assertFalse(protectedMember.contains("workloadScore"))
                mark("PASS protected membership values unchanged")
                auth.signOut()
                val c = auth.signUp("Test Outsider $runId", emailC, password)
                denied("outsider self-join without invite proof") {
                    val batch = db.batch()
                    batch.set(db.document(FirestorePaths.member(h.id, c.id)), mapOf("userId" to c.id, "displayName" to c.name, "joinedAt" to FieldValue.serverTimestamp(), "contributionPoints" to 0L))
                    batch.set(db.document(FirestorePaths.userHousehold(c.id)), mapOf("householdId" to h.id, "inviteCode" to "BAD000"))
                    batch.commit().await()
                }
                assertNull(households.getCurrentUserHousehold())
                denied("outsider chore read") { chores.getChore(h.id, chore.id) }
                denied("outsider chore list") { chores.getHouseholdChores(h.id) }
                denied("outsider chore create") { chores.createChore(h.id, details) }
                denied("outsider chore update") { chores.updateChore(h.id, chore.id, details) }
                denied("outsider assignment read") { assignments.getAssignment(h.id, originalAssignment.id) }
                denied("outsider assignment list") { assignments.getHouseholdAssignments(h.id) }
                denied("outsider user assignment list") { assignments.getAssignmentsForUser(h.id, a.id) }
                denied("outsider assignment create") { assignments.createAssignment(h.id, "outsider", chore.id, a.id, originalAssignment.dueAt) }
                denied("outsider household read") { db.document(FirestorePaths.household(h.id)).get(Source.SERVER).await() }
                denied("outsider roster read") { db.collection(FirestorePaths.members(h.id)).get(Source.SERVER).await() }
                auth.signOut()
                denied("unauthenticated profile read") { db.document(FirestorePaths.user(a.id)).get(Source.SERVER).await() }
                denied("unauthenticated household read") { db.document(FirestorePaths.household(h.id)).get(Source.SERVER).await() }
                denied("unauthenticated chore read") { choreRef.get(Source.SERVER).await() }
                denied("unauthenticated assignment read") { assignmentRef.get(Source.SERVER).await() }
                mark("ACCESS CHECKS COMPLETED run=$runId household=${h.id} integrationFailures=$integrationFailures")
                assertTrue("Live integration failures: $integrationFailures", integrationFailures.isEmpty())
                assertSame(db, FirebaseClients.create(InstrumentationRegistry.getInstrumentation().targetContext).firestore)
                mark("ALL LIVE CHECKS PASSED run=$runId household=${h.id}")
            }
        } finally { clients.auth.signOut() }
    }

    private suspend fun denied(label: String, action: suspend () -> Unit) {
        try {
            withTimeout(30_000) { action() }
            fail("Unexpectedly allowed: $label")
        } catch (failure: FirebaseFirestoreException) {
            assertEquals("Wrong error for $label", FirebaseFirestoreException.Code.PERMISSION_DENIED, failure.code)
            Log.i("RoomieLiveTest", "PASS denied: $label")
        }
    }
}
