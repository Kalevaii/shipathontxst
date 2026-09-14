# Roomie — shared Android foundation

Shipathon 2026 · Kotlin · Jetpack Compose · Material 3 · MVVM-style feature development.
Namespace and application ID: `com.rommie.app`. Minimum SDK: 24.
Existing Android SDK/toolchain versions are preserved. Firebase SDK/configuration scaffolding is
available; see [Firebase/data foundation](docs/FIREBASE.md). Email/password Auth and private user
repositories are implemented; see [Authentication integration](docs/AUTHENTICATION.md). Live Firebase
verification passes; see [results and cache policy](docs/LIVE_FIREBASE_TESTS.md). UI wiring and AI
remain deferred. Household creation/joining and
membership rules are implemented; see [Household integration](docs/HOUSEHOLDS.md).
Chore persistence and scoped access rules are implemented; see [Chore backend contract](docs/CHORES.md)
for the API, editable fields, validation, and current live-verification status.
Assignment persistence and the pure deterministic selector are documented in
[Assignment backend contract](docs/ASSIGNMENTS.md), including tie-breaks, availability, and access rules.

## Package contract

All production sources are under `app/src/main/java/com/rommie/app/`.

- `model/`: authoritative Kotlin domain models, without Android or Firebase dependencies.
- `domain/ChoreCalculations.kt`: pure calculations shared by features and backend orchestration.
- `data/repository/`: read-only suspend repository boundary and injectable mock implementation.
- `data/mock/RoomieSampleData.kt`: fixed sample household with four users, two chores,
  assignments, a pending proof submission, and weekly availability. Media URL is a placeholder.
- `ui/RoomieApp.kt`: saved top-level destination selection and replaceable screen slots.
- `ui/navigation/RoomieDestination.kt`: Home, Chores, Review, Points destinations.
- `ui/theme/`: preserved starter Material theme.
- `viewmodel/`: documented home for feature-owned ViewModels, added when screens need them.

Home preserves the starter greeting. Other tabs are placeholders. Back returns to Home;
this is a top-level shell, not a nested back stack or deep-link implementation. Add a navigation
library when detail routes need it. No dependency was needed for this initial shell.
Add `ui/screens/<feature>/` and `ui/components/` when real implementations exist.

## Shared models

- `User`: account identity. `Household`: name and invite code. `HouseholdMember`: links a
  user to a household and holds its contribution total; do not duplicate member IDs in Household.
- `Availability`: weekly local-time window with explicit IANA time zone. `AvailabilityOverride`:
  temporary absolute window. Scheduling precedence and away handling belong to future logic.
- `Chore`: template, difficulty, configurable reward, duration, creator, and optional recurrence.
  Null recurrence is one-time; EveryDays supports daily/every-three-days; Weekly supports selected days.
- `TaskAssignment`: one occurrence and deadline, worker, status, and workload/reward snapshots.
  Derive workload with `workloadValue(chore.difficulty)` when creating the assignment.
- `TaskCompletion`: nonempty photo/video proof list, worker, submission time, and optional
  `VerificationResult`. A null result is pending and awards no points.
- `Review`: private record with reviewer identity for uniqueness checks, never UI aggregate data.

All absolute times are UTC epoch milliseconds, avoiding API 26 date/time requirements on API 24.
Domain models are not Firestore serialization DTOs; backend owns mapping and access rules.

## Calculation policy

Easy/Medium/Hard workload = 1/2/3. Suggested max rewards = 5/10/20, independently editable.
`arithmeticRating` computes the ordinary mean; `trimmedRating` and `finalRating` remove exactly
one highest and one lowest vote when there are at least five votes, otherwise use the mean.
Empty votes return null. Scores outside 1–10 are rejected.
`qualityBasedPoints` uses the unrounded final rating / 10 × maxPoints, then rounds once
HALF_UP to whole points. Nonfinite/out-of-range ratings and negative rewards are rejected.
Display rounding must not feed back into the award calculation.

Calculations do not decide when voting is complete. Backend must define finalization policy
(e.g. all eligible peers submitted), enforce no self-review, one review per eligible member,
hide individual votes/identities, and award points exactly once atomically. Never expose
partial aggregates or other reviewers' votes before submission. Kotlin comments/types are
not security enforcement. The read contract intentionally excludes raw reviews.
Status lifecycle: ASSIGNED → AWAITING_VERIFICATION after proof → VERIFIED after finalization;
CANCELLED is reserved for cancelled occurrences. Transition enforcement is future service work.

## Team ownership and next tasks

1. **Home/navigation owner:** own `ui/RoomieApp.kt`, `ui/navigation/`, and Home UI. Integrate
   feature screen lambdas here; other developers should not edit MainActivity or navigation.
2. **Chores owner:** create chore/task screens and ViewModels. Accept state and callbacks in
   screen composables. Start with `RoomieSampleData.household` for previews or inject
   `MockRoomieRepository` through the `RoomieRepository` interface.
3. **Verification/points owner:** create proof-review and leaderboard UI with the same state
   pattern. Use shared calculations and household-scoped membership points. Keep Review out
   of worker-facing state. Pending mock submissions intentionally have no award.
4. **Backend/integration owner:** own repository implementations and coordinate changes to
   shared models/contracts. Next agree on write commands, error/loading state, observation,
   and review finalization contracts with feature owners; then implement the deterministic
   availability/workload/history assignment service with tests. Firebase feature operations come in later increments.

The current repository supports loading only, not writes or live updates. Future ViewModels
can swap repository implementations without coupling screens to Firebase. Extend the shared
interface centrally as write workflows are agreed; do not create competing models or repositories.
Reassignment should preserve an audit of who absorbed workload; reward totals are never a
substitute for workload history. Recurrence generation, uploads, invite validation, and
transactional verification are intentionally not implemented by this foundation.

## Validation

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` using the Android Studio JDK and
installed SDK. Calculation tests cover trimming boundaries, ties, empty/invalid input,
proportional rewards, rounding, and maximum integer reward. Instrumentation starter test
expects `com.rommie.app`; it requires a device/emulator.

Latest foundation validation: debug APK build and local unit tests passed (35 tests, zero failures,
including household/authentication orchestration, validation, and document mapping/path tests).
Device/emulator instrumentation tests were not run.

## Stage 2 backend

Completion/proof APIs, security rules, verification results, and live Storage limitations are documented in [docs/COMPLETIONS.md](docs/COMPLETIONS.md).

## Stage 3 backend

Private reviews, repository APIs, security rules, and test results are documented in [docs/REVIEWS.md](docs/REVIEWS.md).

## Stage 4 calculation

The pure rating calculator, threshold, precision, and regression results are documented in [docs/RATINGS.md](docs/RATINGS.md).

## Stage 5 finalization and points

Trusted completion finalization, atomic point awards, and idempotency are documented in [docs/FINALIZATION.md](docs/FINALIZATION.md).

## Stage 6 leaderboard

The household-scoped leaderboard API and deterministic ordering are documented in [docs/LEADERBOARD.md](docs/LEADERBOARD.md).
