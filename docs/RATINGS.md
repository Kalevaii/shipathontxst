# Stage 4: pure rating calculation

The requested algorithm already existed in `com.rommie.app.domain.ChoreCalculations.kt`. Stage 4 preserves
its API and behavior, centralizes the threshold, documents precision, and adds comprehensive tests.
No duplicate calculator or shared model was introduced.

## Contract

- `arithmeticRating(ratings: List<Int>): Double?` averages all valid votes.
- `trimmedRating(ratings: List<Int>): Double?` applies the trimming policy below.
- `finalRating(ratings: List<Int>): Double?` delegates to trimmedRating; the name describes a calculated
  value, not a persisted/finalized completion.
- `MIN_REVIEWS_FOR_TRIMMING = 5` is the single named product-policy constant in the same domain file.
  There is no caller-controlled threshold that could produce inconsistent household results.

Every rating must be an integer in 1..10. Validation occurs before trimming, so 0 or 11 is rejected with
IllegalArgumentException even when it would have been discarded as an extreme. Empty input returns null:
this is the existing tested "no votes / pending" result, preserved rather than breaking callers with a new
exception or introducing a zero score. The shared Review model independently enforces the same range.

For 1–4 votes, use the arithmetic mean of all votes. At 5 or more, sort a copy, remove exactly the first
and last elements, then average what remains. Duplicate minima/maxima retain their other occurrences.
The caller's list and stored reviews are never mutated, and ordering does not affect results.

Example: `[8, 8, 9, 9, 1]` sorts to `[1, 8, 8, 9, 9]`; remove one 1 and one 9, leaving `[8, 8, 9]`.
The result is `25.0 / 3.0`, approximately `8.333333333333334`, not 8.33.

The return type is nullable IEEE-754 Double. No decimal/display rounding occurs in these functions.
Integer ratings summed across any JVM List size fit exactly within Double's exact integer range; division
produces the representable floating-point mean. Tests compare fractions with 1e-12 tolerance and explicitly
check the example matches Double division without intermediate rounding. Presentation formatting belongs
outside this calculator. The existing qualityBasedPoints helper was neither changed nor newly integrated.

## Architecture and scope

Pure Kotlin: no Firebase, Firestore, Android Context, or Compose. No repository or server-reader call is
made. The server-only reader remains independent; no JavaScript copy of the rating algorithm or server
orchestration was added. Integrating trusted finalization is a later stage and is not implemented here.
No completion results, points, contribution totals, or leaderboards are written. No UI/AI changes.

Firestore and Storage rules are unchanged; published Stage 3 rules were left untouched. Existing review
privacy and immutable submission behavior and Stage 2 completion behavior remain unchanged.
Live Storage's project-owner permission limitation remains unchanged; these regressions use the already
supported explicit proof-metadata fixture and do not claim live media upload coverage.

## Verification

- Firebase-enabled debug build, instrumentation APK and unit tests: passed.
- Firebase-disabled debug build and unit tests: passed.
- Unit tests: 114 passed (92 existing + 22 focused rating tests), no errors/failures/skips.
- Emulator suites: all 58 passed (51 Firestore security, 1 trusted-reader, 6 Storage).
- Live Stage 3, Stage 2 and existing backend regressions: pending execution.

New tests cover empty input, each count 1–5, larger groups, identical votes, low/high/both outliers,
duplicate minima/maxima/both, reordered input, accepted endpoints, rejected 0/11, original-list preservation,
threshold behavior and full precision. All previous calculation/points tests remain unchanged and pass.

[Emulator evidence](test-results/rating-emulator-regression.txt).

## File inventory

Modified:
- app/src/main/java/com/rommie/app/domain/ChoreCalculations.kt — named threshold and API documentation;
  replace the existing literal threshold with the constant. No algorithm/empty-result behavior change.
- README.md — Stage 4 handoff link.

Created:
- app/src/test/java/com/rommie/app/domain/RatingCalculationsTest.kt
- docs/RATINGS.md
- docs/test-results/rating-emulator-regression.txt

No Gradle, model, repository, server-reader, UI, security-rule, or existing test-source modifications.
Stop after Stage 4. Points/finalization and leaderboard remain deferred.
