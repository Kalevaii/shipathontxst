package com.rommie.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.ProofType
import kotlinx.coroutines.tasks.await

class FirebaseProofRepository(private val auth: FirebaseAuth, private val storage: FirebaseStorage) : ProofRepository {
    override suspend fun uploadProof(householdId: String, assignmentId: String, proofId: String,
        type: ProofType, contentType: String, bytes: ByteArray): ProofSubmission = session { uid ->
        ProofValidation.validateUpload(type, contentType, bytes.size.toLong())
        val path = ProofValidation.path(householdId, assignmentId, uid, proofId)
        val metadata = StorageMetadata.Builder().setContentType(contentType)
            .setCustomMetadata("proofType", type.name).build()
        storage.reference.child(path).putBytes(bytes, metadata).await()
        ProofSubmission(proofId, path, type)
    }
    override suspend fun downloadProof(householdId: String, assignmentId: String, proof: ProofSubmission): ByteArray = session {
        // Accept only the canonical relative path; reject arbitrary bucket/URL input.
        val segments = proof.mediaUrl.split('/')
        require(segments.size == 7)
        ProofValidation.validate(householdId, assignmentId, segments[5], proof)
        storage.reference.child(proof.mediaUrl).getBytes(ProofValidation.maxBytes(proof.type)).await()
    }
    private suspend fun <T> session(block: suspend (String) -> T): T {
        val uid = auth.currentUser?.uid ?: throw NotAuthenticatedException()
        return block(uid).also { if (auth.currentUser?.uid != uid) throw SessionChangedException() }
    }
}
