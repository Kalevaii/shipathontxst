# Firebase/data foundation — increment 1

**Live verification update:** Auth/household integration and access protections now pass against
`backendtest-21628`. See [live test results and reproduction](LIVE_FIREBASE_TESTS.md).
The shared client factory uses eager in-memory Firestore caching to avoid stale one-shot read
targets across account switches. Cached data/pending writes are not durable across restarts;
durable offline support is deferred. Use this factory before constructing repositories.
This update supersedes the historical deployment-status statements below.

**Increment 3 update:** Household create/join, private membership lookup, member summaries, and
membership rules are implemented. See [Household integration](HOUSEHOLDS.md) for the current schema
and transaction design; it supersedes the original invite-endpoint proposal below.

**Increment 2 update:** Authentication and private user persistence now exist; see
[Authentication integration](AUTHENTICATION.md) for current behavior and access rules.
The sections below describe the original foundation/schema proposal.

This increment adds SDK wiring, client creation, paths, User/Chore mapping, and closed rules.
It does not implement sign-in/sign-up, persistence repositories, joining, uploads, reviews,
awards, live listeners, or UI integration. No cloud project or rules have been deployed.
`RoomieRepository`, its mock, shared models/calculations, MainActivity, and all UI files stay unchanged.

## Dependencies and local configuration

Firebase BoM 34.19.0 manages `firebase-auth`, `firebase-firestore`, and `firebase-storage`.
Google Services plugin 4.5.0 processes the Android project configuration only when opted in.
No deprecated Firebase KTX artifacts, Analytics, AI SDK, or service-account credentials are added.

1. In Firebase Console, select/create the team's project and register an Android app with
   package `com.rommie.app`. Do not change the package to match a different Firebase app.
2. Enable Email/Password in Authentication for the next increment. Google sign-in is deferred.
3. Create the default Cloud Firestore database; agree on its region with the team first.
4. Provision the Storage bucket. Firebase currently requires Blaze billing for Storage.
   Billing/project changes are not performed by this repository setup.
5. Download the Android configuration after provisioning Storage to `app/google-services.json`.
   It is ignored to avoid accidental selection of a teammate's project; its identifiers are
   not server secrets. Never put an Admin SDK/service-account key in the Android project.
6. Build with `./gradlew :app:assembleDebug :app:testDebugUnitTest -Proomie.firebase.enabled=true`.
   Missing configuration fails clearly. The plugin validates the Android package match.
   The flag only controls configuration processing; it does not switch UI to live repositories.
7. Without configuration, run the ordinary build. SDKs still compile, but the existing mock UI
   does not instantiate Firebase clients. After switching modes, rebuild/install the correct APK.

Future repositories receive `FirebaseClients.create(applicationContext)` from the composition
root. Create it once before constructing those repositories. It fails explicitly if Firebase resources are missing; Storage bucket configuration is checked
only on first access to `storage`. It never silently falls back to mock data.
Firebase SDKs contribute their required network manifest permissions through manifest merging.
No custom Application class or Activity changes are needed for this increment.

The checked-in Firestore rules now allow owner-only private profile access (increment 2); all
other collections and Storage client access remain denied. Implement and emulator-test
additional feature rules before enabling those repositories. `firebase.json` points to them but nothing
has been deployed. Do not deploy this baseline over an existing live app expecting it to work.
No project alias is hardcoded. Composite indexes will be added with actual query contracts.

## Proposed Firestore documents

Document IDs are authoritative; do not duplicate `id` fields in document payloads.
Existing models remain authoritative in Kotlin. Explicit mappers encode enum names and recurrence
maps rather than using reflection on sealed classes or adding alternate domain classes.
User and Chore mapping are implemented now; remaining mappings arrive with their repositories.

- `users/{uid}`: `{name, email}`. UID is the Firebase Auth UID. Account document is private;
  email need not be readable by every household member.
- `households/{householdId}`: `{name, inviteCode}`; backend metadata can add `createdBy`, `createdAt`.
- `households/{householdId}/members/{uid}`: `{contributionPoints, displayName}`. Household identity
  and user identity come from the path. `displayName` is a read projection for roster/leaderboard;
  it is not another User domain model. Membership points are server-controlled.
- `households/{householdId}/chores/{choreId}`: `{householdId, title, description, estimatedMinutes,
  difficulty, maxPoints, createdBy, recurrence}`. Recurrence is null, `{type: EVERY_DAYS, interval}`,
  or `{type: WEEKLY, weekdays: [MONDAY, FRIDAY]}`. Workload is derived using existing domain logic.
