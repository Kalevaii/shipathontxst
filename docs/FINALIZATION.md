# Stage 5: trusted completion finalization and points

Finalization runs only in `server/finalize-completion.mjs` with a Firebase Admin Firestore instance.
It is not an Android repository or callable endpoint. The caller supplies the existing Stage 4
Kotlin `finalRating` and `qualityBasedPoints` implementations through the trusted calculator seam;
the server module does not expose a client-controlled rating or points input.

## Transaction contract

The transaction reads the completion, its assignment, the worker membership, and every private review.
It rejects missing or cross-household records, invalid proof shape, a non-`AWAITING_VERIFICATION`
assignment, a mismatched worker, missing membership, malformed ratings, and fewer than one review.
The assignment snapshot supplies `maxPoints`; completion and assignment data supply the worker UID.

For a pending completion it calculates the final rating with the Stage 4 calculator and computes:

`pointsAwarded = qualityBasedPoints(finalRating, assignment.maxPoints)`

`qualityBasedPoints` uses the unrounded `Double` rating and rounds once, HALF_UP, to an integer.
For example, rating 7 and maxPoints 20 awards 14 points.

The transaction writes the aggregate completion result, changes the assignment to `VERIFIED`, and
increments that worker's existing `contributionPoints` in one commit. The completion result is the
idempotency marker: a retry or concurrent attempt that observes a non-null result returns the stored
result and performs no second increment. Firestore retries the transaction safely because the increment
is inside the same transaction as the marker write.

The server/Admin SDK bypasses mobile Firestore rules and therefore requires a private deployment with
appropriate IAM. No privileged credentials or HTTP endpoint belong in the Android app. Client rules
continue to deny completion result edits, assignment verification edits, and membership point edits.

## Verification

The Android JVM suite covers the shared Stage 4 trimming and HALF_UP point behavior. The new server
fixture covers perfect and partial ratings, authoritative max-point/worker selection, missing and
non-pending completions, insufficient reviews, malformed review scope, and repeated finalization.
Execution requires Node 22+ and the locked test dependencies under `tests/firestore`; this environment
does not currently have Node or pnpm installed.

Live finalization was not run because it requires a trusted Admin deployment. Live Storage upload and
download remain blocked by the existing Firebase project-owner permission limitation.