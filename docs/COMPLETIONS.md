# Stage 2: completion and proof

Scope: task completion, proof upload/download repositories, atomic status transition, and access rules.
Reviews, finalization, rewards, leaderboard, UI, MainActivity, navigation, shared models, Gradle dependencies,
and FirebaseClients cache policy are unchanged. Stage 3 has not started.

## Existing architecture reused

Repository interfaces expose the existing TaskCompletion and ProofSubmission models. Firebase implementations
live in data/repository; serialization lives in data/firebase. TaskAssignment retains immutable workload and
maximum-point snapshots. HouseholdMember contributionPoints remains zero/client-protected in this workflow.
No duplicate models or calculation helpers were added. Existing arithmetic/trimmed rating and point calculations
are not used during submission, because submitting proof must never award points.

## Firestore schema and submission

`households/{householdId}/completions/{assignmentId}`: the completion ID is exactly the assignment ID,
so there can be only one completion per task. Fields:

- householdId: parent household ID.
- assignmentId: referenced task ID, matching the completion document ID.
- completedBy: the assigned worker's authenticated UID.
- proof: exactly one `{id, mediaUrl, type}` map for this MVP; type is PHOTO or VIDEO.
- submittedAt: Firestore server timestamp, mapped to epoch milliseconds in TaskCompletion.
- result: null. Only a future trusted verification workflow may supply VerificationResult.

The shared mediaUrl field contains a **relative private Storage object path**, not a public HTTPS download
URL. This intentionally preserves the shared model; consumers pass it to ProofRepository instead of an
unauthenticated image loader. One photo OR one video satisfies the MVP. Multiple evidence items are deferred.

The completion transaction reads the assignment and existing completion. Only the assigned worker can
submit. If no completion exists and status is ASSIGNED, it creates the completion and changes only the
assignment status to AWAITING_VERIFICATION in the same transaction. A retry with identical proof returns
the original committed result/time. Different proof throws CompletionConflictException. Reads are from the
server. Session changes discard successful results; authentication, permission and cancellation failures
are not hidden. If a server read fails after commit, retry the same assignment/proof to recover safely.

APIs:

- `CompletionRepository.submitCompletion(householdId, assignmentId, proof): TaskCompletion`
- `getCompletion(householdId, assignmentId): TaskCompletion?`
- `getHouseholdCompletions(householdId): List<TaskCompletion>` (submittedAt, then ID ascending)
- `ProofRepository.uploadProof(householdId, assignmentId, proofId, type, contentType, bytes): ProofSubmission`
- `downloadProof(householdId, assignmentId, proof): ByteArray`

Construct FirebaseCompletionRepository with the existing Auth/Firestore clients. Construct FirebaseProofRepository
with Auth and the lazy `clients.storage` only after Storage is provisioned. Upload first, await success,
then pass the returned ProofSubmission to submitCompletion. No UI wiring is included.

## Storage structure and rules

`households/{householdId}/completions/{assignmentId}/proof/{workerUid}/{proofId}`

Uploads require an authenticated authoritative household member who is the assignment's worker, while the
assignment is still ASSIGNED. Assignment/proof IDs are bounded ASCII occurrence IDs. PHOTO permits JPEG/PNG
up to 5 MiB; VIDEO permits MP4 up to 20 MiB. Files must be nonempty. MIME/proofType metadata must agree;
custom metadata is restricted to proofType. Rules validate declared types, not decoded media content.

Existing files cannot be overwritten, have metadata changed, or be deleted by clients. Household members
can fetch a known object; listing and nonmember/unauthenticated SDK downloads are denied. The explicit
`resource == null` upload guard also covers the Storage emulator's create/update misclassification of
overwrites. No rule was weakened to accommodate emulator behavior.

The app uses authenticated getBytes and never calls getDownloadUrl or stores bearer download tokens in
Firestore. Firebase's service can generate download tokens; ordinary member access is not a DRM guarantee.
A member who extracts/shares a token or downloads/copies a file can disclose it. The emulator strips its
reserved firebaseStorageDownloadTokens field before rule evaluation, so its custom-metadata allowlist test
uses an ordinary forged field; post-upload token/metadata edits are separately tested and rejected. Do not
claim that this ruleset proves automatic Firebase download tokens cannot exist. A stricter token-free media
service would require trusted server infrastructure and is outside Stage 2.

An upload is immutable and not a retryable overwrite. Retain the returned ProofSubmission; if an upload's
result is uncertain, fetch/compare the object with downloadProof or choose a new proof ID before submission.
Cancelled/abandoned uploads can leave orphan objects. Client cleanup is deliberately denied; bounded server
cleanup is deferred. The repository uses byte arrays with explicit 5/20 MiB caps, suitable for the MVP;
streaming/compression and large media are deferred.

## Security and limits

Firestore enforces both sides of the atomic write with get/getAfter checks. A standalone status change or
standalone completion is rejected. Other workers, nonmembers, foreign-household/path proof, spoofed identities,
extra fields, client timestamps, nonnull results and assignment snapshot edits are rejected. Completion edits,
deletes, verification transitions and contribution updates remain denied. Existing private-user, household,
invite, membership, chore and assignment regression protections remain tested. Reviews remain deny-all.

