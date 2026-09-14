# Live Firebase verification — 2026-09-14

## Result: PASS

Project `backendtest-21628`, Android package `com.rommie.app`, Firestore SDK 26.6.0.
Default Firestore database: Standard edition, `nam5` (United States), Spark plan.
Email/Password authentication is enabled. The checked-in membership rules are deployed.
No UI, MainActivity, chores, Storage uploads, authentication features, or paid-plan changes were made.

- Firebase-enabled debug app and instrumentation APK build: PASS.
- Firebase-disabled debug build: PASS.
- JVM unit tests in both configurations: 35 passed, no failures or skips.
- Local Firestore Emulator rule tests: 9 passed.
- Final live Android instrumentation scenario using production repositories/client factory: PASS (1 test).

Evidence: [live runner output](test-results/live-firebase-instrumentation.txt),
[live checkpoints](test-results/live-firebase-checkpoints.txt),
[emulator output](test-results/firestore-rules-tests.txt).
Expected permission-denied warnings in the emulator output belong to negative tests.
One final-build attempt encountered an Auth transport error (`unexpected end of stream`) during
signup, before household checks. The unchanged test passed on rerun; transport errors were not
caught and converted into successes.

## Root cause and evidence

The original membership rule already used the authoritative document:
`households/{householdId}/members/{request.auth.uid}`. Its database/collection path was correct.
It did not depend on a household member array, the private userHouseholds pointer, request.resource,
or recursive read permissions. The join transaction correctly committed membership and the pointer.

The failure was isolated to reuse of cached Firestore listen targets in the Android client's default
persistent cache across the creator → joiner account switch. Ordinary `get(Source.SERVER)` calls
use the SDK's listen machinery: requesting server data does not mean ignoring cached target state.
Reads of the same household and roster previously fetched by the creator failed for the joiner.

Controlled checks established:

1. Membership existed, and the rules simulator allowed the joiner's household read.
2. Transaction reads of the household succeeded as the same joiner.
3. Waiting five seconds, refreshing the Auth token, and restarting the network did not repair the reads.
4. A fresh Firebase client signed into that same joiner read the same household and roster successfully.
5. A previously unused roster query succeeded while the original query still failed in the same client.
6. Tightening exists() to a membership userId check alone did not fix the original live scenario.
7. Changing only the cache to eager in-memory caching made the complete scenario pass. Moving that
   setting into the production factory and removing diagnostic overrides also passed.

This establishes a retained-target client issue, rather than missing membership or an incorrect rule
path. Reuse of resume state explains these observations and is consistent with the SDK implementation;
the precise server-side reason for rejecting a resumed target has not been independently traced.
Do not describe this as a proven Firestore rules-engine bug or claim exists() was incorrect.

