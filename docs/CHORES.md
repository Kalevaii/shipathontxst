# Chore persistence backend contract

This increment adds chore definitions only. It reuses the shared `Chore`, `Difficulty`, and
`Recurrence` models and `FirestoreMappers`; no shared-model change was needed.
Authentication, households, Firebase cache policy, UI, MainActivity, and Gradle are unchanged.

## Repository API

Create FirebaseClients once before constructing repositories, then inject the chore repository
into a feature ViewModel. Do not call Firebase from Compose.

```kotlin
val clients = FirebaseClients.create(applicationContext)
val chores: ChoreRepository = FirebaseChoreRepository(clients.auth, clients.firestore)
val created = chores.createChore(householdId, ChoreDetails(
    title = "Clean kitchen",
    estimatedMinutes = 30,
    difficulty = Difficulty.MEDIUM,
    maxPoints = 15,
    description = "Clean counters and sink",
    recurrence = Recurrence.EveryDays(3),
))
val one = chores.getChore(householdId, created.id) // null only when confirmed missing
val all = chores.getHouseholdChores(householdId) // stable ID ordering
val edited = chores.updateChore(householdId, created.id, ChoreDetails(
    title = "Clean kitchen",
    estimatedMinutes = 40,
    difficulty = Difficulty.HARD,
    maxPoints = 20,
    recurrence = null, // change to a one-time definition
))
```

`ChoreDetails` is the editable-input value object, not a duplicate persisted entity. Updates replace
all editable fields: callers must supply the desired complete content, including description and
recurrence. A default description is empty and default recurrence is one-time.
All operations are suspend fetch/write functions; no real-time listeners were added.

All operations require a signed-in user, validate path segments, and reject successful results if
the account changed while suspended. Firestore rules are authoritative for membership. Writes use
online transactions, check the session inside the callback, and preserve cancellation/errors.
Invalid inputs throw IllegalArgumentException, missing auth throws NotAuthenticatedException,
changed sessions throw SessionChangedException, and updating a missing chore throws
ChoreNotFoundException (never an upsert). Firestore failures, including PERMISSION_DENIED, propagate.
A write may have committed even if the caller loses its response or session; reconcile by fetching
before blindly retrying creation. This increment does not provide an idempotency-key API.

## Document schema

Path: `households/{householdId}/chores/{choreId}`. The generated document ID is authoritative;
there is no duplicated `id` field. Stored fields are exactly:

- `householdId`: string matching the parent path; immutable.
- `title`: string, 1–100 characters, nonblank, no control characters; repository trims outer whitespace.
- `description`: string, up to 2,000 characters.
- `estimatedMinutes`: integer, 1–1,440 (required by the existing shared model).
- `difficulty`: `EASY`, `MEDIUM`, or `HARD`.
- `maxPoints`: integer, 0–1,000; independent of difficulty.
- `createdBy`: signed-in creator's UID; immutable.
- `createdAt`: server timestamp; immutable, persistence metadata rather than a new domain-model field.
- `recurrence`: explicit null, or one of the exact maps below.

```text
null
{ type: "EVERY_DAYS", interval: 3 }
{ type: "WEEKLY", weekdays: ["MONDAY", "FRIDAY"] }
```

Every-days intervals must be integers from 1–365. Weekly days must be a nonempty unique list of
valid Weekday names. Unknown recurrence shapes, duplicate days, unknown days, fractional intervals,
and missing recurrence fields are rejected; the mapper does not silently reinterpret malformed
recurrence as one-time. Serialization orders weekly days by the shared enum's order.

Workload is deliberately not stored: use the existing `domain.workloadValue(chore.difficulty)`
(Easy 1, Medium 2, Hard 3). UI defaults may use the existing `defaultMaxPoints`; repository-supplied
maxPoints remains independent. No reward or workload algorithm is duplicated.

No deadline was added: this increment persists a chore definition; occurrence due dates belong to
future task assignments. No enabled/active flag exists in the current shared model, so it is deferred.

## Rules

Only members identified by their authoritative household membership document may get/list/create
chores within that household. Being authenticated or belonging to a different household is insufficient.
Creation requires the current UID as createdBy, the path householdId, a server createdAt timestamp,
valid content, and an exact field set. Extra id/workload/points-total fields are rejected.

