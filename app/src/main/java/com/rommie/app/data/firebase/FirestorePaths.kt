package com.rommie.app.data.firebase

/** Canonical paths; no reads or writes occur here. */
object FirestorePaths {
    private fun segment(value: String): String {
        require(value.isNotBlank() && '/' !in value && value != "." && value != "..") {
            "Expected a nonempty single document ID"
        }
        return value
    }

    fun userHousehold(uid: String) = "userHouseholds/${segment(uid)}"
    fun user(uid: String) = "users/${segment(uid)}"
    fun household(householdId: String) = "households/${segment(householdId)}"
    fun members(householdId: String) = "${household(householdId)}/members"
    fun member(householdId: String, uid: String) = "${members(householdId)}/${segment(uid)}"
    fun chores(householdId: String) = "${household(householdId)}/chores"
    fun chore(householdId: String, choreId: String) = "${chores(householdId)}/${segment(choreId)}"
    fun assignments(householdId: String) = "${household(householdId)}/assignments"
    fun assignment(householdId: String, assignmentId: String): String {
        require(assignmentId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Use an occurrence ID of 1–128 letters, digits, hyphens, or underscores" }
        return "${assignments(householdId)}/$assignmentId"
    }
    fun completions(householdId: String) = "${household(householdId)}/completions"
    fun completion(householdId: String, assignmentId: String): String {
        assignment(householdId, assignmentId)
        return "${completions(householdId)}/$assignmentId"
    }
    fun availability(householdId: String) = "${household(householdId)}/availability"
    fun availabilityOverrides(householdId: String) = "${household(householdId)}/availabilityOverrides"
    fun review(householdId: String, completionId: String, reviewerUid: String) =
        "${completions(householdId)}/${segment(completionId)}/reviews/${segment(reviewerUid)}"
    fun invite(code: String): String {
        require(code.matches(Regex("[A-Z0-9]{6}"))) { "Expected six uppercase letters/digits" }
        return "householdInvites/$code"
    }

    // Private object location, not a public download-token URL.
    fun proof(householdId: String, completionId: String, workerUid: String, proofId: String) =
        "${completions(householdId)}/${segment(completionId)}/proof/${segment(workerUid)}/${segment(proofId)}"
}
