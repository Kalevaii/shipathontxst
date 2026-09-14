# Stage 6: household leaderboard

`LeaderboardRepository.getHouseholdLeaderboard(householdId)` reads the existing
`households/{householdId}/members` documents and returns `HouseholdMemberSummary` values. It uses only
the public member projection (`displayName`, `userId`, `contributionPoints`) and never reads private
user profiles or email addresses.

The repository requires authentication and relies on the existing member read rule for household
isolation. The ranking helper also filters to the requested household as a defense-in-depth check.
Results are sorted locally by `contributionPoints` descending, then `userId` ascending for a
deterministic tie. No leaderboard collection, duplicate points ledger, query index, or UI was added.

Points remain owned by the membership document and can only be changed by the trusted finalization
transaction described in [FINALIZATION.md](FINALIZATION.md).