Any household member may edit title, description, estimatedMinutes, difficulty, maxPoints, and
recurrence. All other fields remain immutable; a chore cannot move households. Deletion is denied.
The narrow `/chores/{choreId}` match leaves existing private profile, household, membership, invite,
and default-deny protections intact.

## Verification

- Firebase-enabled debug app and instrumentation APK: build passed.
- JVM tests: 46 passed (35 existing plus 11 additional tests).
- Firestore emulator: 19 passed (9 existing household tests plus 10 chore tests).
- Firebase-disabled debug build and all 46 JVM tests: passed.
- Rules deployed to `backendtest-21628` with user confirmation; live chore/Auth/household integration: passed (1 test, 19.129 seconds).

[Emulator evidence](test-results/chore-rules-tests.txt), [live runner result](test-results/chore-live-instrumentation.txt),
[live checkpoints](test-results/chore-live-checkpoints.txt).

Passing run: `4d8c6c08e161`; household `LZ37B0IGCs7c3BwCb97x`; invite `SM8NHH`;
one-time/edit test chore `IGtEb5p7bBlSJvqmfitf`. Three disposable chores and test accounts remain
for console inspection. No real user documents were edited by the test.

Existing Auth/household live verification is
recorded separately in [LIVE_FIREBASE_TESTS.md](LIVE_FIREBASE_TESTS.md).

To run local rules tests without a live Firebase project:

```sh
cd tests/firestore
pnpm install --ignore-scripts --frozen-lockfile
pnpm test
```

Node 22+, Java 21+, and pnpm are needed. The demo-project emulator runs on localhost:8085 using
`firebase.rules-test.json`; both rule suites run sequentially against the checked-in firestore.rules.

The opt-in `LiveFirebaseBackendTest` now covers chore creation, server timestamp/field structure,
all recurrence definitions, missing get/update behavior, creator reads, joined-member reads and
updates, protected fields, nonmember CRUD denials, and the previous Auth/household checks.
It must target `backendtest-21628`; it creates disposable accounts/households/chores, leaves them for
inspection, and does not log credentials. Run only after the chore rules are published:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -Proomie.firebase.enabled=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.rommie.app.LiveFirebaseBackendTest \
  -e liveFirebase true -e preJoinDeniedRead true \
  com.rommie.app.test/androidx.test.runner.AndroidJUnitRunner
```

Check the JUnit result, not only adb's exit code. Negative tests require PERMISSION_DENIED specifically.

## Files in this increment

Created:
- `app/src/main/java/com/rommie/app/data/repository/ChoreRepository.kt`
- `app/src/main/java/com/rommie/app/data/repository/ChoreValidation.kt`
- `app/src/main/java/com/rommie/app/data/repository/FirebaseChoreRepository.kt`
- `app/src/main/java/com/rommie/app/data/firebase/ChoreStore.kt`
- `app/src/main/java/com/rommie/app/data/firebase/FirebaseChoreStore.kt`
- `app/src/test/java/com/rommie/app/data/repository/ChoreValidationTest.kt`
- `app/src/test/java/com/rommie/app/data/repository/ChoreRepositoryTest.kt`
- `tests/firestore/chores.test.mjs`
- `docs/CHORES.md`
- `docs/test-results/chore-rules-tests.txt`
- `docs/test-results/chore-live-instrumentation.txt`
- `docs/test-results/chore-live-checkpoints.txt`

Modified:
- `app/src/main/java/com/rommie/app/data/firebase/FirestoreMappers.kt`
- `app/src/test/java/com/rommie/app/data/firebase/FirestoreMappersTest.kt`
- `app/src/androidTest/java/com/rommie/app/LiveFirebaseBackendTest.kt`
- `firestore.rules`
- `tests/firestore/package.json`
- `README.md`

## Deferred

No assignment/distribution logic, task occurrences, recurrence regeneration, deletion, deadlines,
media uploads, verification/scoring, AI, pagination, or UI wiring. The next recommended increment
is assignment persistence plus deterministic fair assignment, only after this increment is live-green
and separately requested. No assignment work has started.
