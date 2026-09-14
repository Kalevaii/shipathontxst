# Authentication and private user persistence — increment 2

This increment implements email/password authentication and the private profile repository.
It does not wire screens, change MainActivity, implement households, deploy rules, or connect a live project.

## Repository contracts

Create one shared pair of repositories in the future composition root, using the SAME FirebaseAuth instance:

```kotlin
val clients = FirebaseClients.create(applicationContext)
val users: UserRepository = FirebaseUserRepository(clients.auth, clients.firestore)
val auth: AuthRepository = FirebaseAuthRepository(clients.auth, users)
```

Inject `AuthRepository` and `UserRepository` into feature ViewModels. Do not construct them in
screen composables. FirebaseClients no longer requires a Storage bucket until `storage` is accessed.
The existing mock household repository and all UI remain unchanged.

- `auth.currentUserId`: current Firebase UID or null; this is session identity, not profile readiness.
- `auth.authState`: cold Flow of current UID then session changes, with duplicate events removed.
  Each collector owns a listener which is removed when collection is cancelled. Collect with the
  ViewModel/lifecycle scope; on UID change clear any previous user's UI state before fetching a profile.
- `auth.signUp(name, email, password)`: creates the Auth account, then returns its persisted `User`.
- `auth.signIn(email, password)`: authenticates, then fetches the profile from the server. Null means
  profile missing; a failed read throws, rather than pretending no profile exists.
- `auth.signOut()`: clears the Firebase session. Consumers clear their own profile/UI state.
- `users.createCurrentUserProfile(name)`: creates the signed-in user's profile if absent, otherwise
  returns the existing profile unchanged. Use it to recover interrupted/partially failed sign-up.
- `users.fetchCurrentUserProfile()`: server read of the current user's private document.
- `users.updateCurrentUserName(name)`: transactionally updates only the name on an existing profile.

Signed-out profile calls throw `NotAuthenticatedException`. Session changes during operations throw
`SessionChangedException`; discard their results and reload for the current session. Use a single
AuthRepository instance, whose mutex serializes sign-up/sign-in/sign-out; do not also mutate Auth
from screens or parallel repository instances. Passwords are never stored in Firestore or logged.

Local validation trims name/email, checks a nonblank name of at most 100 characters without control
characters, and makes basic email checks. Passwords are never trimmed; Firebase handles password
policy and definitive email validity. Signup is rejected locally when an account is already signed in.

## Partial failure and cancellation

Firebase Auth and Firestore cannot commit in one transaction. Auth may report signed-in before the
profile is ready. A `ProfileProvisioningException` means the Auth account exists but profile creation
failed; it carries the UID and original cause. Keep that session and retry `createCurrentUserProfile`.
Do not repeat sign-up or delete the Auth account automatically. The Firestore transaction preserves
an existing profile, making retries safe even if an earlier response was lost.

Coroutine cancellation remains `CancellationException`. Cancelling a wait does not roll back or
necessarily cancel a Firebase operation. On resume/restart inspect currentUserId and fetch the profile;
recover a missing profile after successful sign-in. Avoid immediate competing auth commands while
an SDK request may still be completing. A profile-read failure after sign-in also leaves the session
signed in; retry the read. Feature ViewModels should expose loading/retry/error states and must not
navigate solely on a non-null auth UID when profile setup is required.

Profile operations intentionally require online server confirmation (transactions/server reads).
Offline failures are surfaced. No offline provisioning queue or local profile cache is added here.

## Private document schema and access

`users/{firebaseAuthUid}` contains exactly:

```json
{"name": "Alex", "email": "alex@example.com"}
```

The document path supplies `User.id`. Existing `User` and `FirestoreMappers` are reused.
Email comes from FirebaseAuth's account, not a caller-supplied UID/email in a profile-write method.
Name edits do not update Firebase Auth displayName; the Roomie document is the name source of truth.
Changing email/password, email verification, reset password, and account deletion are separate increments.

Firestore rules allow only:

- Authenticated owner `get` of their own document, including a read for absence during create.
- Owner create with exactly name/email, valid string sizes/nonblank name, and email matching the
  authenticated token's email claim. Clients cannot add roles, household IDs, or contribution points.
- Owner update with the same validated shape and only name affected. Email cannot change.

List queries, deletes, other users' documents, household data, and every other collection remain denied.
Storage rules are unchanged and deny access. Rules are local files; they have NOT been deployed.
Future public roommate names must use a separate limited projection/contract, not wider private-user reads.

## Setup and validation

Register `com.rommie.app`, enable Email/Password in Firebase Console, create Firestore, and download
`app/google-services.json`. Build with `-Proomie.firebase.enabled=true`. Ordinary builds keep working
without that file; the opt-in logic is unchanged. Storage provisioning is not required for this increment.
Deploy tested user rules to the chosen development project before attempting live profile writes.

Local JVM tests cover signup orchestration, Auth failure, partial profile failure/recovery, cancellation,
missing profiles vs failed reads, wrong-user results, signout, validation, and user serialization.
The Firebase SDK adapter and Firestore transactions compile but are not mocked as if that proves
server behavior. Firebase CLI/emulator is not installed; no emulator or live rules tests ran.

Before deployment, run this access matrix in the Firestore emulator: anonymous get/create denied;
owner create/get/name update allowed; other UID get/create/update denied; list/delete denied;
extra fields, removed fields, wrong types, blank/oversized names, mismatched email on create and
email changes on update denied. Also test concurrent profile-create retries preserve existing data.

Next increment: household creation/joining and membership rules, after this build/test checkpoint.
Do not loosen private profile rules for that work.

References: [Firebase password authentication](https://firebase.google.com/docs/auth/android/password-auth),
[Firestore field rules](https://firebase.google.com/docs/firestore/security/rules-fields),
[Coroutine Firebase task integration](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/).

Validation result: `:app:assembleDebug :app:testDebugUnitTest` passed without Firebase configuration;
22 local unit tests, zero failures/errors. Live Auth, Firestore transactions, and rules are unverified.