Firestore rules cannot inspect a Storage object. Stage 2 validates the canonical ownership/path/type reference,
not whether uploaded bytes actually exist or show a clean chore. Firestore and Storage have no cross-service
atomic transaction. A modified client can submit a well-formed reference without uploading; this only produces
an unverified completion with zero reward. Before any future trusted verification/reward stage, require valid
stored evidence and handle missing objects. No client-side check should be represented as a security boundary.

Storage rules use Firestore membership/assignment checks in the default database. Their first live deployment
requires the Firebase Storage service's cross-service permissions, in addition to the operator's deployment
permissions. Do not substitute public access rules if that permission setup fails.

## Test results

- Firebase-enabled debug build and instrumentation APK: passed.
- Firebase-disabled debug build/unit tests: passed.
- JVM tests: 77 passed, no failures/errors/skips (68 existing + 9 Stage 2).
- Emulator tests: 43 passed (37 Firestore including 10 completion cases, plus 6 Storage cases).
- Stage 2 Firestore rules: published to backendtest-21628 with user approval.
- Live Firestore Stage 2: passed, JUnit OK (1 test), 15.243 seconds.
- Live Stage 2 run: c990af91cd46; household: j49crinZOXJwbwMuChXS; liveStorage=false.
- Existing live backend regression: passed, JUnit OK (1 test), 21.25 seconds.
- Regression run: 598eb9d0d7c7; household: kADHGYg2WHiNu5KICCuy; integrationFailures=[].
- Live Storage deployment/upload/download: NOT TESTED; external project-owner permission limitation.

On September 14, 2026 the Storage page for backendtest-21628 showed:
“To manage Storage, ask a project owner for the necessary permissions.” The operator cannot currently provision
or manage the bucket. Local Storage rules are tested but not deployed. This is an external test-environment
limitation, not a passing live upload test or an implementation failure. The owner must provision Storage,
resolve permissions and billing requirements, and confirm the app has the correct storage bucket configuration.

The live Firestore test explicitly logs a metadata-only proof fixture when liveStorage is false. It does not
pretend that a file was uploaded. Once live Storage is available, deploy the tested Storage rules and run the
same opt-in test with `-e liveStorage true`; it uploads a real one-pixel PNG and checks a peer's byte-for-byte
download. This remaining live check has not yet been performed.

Evidence: [emulator suites](test-results/completion-rules-tests.txt),
[live Stage 2 JUnit](test-results/completion-live-instrumentation.txt),
[live Stage 2 checkpoints](test-results/completion-live-checkpoints.txt), and
[existing live regression JUnit](test-results/completion-regression-instrumentation.txt) and
[regression checkpoints](test-results/completion-regression-checkpoints.txt).

Reproduce local checks:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -Proomie.firebase.enabled=true
./gradlew :app:assembleDebug :app:testDebugUnitTest -Proomie.firebase.enabled=false
cd tests/firestore
pnpm test
```

Rebuild with Firebase enabled before installing the APK, since the disabled build replaces it. Install both
app-debug.apk and app-debug-androidTest.apk on the emulator. Live test command (creates disposable test users
and household data, signs out afterward, never logs passwords):

```sh
adb shell am instrument -w -e class com.rommie.app.LiveFirebaseCompletionTest \
  -e liveFirebase true -e liveStorage false com.rommie.app.test/androidx.test.runner.AndroidJUnitRunner
```

Also run the existing LiveFirebaseBackendTest for the previous full backend regression. Both tests are guarded
against accidental live execution and restrict the live project to backendtest-21628. Check JUnit's result,
not just the adb process exit code.

## Files for this increment

Created:
- app/src/main/java/com/rommie/app/data/repository/CompletionRepository.kt
- app/src/main/java/com/rommie/app/data/repository/FirebaseCompletionRepository.kt
- app/src/main/java/com/rommie/app/data/repository/ProofRepository.kt
- app/src/main/java/com/rommie/app/data/repository/FirebaseProofRepository.kt
- app/src/main/java/com/rommie/app/data/repository/ProofValidation.kt
- app/src/main/java/com/rommie/app/data/firebase/CompletionMappers.kt
- app/src/test/java/com/rommie/app/data/firebase/CompletionMappersTest.kt
- app/src/test/java/com/rommie/app/data/repository/ProofValidationTest.kt
- app/src/androidTest/java/com/rommie/app/LiveFirebaseCompletionTest.kt
- tests/firestore/completions.test.mjs
- tests/firestore/storage.test.mjs
- docs/COMPLETIONS.md
- docs/test-results/completion-rules-tests.txt
- docs/test-results/completion-live-instrumentation.txt
- docs/test-results/completion-live-checkpoints.txt
- docs/test-results/completion-regression-instrumentation.txt
- docs/test-results/completion-regression-checkpoints.txt

Modified:
- app/src/main/java/com/rommie/app/data/firebase/FirestorePaths.kt
- firestore.rules
- storage.rules
- firebase.rules-test.json
- tests/firestore/package.json
- README.md

Existing live backend test source is unchanged; Stage 2 uses a separate opt-in instrumentation class.
Stop here. Reviews require a new explicit instruction.
