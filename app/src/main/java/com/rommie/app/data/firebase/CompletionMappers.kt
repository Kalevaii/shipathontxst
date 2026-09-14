package com.rommie.app.data.firebase

import com.google.firebase.Timestamp
import com.rommie.app.data.repository.ProofValidation
import com.rommie.app.model.*

object CompletionMappers {
    fun proofDocument(proof: ProofSubmission): Map<String, String> = mapOf(
        "id" to proof.id, "mediaUrl" to proof.mediaUrl, "type" to proof.type.name)

    fun fromDocument(id: String, householdId: String, data: Map<String, Any?>): TaskCompletion {
        require(data["householdId"] == householdId && data["assignmentId"] == id)
        FirestorePaths.completion(householdId, id)
        val worker = data["completedBy"] as? String ?: error("Missing worker")
        val rows = data["proof"] as? List<*> ?: error("Missing proof")
        require(rows.size == 1) { "MVP completion requires exactly one photo or video" }
        val proof = rows.map {
            val row = it as? Map<*, *> ?: error("Invalid proof")
            ProofSubmission(row["id"] as String, row["mediaUrl"] as String,
                ProofType.valueOf(row["type"] as String)).also { item ->
                ProofValidation.validate(householdId, id, worker, item)
            }
        }
        val submittedAt = (data["submittedAt"] as? Timestamp)?.toDate()?.time ?: error("Missing server submission time")
        val result = data["result"]?.let {
            val row = it as? Map<*, *> ?: error("Invalid result")
            val points = row["pointsAwarded"] as? Long ?: error("Invalid points")
            require(points in 0..1000)
            VerificationResult((row["finalRating"] as Number).toDouble(), points.toInt())
        }
        return TaskCompletion(id, id, householdId, worker, proof, submittedAt, result)
    }
}
