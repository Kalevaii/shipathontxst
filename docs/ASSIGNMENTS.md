# Assignment persistence and deterministic fair assignment

This increment reuses TaskAssignment, Chore, HouseholdMember, Difficulty, TaskStatus, Availability,
AvailabilityOverride, and the existing domain.workloadValue helper. No shared-model, UI, Gradle,
authentication, household, chore-repository, or cache-policy changes were needed.

## Pure assignment engine

`FairAssignmentEngine.selectMember(chore, members, assignments, dueAt, availability, overrides,
unavailableUserIds)` returns a HouseholdMember, or null if no eligible member exists. It does not
read Firebase, call Android/Compose, use the current clock/randomness, or mutate its inputs.
Callers supply a complete workload history for the household and explicit due time. Foreign-household
members/history are ignored; duplicate relevant member/assignment identities are rejected.

The engine first excludes explicitly unavailable users and anyone busy during the estimated work
window `[dueAt - estimatedMinutes * 60_000, dueAt)`. Remaining candidates are ordered lexicographically:

1. Lowest total workload from ASSIGNED, AWAITING_VERIFICATION, and VERIFIED assignments.
2. Lowest active workload from ASSIGNED and AWAITING_VERIFICATION assignments.
3. Fewest non-cancelled assignments of this same chore.
4. Explicit availability throughout the work window before an unknown/partially known schedule.
5. Oldest latest non-cancelled assignment dueAt; no history sorts first.
6. Lexicographically smallest userId.

Workload sums use immutable assignment workloadValue snapshots, not task count, reward points,
contribution points, or the chore's current difficulty. New snapshots use the existing mapping:
Easy 1, Medium 2, Hard 3. The incoming chore adds the same workload to any selected member, so it
does not change relative scores for that single decision; once persisted, its weight affects the
next decision. Re-fetch/include each new assignment before selecting for another chore. Batch callers
must also choose a stable chore/occurrence order; the engine selects one chore at a time.

CANCELLED assignments contribute neither workload nor repetition/recency. VERIFIED assignments
contribute historical workload. Recency uses scheduled dueAt because that is the timestamp already
available in the shared model, not an invented assignedAt domain field.

## Availability semantics

Availability is caller-supplied only; no availability persistence subsystem was added.
Weekly windows use each row's explicit timeZoneId and weekday/local minute range. Java Calendar
handles local date changes and DST without requiring API 26/java.time. The evaluator checks every
minute boundary and temporary-override boundary in the planned work window (maximum 24 hours).

At each instant, applicable absolute overrides replace weekly entries. Conflicting busy entries win.
AVAILABLE and DAY_OFF count as free; CLASS, WORK, AWAY, and UNAVAILABLE exclude the member. Unknown
schedule periods remain eligible, with the tie preference described above. Invalid time-zone IDs
fail clearly. `unavailableUserIds` lets a caller exclude an inactive/unavailable user without adding
an unsupported active field to HouseholdMember. Free time never overrides the workload/repetition
criteria, so the roommate with the most free time is not automatically assigned everything.

The estimated window ending at dueAt is an explicit MVP planning assumption; no start-time
optimization or rescheduling service is implemented.

## Repository API and integration

`AssignmentRepository` exposes suspend functions:

- createAssignment(householdId, assignmentId, choreId, assignedUserId, dueAt)
- getAssignment(householdId, assignmentId): TaskAssignment? (null only when confirmed absent)
- getHouseholdAssignments(householdId): List<TaskAssignment>
- getAssignmentsForUser(householdId, userId): List<TaskAssignment>

Lists are sorted by dueAt then ID. User-filtered reads use a single equality query and require no
new composite index. Any household member may see household assignments, including roommates' tasks.
The pure selector is intentionally separate from persistence:

```kotlin
val clients = FirebaseClients.create(applicationContext)
val assignments: AssignmentRepository = FirebaseAssignmentRepository(clients.auth, clients.firestore)
val history = assignments.getHouseholdAssignments(householdId)
val members = households.getHouseholdMembers(householdId).map { it.member }
val selected = FairAssignmentEngine.selectMember(chore, members, history, dueAt,
    availability = weeklyAvailability, overrides = temporaryOverrides)
if (selected != null) {
    val saved = assignments.createAssignment(householdId, occurrenceId, chore.id, selected.userId, dueAt)
}
```

Use FirebaseClients before injecting repositories; its verified eager in-memory cache remains intact.
Do not construct Firebase in Compose. No live listeners or UI wiring were added.

Occurrence IDs must contain 1–128 ASCII letters, digits, hyphens, or underscores. The caller must
reuse the same ID for the same planned occurrence. The online creation transaction:

1. Checks the current Auth session and existing occurrence document.
2. If present, returns the original assignment when chore/assignee/dueAt match; otherwise throws
   AssignmentConflictException. It never overwrites an occurrence or recalculates old snapshots.
3. If absent, reads the chore and selected membership in the same household, checks both references,
   derives workload with the shared helper and maxPoints from the stored chore, and writes ASSIGNED
   plus server creation metadata.

Concurrent retries using the same ID cannot overwrite one another. Missing references throw
AssignmentReferenceException. Repository methods validate IDs/time, reject signed-out calls, discard
successful results after an account switch, and preserve cancellation/Firebase permission errors.
A same-ID retry after chore editing still returns the original historical workload/points snapshot.

