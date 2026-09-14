# Household creation, joining, and membership — increment 3

Implemented behind `HouseholdRepository`, with no UI/MainActivity or shared-model changes.
This increment adds no chores, assignments, proof uploads, scoring, leaderboard operations, or AI.
No live Firebase project is connected and no rules have been deployed.

## Integration

Construct one repository with the same Auth instance as the existing user/auth repositories:

```kotlin
val clients = FirebaseClients.create(applicationContext)
val users: UserRepository = FirebaseUserRepository(clients.auth, clients.firestore)
val households: HouseholdRepository = FirebaseHouseholdRepository(clients.auth, clients.firestore, users)
```

Inject the interface into ViewModels. `createHousehold(name)` and `joinHouseholdByCode(code)` require
a signed-in account with a persisted Roomie profile. `getCurrentUserHousehold()` returns null if no
membership lookup exists. `getHousehold(id)` and `getHouseholdMembers(id)` use server reads, with
membership enforced by rules. A nonmember direct read is an access failure, not a not-found oracle.
No Flow/listener is added: use suspend reads after commands and on screen refresh for now.
Existing Auth flow can trigger clearing/reloading household state when the current UID changes.
The pre-existing mock `RoomieRepository` is untouched; it is not silently replaced or implemented here.

`HouseholdMemberSummary` wraps the existing `HouseholdMember` plus `displayName`. It is a public
roster projection, not another membership/User model. No emails are fetched for roommates.
Roster display names snapshot the profile at join time; later profile edits do not propagate yet.

## Schema and lookup

- `households/{id}`: `name`, `inviteCode`, `createdBy`, `createdAt` (server timestamp).
  The Android repository generates Firestore random 20-character document IDs. Rules require the
  expected shape but cannot prove that a hostile client used random generation.
- `households/{id}/members/{uid}`: `userId`, `displayName`, `joinedAt` (server timestamp),
  `contributionPoints` (integer zero). Household ID comes from the path. No workloadScore field is
  added until assignment accounting is designed. Points cannot currently be edited by clients.
- `householdInvites/{CODE}`: only `householdId`. Authenticated callers may get a document by its
  exact code, including checking whether a code is free. Listing, updates, and deletion are denied.
- `userHouseholds/{uid}`: `householdId`, `inviteCode`. Only that user can read/create this pointer;
  creation must be atomic with self-membership and prove the supplied code matches the household.
  No updates or deletion: this MVP deliberately supports one household per account, with no leaving,
  switching, or invite rotation. A future migration must update all related rules/transactions together.

Private `users/{uid}` stays unchanged: name/email only and owner-only reads. There is no household
reference on that private profile and no need to query all households or add a composite index.
`createdBy` preserves future owner-role information; no role management is invented in this increment.

## Creation and join behavior

Creation validates/trims a 1–100 character name without control characters, loads the caller's own
profile, and generates a six-character code with SecureRandom. The generator excludes ambiguous
I/O/0/1; input accepts all six uppercase ASCII letters/digits to support legacy/sample codes.
Input normalization trims outer whitespace and uppercases with Locale.ROOT; internal spaces and
punctuation fail. A collision retries with a new code, up to five attempts. Generic write failures
are not retried as collisions. Codes are bearer credentials; no sequential IDs or time-seeded RNG.

Creation transaction reads the private lookup and code reservation, then atomically writes four
documents: household, invite, creator member, and private lookup. Existing lookup means ALREADY_JOINED.
Firestore transaction conflict retries make code reservation and concurrent membership creation safe.
The transaction does not read the uncreated household/member document, which rules would deny.

Join transaction reads the private lookup and exact invite document. It rejects existing membership,
returns NOT_FOUND for a nonexistent invite, and writes the caller's membership and private lookup.
It never takes another UID from the caller. It does not read household metadata before membership.
After commit it fetches the household. The rules independently validate the supplied code against
both the target household and invite mapping; knowing a household ID alone is insufficient.

Auth is required before all operations. `HouseholdException.reason` provides INVALID_NAME,
INVALID_CODE, NOT_FOUND, ALREADY_JOINED, PROFILE_REQUIRED, CODE_COLLISION, WRITE_FAILED, or READ_FAILED.
Existing NotAuthenticatedException and SessionChangedException are reused; cancellation propagates.
Unknown SDK errors retain their cause. Transaction callback domain errors are unwrapped when needed.

A cancelled or failed response does not prove a write rolled back. In particular, a join can commit
before the post-commit read fails. On recovery call `getCurrentUserHousehold()` before retrying.
Repeated successful joins return ALREADY_JOINED rather than overwriting membership or zeroing points.
Transactions/server reads require connectivity. If profile name changes between fetch and join,
rules reject the stale snapshot; refresh the profile and retry.

## Rules and security limits

The rules preserve all existing private-user protections. Only existing members can get household
metadata or get/list that household's roster. Household listing is denied. Membership creation is
self-only, copies the caller's persisted display name, uses server time, and initializes points to zero.
Every membership requires a new matching private lookup; every lookup requires the matching member,
household code, and invite reservation after commit. Household creation requires its creator member,
lookup, and reservation. Invite creation is only valid alongside its new household.
These getAfter checks reject incomplete writes and unauthorized self-joins without the code.

All household/member updates and deletions are denied, protecting createdBy, inviteCode, userId,
and contributionPoints. There are no allowed nested chore/assignment/review operations. Storage
remains closed. Authenticated exact-code reads intentionally reveal only the mapped household ID;
a guessed correct code grants a join. Six-character codes are suitable for this hackathon, but
rules alone cannot rate-limit guessing. Production should add a trusted rate-limited invite endpoint.

Cross-document access calls are bounded (each operation below 10; the four-document create below
20 even before reuse), but the actual rules evaluator must be verified in an emulator before deployment.
This implementation replaces the original proposed trusted join endpoint with client transactions
validated by rules; no deployed server endpoint or extra Functions dependency is required for the MVP.

## Verification and next work

Local unit tests cover normalization/name boundaries, generator alphabet and random selections,
collision retry limits, duplicate membership logic, missing/invalid code, missing/mismatched profile,
unauthenticated operations, write errors, cancellation, and public member mapping with protected defaults.
Fake-store tests verify orchestration, not Firestore conflict resolution or security enforcement.
Firebase CLI/emulator is unavailable in this environment; no live Firebase/rules/transaction tests ran.

Before deploying, exercise these emulator cases: atomic create succeeds; duplicate code retries;
valid-code self join succeeds; missing/invalid code fails; concurrent joins leave one membership;
nonmember household/member reads fail; partial create/join batches fail; forged lookup/code fails;
adding another UID fails; points/identity/code/creator edits fail; cross-user profile reads still fail;
invite listing and household/member deletion fail. Also verify recovery after a lost join response.

Next backend increment: chore persistence and its access rules using existing shared models.
Assignment distribution should follow as a separate tested step. Neither is started here.

Build checkpoint: `:app:assembleDebug :app:testDebugUnitTest` passed without google-services.json.
35 unit tests passed, zero failures/errors; the prior 22 tests remain passing.
