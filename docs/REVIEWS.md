# Stage 3: private roommate reviews

Only review submission, own-review detection, and a separate trusted reader are added. No shared models,
Stage 2 implementations, Storage rules, UI, Gradle dependencies, or calculation helpers changed.
No rating finalization, points, leaderboard, AI, or Stage 4 work was implemented.

## Schema and privacy

`households/{householdId}/completions/{completionId}/reviews/{reviewerUid}`

Fields: householdId, completionId, reviewerId, rating (integer 1–10), submittedAt (server timestamp).
The path UID is the document ID and must equal the authenticated reviewer and reviewerId field.
No names, email, public individual scores, or reviewer lists are added to completion documents.
The existing Review model remains authoritative; private mapping uses submittedAt epoch milliseconds.

A household member can create a review only when the completion and assignment exist in the household,
completion/assignment identities agree, status is AWAITING_VERIFICATION, and result is null. The reviewer
must differ from both completedBy and assignedUserId. These checks are enforced in Firestore rules,
not just by Android validation. Missing/malformed documents fail closed.

An eligible household member may get only their own review document, including checking whether it is
absent. Own-review reads remain possible after eventual finalization; new submissions do not. A worker
cannot read any raw review, even a missing document at their own UID. Other members cannot read one
another's ratings before OR after submitting. All review list queries, updates and deletes are denied.
The household creator has no special review-read privilege. Catch-all deny remains in place.

One UID-keyed immutable document prevents duplicates, including identical repeated writes. The transaction
checks the document before writing; concurrent submissions resolve to a single record. Duplicate calls
throw DuplicateReviewException rather than changing the original rating. Membership and immutable-create
rules remain the security boundary if a client bypasses repository code.

## Android API

`ReviewRepository` provides only:

- `submitReview(householdId, completionId, rating): Unit`
- `hasReviewed(householdId, completionId): Boolean`

Construct `FirebaseReviewRepository(clients.auth, clients.firestore)` using the existing FirebaseClients.
No public Android API returns all raw reviews. hasReviewed is for eligible reviewers; permission failures
for workers, nonmembers or invalid completion contexts are not converted into false. Submission requires
signed-in state, canonical IDs, a valid rating and eligible completion. Reads use Source.SERVER. Session
changes discard results and Firebase/cancellation failures propagate. If a commit result is uncertain,
call hasReviewed before offering another submission; committed reviews are immutable.

The internal ReviewStore seam supports orchestration tests; FirebaseReviewStore contains transaction
access, and ReviewMappers maps only the caller's private record when checking a stored document.

## Trusted retrieval, separated from Android

`server/reviews/read-private-reviews.mjs` exports
`readPrivateReviews(adminFirestore, householdId, completionId)` for future trusted server orchestration.
It accepts an already-authenticated Firebase Admin Firestore instance, validates parent/record scopes,
returns private Review-shaped wire records sorted by reviewer UID, and performs no writes/calculations.

This module is not bundled into Android and exposes no HTTP/callable endpoint. No Admin credentials,
SDK initialization or server deployment is introduced in Stage 3. Server operators must supply a trusted
Admin instance later. Do not expose its results through a user-facing API. Emulator coverage exercises
its read contract through a disabled-rules test-only adapter and confirms ordinary clients remain denied.
It is not claimed as a deployed Admin-service integration test. A future finalizer must read transactionally
and define review eligibility/quorum; this simple reader is not an atomic finalization snapshot.

## Verification

- Firebase-enabled debug build/unit tests: passed; 92 JVM tests (77 existing + 15 new).
- Live-test APK build: passed.
- Firebase-disabled build/unit tests: passed.
- Emulator suites: 58 passed: 51 Firestore security cases, 1 trusted-reader integration case, 6 Storage cases.
- Stage 3 rules: published to backendtest-21628 with user approval.
- Stage 3 live test: passed, JUnit OK (1 test), 19.92 seconds.
- Live review run: 56517ff6de90; household: jvZRExFHKhLFFZbR22Q1.
- Stage 2 live completion regression: passed, JUnit OK (1 test), 15.629 seconds.
- Existing live backend regression: passed, JUnit OK (1 test), 27.794 seconds.

Evidence: [emulator suites](test-results/review-rules-tests.txt),
[live review JUnit](test-results/review-live-instrumentation.txt),
[live review checkpoints](test-results/review-live-checkpoints.txt),
[Stage 2 regression](test-results/review-completion-regression.txt),
[backend regression](test-results/review-backend-regression.txt).

The live Stage 3 test uses A (creator), B (worker), C (reviewer) and D (outsider). It creates a completion
with the existing explicitly labeled proof-metadata fixture, submits independent ratings 10 and 1,
checks duplicate/self-review/outsider rejection, checks worker/peer privacy and immutable fields, and
verifies persistence after sign-in. It asserts no final result or contribution reward was written.
No live Storage operation is needed or claimed. Stage 2's external owner-permission limitation remains.

Local checks reuse the existing Gradle and Firestore/Storage emulator suites. Reproduce live testing after
building/installing the Firebase-enabled debug and instrumentation APKs:

```sh
adb shell am instrument -w -e class com.rommie.app.LiveFirebaseReviewTest \
  -e liveFirebase true com.rommie.app.test/androidx.test.runner.AndroidJUnitRunner
```

Also rerun LiveFirebaseCompletionTest with `liveStorage false` and LiveFirebaseBackendTest with
`preJoinDeniedRead true`. All live tests require `liveFirebase true`, guard project backendtest-21628,
leave disposable records for inspection, and sign out afterward. Check JUnit output, not only adb exit code.

## Files created

- app/src/main/java/com/rommie/app/data/repository/ReviewRepository.kt
- app/src/main/java/com/rommie/app/data/repository/FirebaseReviewRepository.kt
- app/src/main/java/com/rommie/app/data/repository/ReviewValidation.kt
- app/src/main/java/com/rommie/app/data/firebase/ReviewStore.kt
- app/src/main/java/com/rommie/app/data/firebase/FirebaseReviewStore.kt
- app/src/main/java/com/rommie/app/data/firebase/ReviewMappers.kt
- app/src/test/java/com/rommie/app/data/repository/ReviewRepositoryTest.kt
- app/src/test/java/com/rommie/app/data/repository/ReviewValidationTest.kt
- app/src/test/java/com/rommie/app/data/firebase/ReviewMappersTest.kt
- app/src/androidTest/java/com/rommie/app/LiveFirebaseReviewTest.kt
- server/reviews/read-private-reviews.mjs
- tests/firestore/reviews.test.mjs
- docs/REVIEWS.md
- docs/test-results/review-rules-tests.txt
- docs/test-results/review-live-instrumentation.txt
- docs/test-results/review-live-checkpoints.txt
- docs/test-results/review-completion-regression.txt
- docs/test-results/review-backend-regression.txt

## Files modified

- firestore.rules (nested reviews match and catch-all comment only)
- README.md (handoff link)

Deferred: review quorum/deadlines, final rating, rewards, leaderboard, trusted server deployment,
UI, AI, and resolving live Storage project-owner permissions. Stop after Stage 3.