Fair selection and the history fetch are not a globally serialized server scheduler. Different
occurrence IDs can be selected concurrently from stale history, and clients can call valid manual
assignment creation without using the selector. Rules enforce household/reference/snapshot integrity,
not an optimal fairness decision. Callers should sequence automatic decisions for the demo; a trusted
scheduler/serialization mechanism is deferred. Automatic recurrence expansion/deduplication is also
deferred; using different IDs for the same logical occurrence is not prevented by this API.

## Firestore document schema and access

Path: `households/{householdId}/assignments/{assignmentId}`. ID is authoritative in the path;
there is no duplicate id or chore title/description in the payload.

- householdId: string, must match parent household.
- choreId: reference ID of a chore in the same household.
- assignedUserId: userId of an authoritative member document in the same household.
- dueAt: integer UTC epoch milliseconds, 1–253402300799999 (matches model units).
- workloadValue: integer snapshot, 1–3, matching the referenced chore's difficulty at creation.
- maxPoints: integer snapshot matching the referenced chore's maxPoints at creation.
- status: ASSIGNED at creation.
- createdBy: authenticated caller UID (may differ from assignee); wire audit metadata.
- createdAt: server timestamp; wire audit metadata.

Household members may read/create valid assignments. Nonmembers and unauthenticated users are denied.
Exact field sets reject injected fields. Cross-household references, nonmember assignees, spoofed
creator/timestamp/status/workload/points, and invalid due times are rejected. Rules mirror the fixed
workload mapping only to validate untrusted writes; the Kotlin application calculation remains in
one shared helper. All assignment updates and deletes are denied, including ownership and status.
Status-transition APIs are deferred until completion/proof/verification invariants are implemented.
Existing user, household, member, invite, and chore rule protections are unchanged.

## Verification

- Firebase-enabled debug build and live-test APK: passed.
- JVM tests: 68 passed (46 existing plus 22 new).
- Emulator rules: 27 passed (19 existing plus 8 assignment cases).
- Firebase-disabled debug build and JVM tests: passed.
- Assignment rules published to backendtest-21628 with user approval.
- Live assignment integration: passed, JUnit OK (1 test), 29.822 seconds.
- Live run: d73b25c92635; household: u9W4YCVRnyJaJ3PrgQKR; integrationFailures=[].

[Emulator output](test-results/assignment-rules-tests.txt).
[Live JUnit result](test-results/assignment-live-instrumentation.txt) and
[live checkpoints](test-results/assignment-live-checkpoints.txt) cover successful member reads,
assignment persistence/retries, protected-field denials, private profiles, and outsider denials.
Run all rule suites with `cd tests/firestore && pnpm test` (Node 22+, Java 21+, installed locked
packages). The existing demo emulator configuration is reused; no live credentials are involved.

After the rules are published, the opt-in LiveFirebaseBackendTest creates a household, members,
chores and three assignments. It compares repeated/reordered selector inputs, verifies snapshots,
idempotent retries/conflicts, missing references, both members' reads, per-user lists, immutable
fields, nonmember denials, and all existing Auth/household/chore checks. It leaves disposable test
records for console inspection and never logs passwords. Reproduce with:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -Proomie.firebase.enabled=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.rommie.app.LiveFirebaseBackendTest \
  -e liveFirebase true -e preJoinDeniedRead true \
  com.rommie.app.test/androidx.test.runner.AndroidJUnitRunner
```

The live project guard is backendtest-21628. Check JUnit results, not just adb exit status.

## File inventory

Created:
- app/src/main/java/com/rommie/app/domain/FairAssignmentEngine.kt
- app/src/main/java/com/rommie/app/data/repository/AssignmentRepository.kt
- app/src/main/java/com/rommie/app/data/repository/FirebaseAssignmentRepository.kt
- app/src/main/java/com/rommie/app/data/firebase/AssignmentStore.kt
- app/src/main/java/com/rommie/app/data/firebase/FirebaseAssignmentStore.kt
- app/src/main/java/com/rommie/app/data/firebase/AssignmentMappers.kt
- app/src/test/java/com/rommie/app/domain/FairAssignmentEngineTest.kt
- app/src/test/java/com/rommie/app/data/repository/AssignmentRepositoryTest.kt
- app/src/test/java/com/rommie/app/data/firebase/AssignmentMappersTest.kt
- tests/firestore/assignments.test.mjs
- docs/ASSIGNMENTS.md
- docs/test-results/assignment-rules-tests.txt
- docs/test-results/assignment-live-instrumentation.txt
- docs/test-results/assignment-live-checkpoints.txt

Modified:
- app/src/main/java/com/rommie/app/data/firebase/FirestorePaths.kt
- app/src/androidTest/java/com/rommie/app/LiveFirebaseBackendTest.kt
- firestore.rules
- README.md

Deferred: UI, automatic recurring occurrence generation, reassignment, status changes, proof uploads,
completion/review/reward processing, leaderboard, AI, pagination, and server-enforced fair scheduling.
Next recommended increment after live verification: proof-backed completion submission and Storage
access rules. No proof or review implementation has started.