The Firebase SDK's [LocalStore](https://github.com/firebase/firebase-android-sdk/blob/master/firebase-firestore/src/main/java/com/google/firebase/firestore/local/LocalStore.java)
retains query/target state separately from per-user mutation state.
Its [eager memory collector](https://github.com/firebase/firebase-android-sdk/blob/master/firebase-firestore/src/main/java/com/google/firebase/firestore/local/MemoryEagerReferenceDelegate.java)
removes inactive target data.

## Exact changes

`firestore.rules`: the single membership condition changed from:

```text
exists(/databases/$(database)/documents/households/$(hid)/members/$(request.auth.uid))
```

to:

```text
get(/databases/$(database)/documents/households/$(hid)/members/$(request.auth.uid)).data.userId == request.auth.uid
```

The authentication guard remains. This is narrow identity hardening, not the cache fix: an absent or
malformed member document cannot grant access. All private-profile, invite, membership-create,
immutable-field, and default-deny rules remain unchanged. No household data was made public.

`FirebaseClients.kt`: configures `MemoryCacheSettings` with explicit `MemoryEagerGcSettings` before
first Firestore use. The existing online repositories and transactions are unchanged. Repeated
factory creation with the same settings is covered by the live test.

Tradeoff: cached documents and pending writes are not durable across app restarts. This fits the
current online MVP (`Source.SERVER` reads and online transactions); durable offline behavior is
explicitly deferred. No clearPersistence(), document deletion, sign-out workaround, retries masking
permission errors, or transaction-read fallback was added. Future live listeners must still be
removed when their user/session is no longer active.
See [Firebase memory-cache API](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/MemoryCacheSettings).

## Verified live behavior

- Signup creates Auth accounts and private name/email profiles; signout/signin round-trips profiles.
- Creator creates household, invite reservation, creator membership, and private lookup.
- Second account is denied household/roster reads before joining.
- Joining writes its membership and pointer, and returns the household successfully.
- Joiner reads its own membership, creator's membership, household, private current-household lookup,
  and the complete two-member roster immediately, then again after signing out and back in.
- Creator can still read the roster after signing back in.
- Peer profiles and peer private lookups are denied.
- Adding another UID, changing member identity/points/workload, changing household creator/invite,
  listing invite codes, overwriting peer profiles, and forging a wrong-code join are denied.
- Nonmembers and unauthenticated clients remain denied.

Negative tests require PERMISSION_DENIED specifically. Assertion failures/timeouts are failures.

Final passing run: `ffc94899c58b`.
Household: `Q0PeXBUEqmINAIrJs1HU`; invite: `UWCWZQ`.
Creator: `928srMR2jiXlRL7TdAN4tGxuUSo2`.
Joiner: `oMjvrcqoorRaSNzaASR7rXDyIep1`.
Disposable accounts use `roomie-test-ffc94899c58b-a@example.com`, `-b@example.com`, and `-c@example.com`.
Passwords were generated in memory and not logged. Diagnostic test records remain for inspection.

## Reproduce the live test

Build with the ignored `app/google-services.json` pointing to this test project:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -Proomie.firebase.enabled=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.rommie.app.LiveFirebaseBackendTest \
  -e liveFirebase true \
  -e preJoinDeniedRead true \
  com.rommie.app.test/androidx.test.runner.AndroidJUnitRunner
```

The test skips without explicit liveFirebase opt-in, hard-checks the test project ID before writes,
uses production repositories, and signs out in finally. Inspect JUnit's result, not just adb's exit
status (which can be zero even for failing instrumentation).

## Reproduce emulator tests

Install Node 22+ (supported by Firebase CLI), Java 21+, and pnpm; no live credentials are needed.

```sh
cd tests/firestore
pnpm install --ignore-scripts --frozen-lockfile
pnpm test
```

The isolated config `firebase.rules-test.json` loads the same root firestore.rules, uses localhost
port 8085, and selects only the demo project `demo-roomie-rules`. It never deploys to live Firebase.
The nine sequential checks cover create/join/read, nonmember/unauthenticated denials, private
profiles/lookups, protected fields, invite protections, pointer-only denial, and malformed-member denial.
Privileged fixtures are used only for the last two otherwise impossible malformed states.

## Files changed in this fix

Modified: `firestore.rules`, `app/src/main/java/com/rommie/app/data/firebase/FirebaseClients.kt`,
`app/src/androidTest/java/com/rommie/app/LiveFirebaseBackendTest.kt`, `README.md`,
`docs/FIREBASE.md`, `docs/LIVE_FIREBASE_TESTS.md`, `docs/test-results/live-firebase-checkpoints.txt`.

Created: `firebase.rules-test.json`, `tests/firestore/.gitignore`, `tests/firestore/package.json`,
`tests/firestore/pnpm-lock.yaml`, `tests/firestore/membership.test.mjs`,
`docs/test-results/firestore-rules-tests.txt`, `docs/test-results/live-firebase-instrumentation.txt`.

Next backend feature remains unstarted. Teammates should obtain Firestore through FirebaseClients
and inject it into the existing repositories; direct unconfigured SDK construction bypasses the fix.