- `households/{householdId}/assignments/{assignmentId}`: `{householdId, choreId, assignedUserId,
  dueAt, workloadValue, maxPoints, status}`. Preserve workload/reward snapshots and assignment history.
- `households/{householdId}/completions/{completionId}`: `{assignmentId, householdId, completedBy,
  proof: [{id, mediaUrl, type}], submittedAt, result}`. Result stays null until finalized, then
  contains only `{finalRating, pointsAwarded}`. Prefer assignment ID as completion ID for MVP
  one-submission uniqueness; agree on resubmission semantics before implementing retries.
- `households/{householdId}/completions/{completionId}/reviews/{reviewerUid}`:
  `{completionId, reviewerId, rating, submittedAt}`. Reviewer UID as document ID prevents multiple
  vote documents per reviewer; rules must also prohibit updates/self-review and enforce eligibility.
  Never grant household-wide list/read permission on this subcollection. Reviewers may read only
  their own vote, including after submitting. Workers see only the finalized parent result.
- `households/{householdId}/availability/{availabilityId}`: the existing weekly Availability fields.
- `households/{householdId}/availabilityOverrides/{overrideId}`: existing temporary-window fields.
- `householdInvites/{CODE}`: `{householdId}`; six uppercase letters/digits. Trusted create/join
  operations reserve/check codes transactionally. No public invite listing or arbitrary membership writes.

Firestore whole-document reads cannot hide selected fields. Keep private votes and account email
out of public roster/completion documents. Existing `HouseholdSnapshot.users` requires User.email:
future roster mapping must deliberately use an empty email for peers or coordinate a minimal
public-profile contract change before integration. Do not fetch private peer account documents.

For the future timestamp mappers, store absolute times as Firestore Timestamp and convert to/from
existing epoch-millisecond Long models. Use server timestamps for submission/audit times, with
explicit handling of unresolved local timestamps. Reject malformed records instead of supplying
zero reward, random IDs, or fabricated ownership. User/Chore currently have no timestamp fields.

Leaderboard should query household members ordered by `contributionPoints` descending; no separate
leaderboard collection or duplicated award calculator. Future points finalization must atomically
mark completion finalized and increment membership points exactly once using a trusted transaction.

## Proof Storage layout

`households/{householdId}/completions/{completionId}/proof/{workerUid}/{proofId}`

Store the authenticated `gs://` object reference in existing `ProofSubmission.mediaUrl`, not a
public long-lived download-token URL. Future data-layer media loading resolves it with the Storage
SDK; do not hand raw Firebase calls to Compose. Rules must validate membership, worker ownership,
completion state, media content type, and agreed size limits. Upload/submission retries need a stable
completion/proof ID and orphan-media cleanup strategy. None of those operations run in this increment.

## Incremental integration plan

1. Authentication repository + account persistence, explicit loading/error state, and account rules/tests.
2. Trusted atomic household creation/joining + roster reads. Agree on the private/public profile boundary.
3. Chore and assignment repositories using existing models and mappers; add listeners/write contracts
   centrally to avoid competing interfaces. Keep existing mock screens usable throughout.
4. Storage upload and atomic completion submission, with tested authorization and retry behavior.
5. Private reviews and trusted finalization/points. Reuse `finalRating` and `qualityBasedPoints` in a
   Kotlin-capable trusted service or settle a shared implementation strategy before choosing the server
   runtime. Do not award points from an untrusted Android client or duplicate formulas in rules.
6. Live leaderboard integration and end-to-end/emulator tests.

Each increment must build before the next. Feature owners retain their screens/ViewModels/navigation.
The existing `RoomieRepository` is read-only; this increment deliberately does not claim a working
Firebase implementation of that interface or wire a partial one into the app.

Sources: https://firebase.google.com/docs/android/setup and
https://firebase.google.com/docs/storage/android/start

## Validation performed

- Default `assembleDebug` and `testDebugUnitTest`: successful; 11 tests, zero failures.
- Firebase-enabled build without `google-services.json`: expected explicit setup failure.
- Merged manifest contains Internet and network-state permissions contributed by dependencies.
- Live configuration processing, sign-in, network operations, and rules/emulator tests have not
  been verified; no project configuration is available and feature operations are deferred.
