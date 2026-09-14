package com.rommie.app.data.repository

import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.ProofType

interface ProofRepository {
    /** Bounded MVP upload: JPEG/PNG up to 5 MiB or MP4 up to 20 MiB. */
    suspend fun uploadProof(householdId: String, assignmentId: String, proofId: String,
        type: ProofType, contentType: String, bytes: ByteArray): ProofSubmission
    /** Authenticated download, never a bearer-token download URL. */
    suspend fun downloadProof(householdId: String, assignmentId: String, proof: ProofSubmission): ByteArray
}
